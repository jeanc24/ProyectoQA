import { Link } from "react-router-dom";

function Products() {
  return (
    <div>
      <h1>Products</h1>
      <p>Esta es la página de productos.</p>

      <Link to="/login">Ir a Login</Link>
    </div>
  );
}

export default Products;