import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const throttled = new Counter('status_429');
export const options = {
  scenarios: { loginBurst: { executor: 'shared-iterations', vus: 20, iterations: 30, maxDuration: '20s' } },
  thresholds: { status_429: ['count>0'], checks: ['rate==1'] },
};
export default function () {
  const response = http.post(`${__ENV.BASE_URL}/api/auth/login`, JSON.stringify({
    email: 'day3-quota-probe@example.com', password: 'IntentionallyIncorrect',
  }), { headers: { 'Content-Type': 'application/json' }, timeout: '10s' });
  throttled.add(response.status === 429 ? 1 : 0);
  check(response, { 'invalid login or quota rejection': r => r.status === 401 || r.status === 429 });
}
export function handleSummary(data) {
  return { [__ENV.SUMMARY_FILE || 'rate-summary.json']: JSON.stringify(data, null, 2) };
}
