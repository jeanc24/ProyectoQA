/**
 * TEST-04 — Load test: GET /api/v1/products
 *
 * Escenario: rampa suave de usuarios concurrentes (throughput + latencia).
 * Umbrales (documentados en docs/final/testing/k6/README.md):
 *   - http_req_duration p(95) < 500 ms
 *   - http_req_failed rate < 1%
 *
 * Ejecutar: ./scripts/k6-run.sh load
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.4/index.js';
import { BASE_URL, fetchAccessToken, authHeaders } from './helpers.js';

export const options = {
  scenarios: {
    load_products: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '20s', target: 5 },
        { duration: '40s', target: 15 },
        { duration: '30s', target: 15 },
        { duration: '20s', target: 0 },
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
};

export function setup() {
  const health = http.get(`${BASE_URL}/actuator/health`);
  if (health.status !== 200) {
    throw new Error(`API health failed at ${BASE_URL}: HTTP ${health.status}`);
  }
  return { token: fetchAccessToken(http) };
}

export default function (data) {
  const res = http.get(`${BASE_URL}/api/v1/products?size=20`, authHeaders(data.token));
  check(res, {
    'status is 200': (r) => r.status === 200,
    'body is JSON array/page': (r) => {
      try {
        const j = r.json();
        return j !== null && (Array.isArray(j) || Array.isArray(j.content) || typeof j === 'object');
      } catch (_) {
        return false;
      }
    },
  });
  sleep(1);
}

export function handleSummary(data) {
  const outDir = __ENV.K6_OUT_DIR || 'docs/final/testing/k6';
  return {
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
    [`${outDir}/load-products-summary.json`]: JSON.stringify(data, null, 2),
    [`${outDir}/load-products-summary.txt`]: textSummary(data, { indent: ' ', enableColors: false }),
  };
}
