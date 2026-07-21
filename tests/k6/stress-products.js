/**
 * TEST-04 — Stress test: GET /api/v1/products
 *
 * Escenario: picos de concurrencia por encima de carga normal.
 * Umbrales más permisivos (el sistema puede degradar, no colapsar):
 *   - http_req_duration p(95) < 2000 ms
 *   - http_req_failed rate < 5%
 *
 * Ejecutar: ./scripts/k6-run.sh stress
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.4/index.js';
import { BASE_URL, fetchAccessToken, authHeaders } from './helpers.js';

export const options = {
  scenarios: {
    stress_products: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '15s', target: 20 },
        { duration: '30s', target: 50 },
        { duration: '30s', target: 80 },
        { duration: '20s', target: 80 },
        { duration: '20s', target: 0 },
      ],
      gracefulRampDown: '15s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<2000'],
    http_req_failed: ['rate<0.05'],
    checks: ['rate>0.95'],
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
  const res = http.get(`${BASE_URL}/api/v1/products?size=50`, authHeaders(data.token));
  check(res, {
    'status is 200': (r) => r.status === 200,
  });
  sleep(0.3);
}

export function handleSummary(data) {
  const outDir = __ENV.K6_OUT_DIR || 'docs/final/testing/k6';
  return {
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
    [`${outDir}/stress-products-summary.json`]: JSON.stringify(data, null, 2),
    [`${outDir}/stress-products-summary.txt`]: textSummary(data, { indent: ' ', enableColors: false }),
  };
}
