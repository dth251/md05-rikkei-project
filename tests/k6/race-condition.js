import http from 'k6/http';
import { check } from 'k6';
import execution from 'k6/execution';
import { Counter } from 'k6/metrics';

const fixture = JSON.parse(open(__ENV.FIXTURE_FILE));
const created = new Counter('orders_created');
const rejected = new Counter('stock_conflicts');
const status2xx = new Counter('status_2xx');
const status4xx = new Counter('status_4xx');
const status5xx = new Counter('status_5xx');
const throttled = new Counter('status_429');
const transport = new Counter('status_0');
export const options = {
  scenarios: { race: { executor: 'per-vu-iterations', vus: 100, iterations: 1, maxDuration: '90s' } },
  summaryTrendStats: ['avg', 'p(95)', 'p(99)', 'max'],
  thresholds: { orders_created: ['count==1'], stock_conflicts: ['count==99'], checks: ['rate==1'] },
};
export default function () {
  const response = http.post(`${__ENV.ORDER_URL}/api/orders`, JSON.stringify({
    channel: 'WEB', idempotencyKey: `${fixture.runId}-race-${execution.vu.idInTest}`,
    items: [{ productId: fixture.raceProductId, quantity: 1 }],
  }), { headers: { Authorization: `Bearer ${fixture.userToken}`, 'Content-Type': 'application/json' }, timeout: '60s' });
  created.add(response.status === 201 ? 1 : 0);
  rejected.add(response.status === 409 ? 1 : 0);
  status2xx.add(response.status >= 200 && response.status < 300 ? 1 : 0);
  status4xx.add(response.status >= 400 && response.status < 500 ? 1 : 0);
  status5xx.add(response.status >= 500 ? 1 : 0);
  throttled.add(response.status === 429 ? 1 : 0);
  transport.add(response.status === 0 ? 1 : 0);
  check(response, { 'created or stock conflict, no gateway quota': r => r.status === 201 || r.status === 409 });
}
export function handleSummary(data) {
  return { [__ENV.SUMMARY_FILE || 'race-summary.json']: JSON.stringify(data, null, 2) };
}
