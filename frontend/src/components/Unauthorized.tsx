import { Link } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

export default function Unauthorized() {
  const { logout } = useAuth();

  return (
    <div className="page">
      <h1>Acceso denegado</h1>
      <p>No tienes permisos para ver esta página.</p>
      <div className="actions">
        <Link to="/login">Volver al login</Link>
        <button type="button" onClick={logout}>
          Cerrar sesión
        </button>
      </div>
    </div>
  );
}