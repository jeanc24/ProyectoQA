import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

export default function Login() {
  const { isAuthenticated, login } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    if (isAuthenticated) {
      navigate("/products", { replace: true });
    }
  }, [isAuthenticated, navigate]);

  return (
    <div className="page">
      <h1>Inventario</h1>
      <p>Inicia sesión con Keycloak para gestionar productos.</p>
      <button type="button" onClick={login}>
        Iniciar sesión
      </button>
    </div>
  );
}