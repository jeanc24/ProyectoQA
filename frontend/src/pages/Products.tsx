import { useCallback, useEffect, useState } from "react";
import { useAuth } from "../auth/AuthContext";
import ProductForm from "../components/ProductForm";
import {
  createProduct,
  deleteProduct,
  listProducts,
  updateProduct,
} from "../api/products";
import { ApiError } from "../api/client";
import type { ProductRequest, ProductResponse } from "../types/product";

function toForm(product: ProductResponse): ProductRequest {
  return {
    name: product.name,
    sku: product.sku,
    description: product.description ?? "",
    categoryId: product.categoryId,
    price: product.price,
    quantity: product.quantity,
    minStock: product.minStock,
    active: product.active,
  };
}

export default function Products() {
  const { username, logout, hasRole } = useAuth();
  const canManage = hasRole("product:manage");

  const [products, setProducts] = useState<ProductResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<
    { field: string; message: string }[]
  >([]);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<ProductResponse | null>(null);

  const loadProducts = useCallback(async () => {
    setLoading(true);
    setError(null);
    setFieldErrors([]);
    try {
      const page = await listProducts();
      setProducts(page.content);
    } catch (err) {
      setError(
        err instanceof ApiError ? err.message : "Error al cargar productos",
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadProducts();
  }, [loadProducts]);

  function handleApiError(err: unknown) {
    if (err instanceof ApiError) {
      setError(`${err.status}: ${err.message}`);
      setFieldErrors(err.fieldErrors ?? []);
      return;
    }
    setError("Error inesperado");
  }

  async function handleCreate(data: ProductRequest) {
    try {
      await createProduct(data);
      setShowForm(false);
      await loadProducts();
    } catch (err) {
      handleApiError(err);
    }
  }

  async function handleUpdate(data: ProductRequest) {
    if (!editing) return;
    try {
      await updateProduct(editing.id, data);
      setEditing(null);
      await loadProducts();
    } catch (err) {
      handleApiError(err);
    }
  }

  async function handleDelete(product: ProductResponse) {
    if (!window.confirm(`¿Eliminar "${product.name}"?`)) return;
    try {
      await deleteProduct(product.id);
      await loadProducts();
    } catch (err) {
      handleApiError(err);
    }
  }

  return (
    <div className="page page-left">
      <header className="page-header">
        <div>
          <h1>Productos</h1>
          <p>Sesión: {username ?? "—"}</p>
        </div>
        <div className="actions">
          {canManage && (
            <button
              type="button"
              onClick={() => {
                setEditing(null);
                setShowForm(true);
                setError(null);
                setFieldErrors([]);
              }}
            >
              Crear producto
            </button>
          )}
          <button type="button" onClick={logout}>
            Cerrar sesión
          </button>
        </div>
      </header>

      {error && (
        <div className="alert alert-error" role="alert">
          {error}
          {fieldErrors.length > 0 && (
            <ul>
              {fieldErrors.map((fe) => (
                <li key={fe.field}>
                  {fe.field}: {fe.message}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}

      {showForm && (
        <section className="card">
          <h2>Nuevo producto</h2>
          <ProductForm
            submitLabel="Crear"
            onSubmit={handleCreate}
            onCancel={() => setShowForm(false)}
          />
        </section>
      )}

      {editing && (
        <section className="card">
          <h2>Editar producto</h2>
          <ProductForm
            initial={toForm(editing)}
            submitLabel="Guardar"
            onSubmit={handleUpdate}
            onCancel={() => setEditing(null)}
          />
        </section>
      )}

      {loading ? (
        <p>Cargando productos...</p>
      ) : (
        <table className="products-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Nombre</th>
              <th>SKU</th>
              <th>Precio</th>
              <th>Stock</th>
              <th>Categoría</th>
              <th>Activo</th>
              {canManage && <th>Acciones</th>}
            </tr>
          </thead>
          <tbody>
            {products.map((product) => (
              <tr key={product.id}>
                <td>{product.id}</td>
                <td>{product.name}</td>
                <td>{product.sku}</td>
                <td>{product.price}</td>
                <td>{product.quantity}</td>
                <td>{product.categoryName ?? "—"}</td>
                <td>{product.active ? "Sí" : "No"}</td>
                {canManage && (
                  <td className="actions">
                    <button
                      type="button"
                      onClick={() => {
                        setShowForm(false);
                        setEditing(product);
                        setError(null);
                        setFieldErrors([]);
                      }}
                    >
                      Editar
                    </button>
                    <button
                      type="button"
                      onClick={() => handleDelete(product)}
                    >
                      Eliminar
                    </button>
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}