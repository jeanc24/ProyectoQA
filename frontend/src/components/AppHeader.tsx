import type { ReactNode } from "react";
import { NavLink } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import "../styles/nav.css";

type Props = {
  title: string;
  subtitle?: string;
  actions?: ReactNode;
};

export default function AppHeader({ title, subtitle, actions }: Props) {
  const { username, logout, hasRole } = useAuth();
  const canViewProducts = hasRole("product:view");
  const canViewStock = hasRole("stock:view");
  const canViewDashboard = hasRole("report:view");

  return (
    <header className="app-header">
      <div className="app-header-top">
        <div>
          <h1>{title}</h1>
          <p>{subtitle ?? `Sesión: ${username ?? "—"}`}</p>
        </div>
        <div className="app-header-actions">
          {actions}
          <button type="button" onClick={logout}>
            Cerrar sesión
          </button>
        </div>
      </div>

      <nav className="app-nav" data-testid="app-nav" aria-label="Principal">
        {canViewProducts && (
          <NavLink
            to="/products"
            data-testid="nav-products"
            className={({ isActive }) =>
              isActive ? "app-nav-link active" : "app-nav-link"
            }
          >
            Productos
          </NavLink>
        )}
        {canViewStock && (
          <NavLink
            to="/stock"
            data-testid="nav-stock"
            className={({ isActive }) =>
              isActive ? "app-nav-link active" : "app-nav-link"
            }
          >
            Stock
          </NavLink>
        )}
        {canViewDashboard && (
          <NavLink
            to="/dashboard"
            data-testid="nav-dashboard"
            className={({ isActive }) =>
              isActive ? "app-nav-link active" : "app-nav-link"
            }
          >
            Dashboard
          </NavLink>
        )}
      </nav>
    </header>
  );
}
