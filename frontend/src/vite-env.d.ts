/// <reference types="vite/client" />
// Defines the environment variables for the whole application
interface ImportMetaEnv {
    readonly VITE_KEYCLOAK_URL: string;
    readonly VITE_KEYCLOAK_REALM: string;
    readonly VITE_KEYCLOAK_CLIENT_ID: string;
    readonly VITE_API_URL: string;
    readonly VITE_GRAFANA_URL?: string;
  }
  
  interface ImportMeta {
    readonly env: ImportMetaEnv;
  }