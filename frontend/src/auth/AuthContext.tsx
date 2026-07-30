import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { keycloak } from "./keycloak.ts";

type AuthContextValue = {
  ready: boolean;
  isAuthenticated: boolean;
  username: string | undefined;
  login: () => void;
  logout: () => void;
  hasRole: (role: string) => boolean;
};

const AuthContext = createContext<AuthContextValue | null>(null);

/** Client del realm cuyas roles miramos (debe coincidir con inventory-realm.json). */
const API_CLIENT_ID = "inventory-api";

/**
 * Estado de sesión Keycloak para toda la app.
 *
 * Bloques:
 * 1. init (check-sso + PKCE) al montar
 * 2. login / logout (redirect a Keycloak)
 * 3. hasRole → resource roles de inventory-api (misma fuente que el JWT de la API)
 * 4. refresh automático cuando el token expira
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false);
  const [isAuthenticated, setIsAuthenticated] = useState(false);

  useEffect(() => {
    // Renovar token o cerrar sesión si falla el refresh
    keycloak.onTokenExpired = () => {
      keycloak.updateToken(30).catch(() => {
        keycloak.logout({ redirectUri: window.location.origin + "/" });
      });
    };

    // check-sso: no fuerza login; solo restaura sesión si ya hay SSO/cookies
    keycloak
      .init({
        onLoad: "check-sso",
        pkceMethod: "S256",
      })
      .then((authenticated) => {
        setIsAuthenticated(authenticated);
        setReady(true);
      })
      .catch(() => setReady(true));
  }, []);

  // Tras login, Keycloak vuelve a /management (App.tsx redirige a /products)
  const login = useCallback(() => {
    keycloak.login({
      redirectUri: window.location.origin + "/management",
    });
  }, []);

  const logout = useCallback(() => {
    keycloak.logout({
      redirectUri: window.location.origin + "/",
    });
  }, []);

  // Roles del client inventory-api (product:view, stock:manage, …)
  const hasRole = useCallback((role: string) => {
    return keycloak.hasResourceRole(role, API_CLIENT_ID);
  }, []);

  const value = useMemo(
    () => ({
      ready,
      isAuthenticated,
      username: keycloak.tokenParsed?.preferred_username as string | undefined,
      login,
      logout,
      hasRole,
    }),
    [ready, isAuthenticated, login, logout, hasRole],
  );

  if (!ready) {
    return <p>Cargando sesión...</p>;
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

/** Hook: usar solo dentro de <AuthProvider>. */
export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth debe usarse dentro de AuthProvider");
  }
  return ctx;
}
