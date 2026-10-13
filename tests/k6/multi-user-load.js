import http from 'k6/http';
import { check, sleep } from 'k6';
import execution from 'k6/execution';
import { Counter } from 'k6/metrics';

const fixture = JSON.parse(open(__ENV.FIXTURE_FILE));
const phase = __ENV.PHASE || 'catalog';
const requestRun = __ENV.REQUEST_RUN || phase;
if (!/^[a-z0-9-]{1,12}$/.test(requestRun)) throw new Error('REQUEST_RUN must be 1..12 lowercase letters/digits/hyphens');
if (!['catalog', 'orders', 'e2e'].includes(phase) || fixture.users.length < 2) {
  throw new Error('Choose catalog/orders/e2e with a private multi-account fixture');
}
const created = new Counter('orders_created');
const completed = new Counter('orders_confirmed');
const statuses = Object.fromEntries(['2xx', '4xx', '5xx', '429', 'transport'].map(x => [x, new Counter(`status_${x}`)]));
const thresholds = {
  http_req_failed: ['rate<0.01'], checks: ['rate==1'],
  'http_req_duration{stage:primary}': ['p(95)<2000'],
  status_429: ['count==0'], status_transport: ['count==0'], dropped_iterations: ['count==0'],
};
if (phase !== 'catalog') thresholds.orders_created = ['count>0'];
if (phase === 'e2e') thresholds.orders_confirmed = ['count>0'];
export const options = {
  scenarios: { load: { executor: 'constant-arrival-rate', rate: Number(__ENV.RATE || 1),
    timeUnit: '1s', duration: __ENV.DURATION || '20s', preAllocatedVUs: 5, maxVUs: 20, gracefulStop: '60s' } },
  summaryTrendStats: ['avg', 'p(95)', 'p(99)', 'max'], thresholds,
};
function request(method, route, body, token, stage, accepted = [200]) {
  const r = http.request(method, `${__ENV.BASE_URL}${route}`, body === null ? null : JSON.stringify(body), {
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json',
      ...(__ENV.TRACEPARENT ? { traceparent: __ENV.TRACEPARENT } : {}) },
    timeout: '30s', tags: { name: `${method} ${route.replace(/\d+/g, ':id')}`, stage },
    responseCallback: http.expectedStatuses(...accepted),
  });
  statuses['2xx'].add(r.status >= 200 && r.status < 300 ? 1 : 0);
  statuses['4xx'].add(r.status >= 400 && r.status < 500 ? 1 : 0);
  statuses['5xx'].add(r.status >= 500 ? 1 : 0);
  statuses['429'].add(r.status === 429 ? 1 : 0);
  statuses.transport.add(r.status === 0 ? 1 : 0);
  return r;
}
export default function () {
  const iteration = execution.scenario.iterationInTest;
  const user = fixture.users[iteration % fixture.users.length];
  if (phase === 'catalog') {
    const r = request('GET', `/api/products/${user.productId}`, null, user.userToken, 'primary');
    check(r, { 'catalog through gateway': x => x.status === 200 && x.headers['X-Gateway'] === 'api-gateway' });
    return;
  }
  const r = request('POST', '/api/orders', { channel: iteration % 2 ? 'POS' : 'WEB',
    idempotencyKey: `${fixture.runId}-${requestRun}-${iteration}`,
    items: [{ productId: user.productId, quantity: 1 }] }, user.userToken, 'primary', [201]);
  created.add(r.status === 201 ? 1 : 0);
  if (!check(r, { 'new pending order': x => x.status === 201 && x.json('data.status') === 'PENDING_PAYMENT' })) return;
  if (phase === 'orders') return;
  const order = r.json('data.id');
  let payment;
  for (let poll = 0; poll < 60; poll++) {
    const p = request('GET', `/api/payments/by-order/${order}`, null, user.userToken, 'payment_poll', [200, 404]);
    if (p.status === 200) { payment = p.json('data.id'); break; }
    if (p.status !== 404) break;
    sleep(0.5);
  }
  if (!check(payment, { 'payment consumed durable order event': x => !!x })) return;
  const paid = request('POST', `/api/payments/${payment}/simulate`, { status: 'SUCCESS' }, fixture.adminToken, 'payment');
  if (!check(paid, { 'payment captured': x => x.status === 200 && x.json('data.status') === 'SUCCESS' })) return;
  let confirmed = false;
  for (let poll = 0; poll < 60; poll++) {
    const o = request('GET', `/api/orders/${order}`, null, user.userToken, 'saga_poll');
    if (o.status === 200 && o.json('data.status') === 'CONFIRMED') { confirmed = true; break; }
    if (o.status !== 200) break;
    sleep(0.5);
  }
  completed.add(confirmed ? 1 : 0);
  check(confirmed, { 'Saga confirms order': x => x });
}
export function handleSummary(data) {
  data.fixture = { runId: fixture.runId, accounts: fixture.users.length, phase,
    note: 'Credentials excluded. Verify ownership, terminal payments and exact stock with Java after load.' };
  return { [__ENV.SUMMARY_FILE || 'multi-user-summary.json']: JSON.stringify(data, null, 2) };
}
