/**
 * Shared helpers for k6 performance tests (TEST-04).
 *
 * Prefer K6_ACCESS_TOKEN from the host (iss=http://localhost:8081/...).
 * Tokens obtained via host.docker.internal get a mismatched issuer and the API returns 401.
 */
export const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8080';
export const KEYCLOAK_URL = __ENV.KEYCLOAK_URL || 'http://host.docker.internal:8081';
export const REALM = __ENV.KEYCLOAK_REALM || 'inventory';
export const CLIENT_ID = __ENV.KEYCLOAK_CLIENT_ID || 'inventory-api';
export const CLIENT_SECRET = __ENV.KEYCLOAK_CLIENT_SECRET || 'inventory-api-secret';
export const USERNAME = __ENV.K6_USERNAME || 'viewer';
export const PASSWORD = __ENV.K6_PASSWORD || 'viewer';

/**
 * Resolve access token: env override first, else password grant.
 * @param {typeof import('k6/http')} http
 */
export function fetchAccessToken(http) {
  if (__ENV.K6_ACCESS_TOKEN) {
    return __ENV.K6_ACCESS_TOKEN;
  }

  const url = `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token`;
  const res = http.post(
    url,
    {
      grant_type: 'password',
      client_id: CLIENT_ID,
      client_secret: CLIENT_SECRET,
      username: USERNAME,
      password: PASSWORD,
    },
    { tags: { name: 'keycloak_token' } }
  );
  if (res.status !== 200) {
    throw new Error(`Keycloak token failed: HTTP ${res.status} body=${res.body}`);
  }
  const body = res.json();
  if (!body.access_token) {
    throw new Error('Keycloak response missing access_token');
  }
  return body.access_token;
}

export function authHeaders(token) {
  return {
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'application/json',
    },
  };
}
