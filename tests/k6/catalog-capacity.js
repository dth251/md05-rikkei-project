import http from 'k6/http';
import { check } from 'k6';
import execution from 'k6/execution';
import { Counter } from 'k6/metrics';

const fixture = JSON.parse(open(__ENV.FIXTURE_FILE));
const rate = Number(__ENV.RATE || 500);
if (fixture.users.length < 50 || ![500, 1000].includes(rate)) {
  throw new Error('Capacity demo needs 50 real Auth accounts and RATE=500 or 1000');
}
const successful = new Counter('successful_requests');
const limited = new Counter('rate_limited_requests');
const serverErrors = new Counter('server_error_requests');
const transportErrors = new Counter('transport_error_requests');
export const options = {
  scenarios: { catalog: { executor: 'constant-arrival-rate', rate, timeUnit: '1s',
    duration: __ENV.DURATION || '10s', preAllocatedVUs: 100, maxVUs: 200, gracefulStop: '5s' } },
  summaryTrendStats: ['avg', 'p(95)', 'p(99)', 'max'],
  thresholds: { http_req_failed: ['rate<0.01'], http_req_duration: ['p(95)<2000'],
    checks: ['rate==1'], dropped_iterations: ['count==0'] },
};
export default function () {
  const user = fixture.users[execution.scenario.iterationInTest % fixture.users.length];
  const response = http.get(`${__ENV.BASE_URL}/api/products?page=0&size=20`, {
    headers: { Authorization: `Bearer ${user.userToken}` }, timeout: '3s',
    tags: { name: 'GET /api/products (capacity)' },
  });
  successful.add(response.status === 200 ? 1 : 0);
  limited.add(response.status === 429 ? 1 : 0);
  serverErrors.add(response.status >= 500 ? 1 : 0);
  transportErrors.add(response.status === 0 ? 1 : 0);
  check(response, { 'catalog 200 through Gateway': r => r.status === 200
    && r.headers['X-Gateway'] === 'api-gateway' && r.json('success') === true });
}
export function handleSummary(data) {
  data.workload = { targetRequestsPerSecond: rate, accounts: fixture.users.length,
    route: 'GET /api/products', note: 'One request per iteration; credentials excluded; quotas unchanged.' };
  return { [__ENV.SUMMARY_FILE || 'catalog-capacity-summary.json']: JSON.stringify(data, null, 2) };
}
