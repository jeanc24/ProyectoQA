import { Link } from "react-router-dom";

function Login() {
  return (
    <div>
      <h1>Login</h1>
      <p>Esta es la página de login.</p>

      <Link to="/products">Ir a Products</Link>
    </div>
  );
}

export default Login;