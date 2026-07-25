import { useCallback, useEffect, useState, type FormEvent } from "react";
import { useAuth } from "../auth/AuthContext";
import AppHeader from "../components/AppHeader";
import ProductForm from "../components/ProductForm";
import { PERMISSIONS } from "../auth/permissions";
import {
  createProduct,
  deleteProduct,
  listProducts,
  updateProduct,
} from "../api/products";
import {
  getProductAuditHistory,
  type ProductRevision,
} from "../api/audit";
import { listCategories, type Category } from "../api/categories";
import { ApiError } from "../api/client";
import type { ProductRequest, ProductResponse } from "../types/product";
import "../styles/products.css";

type SortField = "name" | "price" | "quantity" | "sku";
type SortDir = "asc" | "desc";
type SearchField = "name" | "sku";

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

function formatRevisionType(type: string) {
  const t = type.toUpperCase();
  if (t === "ADD") return "Alta";
  if (t === "MOD" || t === "MODIFIED") return "Modificación";
  if (t === "DEL" || t === "DELETED") return "Baja";
  return type;
}

function formatTimestamp(iso: string) {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString();
}

export default function Products() {
  const { hasRole } = useAuth();
  const canManage = hasRole(PERMISSIONS.productManage);
  const canAudit = hasRole(PERMISSIONS.auditView);
  const showActions = canManage || canAudit;

  const [products, setProducts] = useState<ProductResponse[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<
    { field: string; message: string }[]
  >([]);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<ProductResponse | null>(null);
  const [auditProduct, setAuditProduct] = useState<ProductResponse | null>(
    null,
  );
  const [auditHistory, setAuditHistory] = useState<ProductRevision[]>([]);
  const [auditLoading, setAuditLoading] = useState(false);
  const [auditError, setAuditError] = useState<string | null>(null);

  // filters / paging / sort (applied to API)
  const [search, setSearch] = useState("");
  const [searchField, setSearchField] = useState<SearchField>("name");
  const [activeFilter, setActiveFilter] = useState<"all" | "true" | "false">(
    "all",
  );
  const [categoryId, setCategoryId] = useState<number | "">("");
  const [page, setPage] = useState(0);
  const [size] = useState(10);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [sortField, setSortField] = useState<SortField>("name");
  const [sortDir, setSortDir] = useState<SortDir>("asc");

  // draft search applied on submit / debounce-like via "Buscar" button and Enter
  const [appliedSearch, setAppliedSearch] = useState("");

  const loadProducts = useCallback(async () => {
    setLoading(true);
    setError(null);
    setFieldErrors([]);
    try {
      const pageResult = await listProducts({
        page,
        size,
        name: searchField === "name" && appliedSearch ? appliedSearch : undefined,
        sku: searchField === "sku" && appliedSearch ? appliedSearch : undefined,
        categoryId: categoryId === "" ? null : categoryId,
        active:
          activeFilter === "all"
            ? null
            : activeFilter === "true",
        sort: `${sortField},${sortDir}`,
      });
      setProducts(pageResult.content);
      setTotalPages(pageResult.totalPages);
      setTotalElements(pageResult.totalElements);
    } catch (err) {
      setError(
        err instanceof ApiError ? err.message : "Error al cargar productos",
      );
    } finally {
      setLoading(false);
    }
  }, [
    page,
    size,
    appliedSearch,
    searchField,
    categoryId,
    activeFilter,
    sortField,
    sortDir,
  ]);

  useEffect(() => {
    void loadProducts();
  }, [loadProducts]);

  useEffect(() => {
    void listCategories()
      .then(setCategories)
      .catch(() => setCategories([]));
  }, []);

  function handleApiError(err: unknown) {
    if (err instanceof ApiError) {
      setError(err.message || `No se pudo completar la operación (${err.status})`);
      setFieldErrors(err.fieldErrors ?? []);
      return;
    }
    setError("Error inesperado");
  }

  function applySearch(e?: FormEvent) {
    e?.preventDefault();
    setPage(0);
    setAppliedSearch(search.trim());
  }

  function toggleSort(field: SortField) {
    setPage(0);
    if (sortField === field) {
      setSortDir((d) => (d === "asc" ? "desc" : "asc"));
    } else {
      setSortField(field);
      setSortDir("asc");
    }
  }

  function sortIndicator(field: SortField) {
    if (sortField !== field) return "";
    return sortDir === "asc" ? " ↑" : " ↓";
  }

  async function handleCreate(data: ProductRequest) {
    try {
      await createProduct(data);
      setShowForm(false);
      // Keep the new row visible under current page size/sort
      setSearchField("name");
      setSearch(data.name);
      setAppliedSearch(data.name.trim());
      setPage(0);
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

  async function openAudit(product: ProductResponse) {
    setShowForm(false);
    setEditing(null);
    setAuditProduct(product);
    setAuditHistory([]);
    setAuditError(null);
    setAuditLoading(true);
    try {
      const history = await getProductAuditHistory(product.id);
      setAuditHistory(history);
    } catch (err) {
      setAuditError(
        err instanceof ApiError
          ? `${err.status}: ${err.message}`
          : "Error al cargar historial",
      );
    } finally {
      setAuditLoading(false);
    }
  }

  return (
    <div className="page page-left">
      <AppHeader
        title="Productos"
        actions={
          canManage ? (
            <button
              type="button"
              data-testid="create-product-button"
              onClick={() => {
                setEditing(null);
                setShowForm(true);
                setError(null);
                setFieldErrors([]);
              }}
            >
              Crear producto
            </button>
          ) : undefined
        }
      />

      <form
        className="filters-bar"
        data-testid="products-filters"
        onSubmit={applySearch}
      >
        <label className="filter-field">
          Buscar
          <input
            type="search"
            placeholder="Texto a buscar…"
            value={search}
            data-testid="products-search"
            onChange={(e) => setSearch(e.target.value)}
          />
        </label>

        <label className="filter-field">
          En
          <select
            value={searchField}
            data-testid="products-search-field"
            onChange={(e) => {
              setSearchField(e.target.value as SearchField);
              setPage(0);
            }}
          >
            <option value="name">Nombre</option>
            <option value="sku">SKU</option>
          </select>
        </label>

        <label className="filter-field">
          Estado
          <select
            value={activeFilter}
            data-testid="products-active-filter"
            onChange={(e) => {
              setActiveFilter(e.target.value as "all" | "true" | "false");
              setPage(0);
            }}
          >
            <option value="all">Todos</option>
            <option value="true">Activos</option>
            <option value="false">Inactivos</option>
          </select>
        </label>

        <label className="filter-field">
          Categoría
          <select
            value={categoryId === "" ? "" : String(categoryId)}
            data-testid="products-category-filter"
            onChange={(e) => {
              const v = e.target.value;
              setCategoryId(v === "" ? "" : Number(v));
              setPage(0);
            }}
          >
            <option value="">Todas</option>
            {categories.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </label>

        <button type="submit" data-testid="products-search-button">
          Buscar
        </button>
      </form>

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

      {auditProduct && (
        <section className="card" data-testid="product-audit-panel">
          <div className="audit-panel-header">
            <h2>
              Historial — {auditProduct.name}{" "}
              <span className="audit-sku">({auditProduct.sku})</span>
            </h2>
            <button
              type="button"
              data-testid="audit-close"
              onClick={() => {
                setAuditProduct(null);
                setAuditHistory([]);
                setAuditError(null);
              }}
            >
              Cerrar
            </button>
          </div>
          {auditLoading && <p>Cargando revisiones…</p>}
          {auditError && (
            <div className="alert alert-error" role="alert">
              {auditError}
            </div>
          )}
          {!auditLoading && !auditError && auditHistory.length === 0 && (
            <p>Sin revisiones registradas.</p>
          )}
          {!auditLoading && auditHistory.length > 0 && (
            <div className="table-wrap">
              <table className="products-table" data-testid="audit-history-table">
                <thead>
                  <tr>
                    <th>Rev</th>
                    <th>Tipo</th>
                    <th>Fecha</th>
                    <th>Usuario</th>
                    <th>Precio</th>
                    <th>Stock</th>
                    <th>Activo</th>
                  </tr>
                </thead>
                <tbody>
                  {auditHistory.map((rev) => (
                    <tr key={`${rev.productId}-${rev.revision}`}>
                      <td>{rev.revision}</td>
                      <td>{formatRevisionType(rev.revisionType)}</td>
                      <td>{formatTimestamp(rev.timestamp)}</td>
                      <td>{rev.username ?? "—"}</td>
                      <td>{rev.price}</td>
                      <td>{rev.quantity}</td>
                      <td>{rev.active ? "Sí" : "No"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      )}

      {loading ? (
        <p>Cargando productos...</p>
      ) : (
        <>
          <p className="results-meta" data-testid="products-results-meta">
            {totalElements} producto{totalElements === 1 ? "" : "s"} · página{" "}
            {totalPages === 0 ? 0 : page + 1} de {totalPages}
          </p>

          <div className="table-wrap">
            <table className="products-table" data-testid="products-table">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>
                    <button
                      type="button"
                      className="sort-btn"
                      data-testid="sort-name"
                      onClick={() => toggleSort("name")}
                    >
                      Nombre{sortIndicator("name")}
                    </button>
                  </th>
                  <th>
                    <button
                      type="button"
                      className="sort-btn"
                      data-testid="sort-sku"
                      onClick={() => toggleSort("sku")}
                    >
                      SKU{sortIndicator("sku")}
                    </button>
                  </th>
                  <th>
                    <button
                      type="button"
                      className="sort-btn"
                      data-testid="sort-price"
                      onClick={() => toggleSort("price")}
                    >
                      Precio{sortIndicator("price")}
                    </button>
                  </th>
                  <th>
                    <button
                      type="button"
                      className="sort-btn"
                      data-testid="sort-quantity"
                      onClick={() => toggleSort("quantity")}
                    >
                      Stock{sortIndicator("quantity")}
                    </button>
                  </th>
                  <th>Categoría</th>
                  <th>Activo</th>
                  {showActions && <th>Acciones</th>}
                </tr>
              </thead>
              <tbody>
                {products.length === 0 ? (
                  <tr>
                    <td colSpan={showActions ? 8 : 7}>No hay productos</td>
                  </tr>
                ) : (
                  products.map((product) => (
                    <tr
                      key={product.id}
                      className={
                        product.belowMinStock ? "row-low-stock" : undefined
                      }
                      data-low-stock={product.belowMinStock ? "true" : "false"}
                    >
                      <td>{product.id}</td>
                      <td>{product.name}</td>
                      <td>{product.sku}</td>
                      <td>{product.price}</td>
                      <td>
                        {product.quantity}
                        {product.belowMinStock && (
                          <span
                            className="badge-low-stock"
                            data-testid="low-stock-badge"
                            title={`Stock mínimo: ${product.minStock}`}
                          >
                            Bajo
                          </span>
                        )}
                      </td>
                      <td>{product.categoryName ?? "—"}</td>
                      <td>{product.active ? "Sí" : "No"}</td>
                      {showActions && (
                        <td className="actions">
                          {canAudit && (
                            <button
                              type="button"
                              data-testid="audit-history-button"
                              onClick={() => void openAudit(product)}
                            >
                              Historial
                            </button>
                          )}
                          {canManage && (
                            <>
                              <button
                                type="button"
                                onClick={() => {
                                  setShowForm(false);
                                  setAuditProduct(null);
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
                            </>
                          )}
                        </td>
                      )}
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>

          <div className="pagination" data-testid="products-pagination">
            <button
              type="button"
              disabled={page <= 0}
              data-testid="page-prev"
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              Anterior
            </button>
            <span>
              {totalPages === 0 ? 0 : page + 1} / {totalPages}
            </span>
            <button
              type="button"
              disabled={page + 1 >= totalPages}
              data-testid="page-next"
              onClick={() => setPage((p) => p + 1)}
            >
              Siguiente
            </button>
          </div>
        </>
      )}
    </div>
  );
}
