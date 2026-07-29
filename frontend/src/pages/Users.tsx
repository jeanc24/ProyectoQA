import { useCallback, useEffect, useState } from "react";
import AppHeader from "../components/AppHeader";
import { ApiError } from "../api/client";
import { listUsers } from "../api/users";
import type { UserResponse } from "../types/user";
import "../styles/products.css";
import "../styles/users.css";

const KEYCLOAK_URL =
  import.meta.env.VITE_KEYCLOAK_URL ?? "http://localhost:8081";
const KEYCLOAK_REALM =
  import.meta.env.VITE_KEYCLOAK_REALM ?? "inventory";

export default function Users() {
  const [users, setUsers] = useState<UserResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setUsers(await listUsers());
    } catch (err) {
      setError(
        err instanceof ApiError
          ? `${err.status}: ${err.message}`
          : "No se pudo cargar el directorio de usuarios",
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <div className="page page-left users-page">
      <AppHeader
        title="Usuarios"
        subtitle="Directorio de lectura del realm (solo admin)"
        actions={
          <button type="button" onClick={() => void load()} disabled={loading}>
            Actualizar
          </button>
        }
      />

      <aside className="users-keycloak-note" data-testid="users-keycloak-note">
        <p>
          <strong>Solo consulta.</strong> Esta pantalla lista los usuarios del
          realm <code>{KEYCLOAK_REALM}</code> y sus roles del cliente{" "}
          <code>inventory-api</code>. Para crear usuarios, cambiar contraseñas o
          asignar permisos debes hacerlo en la{" "}
          <a
            href={KEYCLOAK_URL}
            target="_blank"
            rel="noreferrer"
            data-testid="users-keycloak-link"
          >
            consola de administración de Keycloak
          </a>
          .
        </p>
      </aside>

      {error && (
        <p className="alert alert-error" role="alert">
          {error}
        </p>
      )}

      {loading && !users.length ? (
        <p className="muted">Cargando usuarios…</p>
      ) : (
        <div className="table-wrap">
          <table className="data-table" data-testid="users-table">
            <thead>
              <tr>
                <th>Usuario</th>
                <th>Nombre</th>
                <th>Email</th>
                <th>Estado</th>
                <th>Roles (inventory-api)</th>
              </tr>
            </thead>
            <tbody>
              {users.map((u) => (
                <tr key={u.id || u.username}>
                  <td>
                    <code>{u.username}</code>
                  </td>
                  <td>
                    {[u.firstName, u.lastName].filter(Boolean).join(" ") || "—"}
                  </td>
                  <td>{u.email ?? "—"}</td>
                  <td>
                    <span
                      className={
                        u.enabled ? "user-badge on" : "user-badge off"
                      }
                    >
                      {u.enabled ? "Activo" : "Inactivo"}
                    </span>
                  </td>
                  <td>
                    <div className="user-roles">
                      {u.roles.length === 0 ? (
                        <span className="muted">Sin roles de cliente</span>
                      ) : (
                        u.roles.map((role) => (
                          <span key={role} className="user-role-chip">
                            {role}
                          </span>
                        ))
                      )}
                    </div>
                  </td>
                </tr>
              ))}
              {!loading && users.length === 0 && (
                <tr>
                  <td colSpan={5} className="muted">
                    No hay usuarios en el realm.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
