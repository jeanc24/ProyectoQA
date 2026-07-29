import type { ReactNode } from "react";
import { Link, NavLink } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { PERMISSIONS } from "../auth/permissions";
import "../styles/nav.css";

type Props = {
  title: string;
  subtitle?: string;
  actions?: ReactNode;
};

export default function AppHeader({ title, subtitle, actions }: Props) {
  const { username, logout, hasRole } = useAuth();
  const canViewProducts = hasRole(PERMISSIONS.productView);
  const canViewStock = hasRole(PERMISSIONS.stockView);
  const canViewDashboard = hasRole(PERMISSIONS.reportView);
  const canManageUsers = hasRole(PERMISSIONS.userManage);

  return (
    <header className="app-header">
      <div className="app-brand-row">
        <Link to="/products" className="app-brand">
          Inventario
        </Link>
        <span className="app-session-chip">Sesión: {username ?? "—"}</span>
      </div>

      <div className="app-header-top">
        <div>
          <h1>{title}</h1>
          <p>{subtitle ?? "Gestión de inventarios"}</p>
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
        {canManageUsers && (
          <NavLink
            to="/users"
            data-testid="nav-users"
            className={({ isActive }) =>
              isActive ? "app-nav-link active" : "app-nav-link"
            }
          >
            Usuarios
          </NavLink>
        )}
      </nav>
    </header>
  );
}
