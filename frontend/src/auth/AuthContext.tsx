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
  
  const API_CLIENT_ID = "inventory-api";
  
  export function AuthProvider({ children }: { children: ReactNode }) {
    const [ready, setReady] = useState(false);
    const [isAuthenticated, setIsAuthenticated] = useState(false);
  
    useEffect(() => {
      keycloak.onTokenExpired = () => {
        keycloak.updateToken(30).catch(() => {
          keycloak.logout({ redirectUri: window.location.origin + "/login" });
        });
      };

      // Check if the user is already authenticated
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
  
    // Redirects to keycloak login page and redirects to the products page after successful login
    const login = useCallback(() => {
      keycloak.login({
        redirectUri: window.location.origin + "/products",
      });
    }, []);
  
    // Redirects to keycloak logout page and redirects to the login page after successful logout
    const logout = useCallback(() => {
      keycloak.logout({
        redirectUri: window.location.origin + "/login",
      });
    }, []);
  
    // Checks if the user has the given role
    const hasRole = useCallback((role: string) => {
      return keycloak.hasResourceRole(role, API_CLIENT_ID);
    }, []);
  
    const value = useMemo(
      () => ({
        // The ready state is true if the keycloak instance is ready 
        // to prevent the user from accessing the application before it is ready
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
  
  export function useAuth() {
    const ctx = useContext(AuthContext);
    if (!ctx) {
      throw new Error("useAuth debe usarse dentro de AuthProvider");
    }
    return ctx;
  }