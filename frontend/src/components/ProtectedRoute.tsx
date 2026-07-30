import { Navigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import type { ReactNode } from "react";

type Props = {
  children: ReactNode;
  /** Si se pasa, exige ese client role (ej. PERMISSIONS.reportView). */
  requiredRole?: string;
};

/**
 * Guarda de rutas (solo UX).
 *
 * Bloques:
 * 1. Sin sesión → redirige a "/" (landing / login)
 * 2. Sin el rol pedido → "/unauthorized"
 * 3. OK → renderiza children
 *
 * No sustituye la seguridad del backend (@PreAuthorize).
 */
export default function ProtectedRoute({ children, requiredRole }: Props) {
  const { isAuthenticated, hasRole } = useAuth();

  if (!isAuthenticated) {
    return <Navigate to="/" replace />;
  }

  if (requiredRole && !hasRole(requiredRole)) {
    return <Navigate to="/unauthorized" replace />;
  }

  return children;
}
