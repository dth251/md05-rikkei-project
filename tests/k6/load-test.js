import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const statuses = {
  status_2xx: new Counter('status_2xx'),
  status_4xx: new Counter('status_4xx'),
  status_5xx: new Counter('status_5xx'),
  status_429: new Counter('status_429'),
};
export const options = {
  scenarios: {
    catalog: {
      executor: 'constant-arrival-rate', rate: Number(__ENV.RATE || 50),
      timeUnit: '1s', duration: __ENV.DURATION || '20s',
      preAllocatedVUs: 30, maxVUs: 100, gracefulStop: '5s',
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(95)', 'p(99)'],
  thresholds: {
    http_req_failed: [{ threshold: 'rate<0.05', abortOnFail: true, delayAbortEval: '5s' }],
    http_req_duration: [{ threshold: 'p(95)<2000', abortOnFail: true, delayAbortEval: '5s' }],
    checks: ['rate>0.99'],
  },
};
export default function () {
  const response = http.get(`${__ENV.BASE_URL}/api/products?page=0&size=20`, { timeout: '3s' });
  for (const [name, metric] of Object.entries(statuses)) {
    const matched = name === 'status_429' ? response.status === 429
      : name === 'status_2xx' ? response.status >= 200 && response.status < 300
      : name === 'status_4xx' ? response.status >= 400 && response.status < 500
      : response.status >= 500;
    metric.add(matched ? 1 : 0);
  }
  check(response, {
    'catalog 200 through gateway': r => r.status === 200 && r.headers['X-Gateway'] === 'api-gateway',
    'successful API envelope': r => r.status === 200 && r.json('success') === true,
  });
}
export function handleSummary(data) {
  return { [__ENV.SUMMARY_FILE || 'catalog-summary.json']: JSON.stringify(data, null, 2) };
}
