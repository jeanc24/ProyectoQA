import Keycloak from "keycloak-js";

/**
 * Cliente Keycloak del frontend (adapter keycloak-js).
 *
 * Env (Vite):
 * - VITE_KEYCLOAK_URL       → http://localhost:8081
 * - VITE_KEYCLOAK_REALM     → inventory
 * - VITE_KEYCLOAK_CLIENT_ID → inventory-frontend  (client público + PKCE del realm)
 *
 * Lo usan AuthContext (init / login / logout / roles) y api/client.ts (Bearer).
 */
export const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL,
  realm: import.meta.env.VITE_KEYCLOAK_REALM,
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID,
});
