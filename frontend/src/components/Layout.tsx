import { NavLink } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import type { ReactNode } from "react";

type Props = {
  children: ReactNode;
};

export default function Layout({ children }: Props) {
  const { username, logout, hasRole } = useAuth();
  const canViewReports = hasRole("report:view");
  const canViewProducts = hasRole("product:view");

  return (
    <div className="app-shell">
      <nav className="app-nav" aria-label="Navegación principal">
        <div className="app-nav-brand">Inventario</div>
        <div className="app-nav-links">
          {canViewReports && (
            <NavLink
              to="/dashboard"
              className={({ isActive }) =>
                isActive ? "nav-link nav-link-active" : "nav-link"
              }
              data-testid="nav-dashboard"
            >
              Dashboard
            </NavLink>
          )}
          {canViewProducts && (
            <NavLink
              to="/products"
              className={({ isActive }) =>
                isActive ? "nav-link nav-link-active" : "nav-link"
              }
              data-testid="nav-products"
            >
              Productos
            </NavLink>
          )}
        </div>
        <div className="app-nav-actions">
          <span className="app-nav-user">{username ?? "—"}</span>
          <button type="button" onClick={logout}>
            Cerrar sesión
          </button>
        </div>
      </nav>
      <main className="app-main">{children}</main>
    </div>
  );
}
