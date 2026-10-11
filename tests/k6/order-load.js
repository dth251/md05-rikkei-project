import http from 'k6/http';
import { check } from 'k6';
import execution from 'k6/execution';
import { Counter } from 'k6/metrics';

const fixture = JSON.parse(open(__ENV.FIXTURE_FILE));
const created = new Counter('orders_created');
const throttled = new Counter('status_429');
const status2xx = new Counter('status_2xx');
const status4xx = new Counter('status_4xx');
const status5xx = new Counter('status_5xx');
const transport = new Counter('status_0');
export const options = {
  scenarios: { orders: { executor: 'constant-arrival-rate', rate: Number(__ENV.RATE || 2),
    timeUnit: '1s', duration: __ENV.DURATION || '20s', preAllocatedVUs: 5, maxVUs: 20, gracefulStop: '10s' } },
  summaryTrendStats: ['avg', 'p(95)', 'p(99)', 'max'],
  thresholds: { http_req_failed: ['rate<0.01'], checks: ['rate==1'], status_429: ['count==0'] },
};
export default function () {
  const response = http.post(`${__ENV.BASE_URL}/api/orders`, JSON.stringify({
    channel: execution.scenario.iterationInTest % 2 === 0 ? 'WEB' : 'POS',
    idempotencyKey: `${fixture.runId}-load-${execution.scenario.iterationInTest}`,
    items: [{ productId: fixture.loadProductId, quantity: 1 }],
  }), { headers: { Authorization: `Bearer ${fixture.userToken}`, 'Content-Type': 'application/json' }, timeout: '30s' });
  created.add(response.status === 201 ? 1 : 0);
  throttled.add(response.status === 429 ? 1 : 0);
  status2xx.add(response.status >= 200 && response.status < 300 ? 1 : 0);
  status4xx.add(response.status >= 400 && response.status < 500 ? 1 : 0);
  status5xx.add(response.status >= 500 ? 1 : 0);
  transport.add(response.status === 0 ? 1 : 0);
  check(response, { 'new pending order through gateway': r => r.status === 201
    && r.headers['X-Gateway'] === 'api-gateway' && r.json('data.status') === 'PENDING_PAYMENT' });
}
export function handleSummary(data) {
  return { [__ENV.SUMMARY_FILE || 'order-summary.json']: JSON.stringify(data, null, 2) };
}
