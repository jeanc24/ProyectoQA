import Keycloak from "keycloak-js";
// Saves the token in the browser's local storage 
// to avoid having to login every time the page is loaded
export const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL,
  realm: import.meta.env.VITE_KEYCLOAK_REALM,
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID,
});