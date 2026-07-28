import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
import AppHeader from "../components/AppHeader";
import { useAuth } from "../auth/AuthContext";
import { PERMISSIONS } from "../auth/permissions";
import { ApiError } from "../api/client";
import { listProducts } from "../api/products";
import {
  createMovement,
  listMovements,
  type MovementType,
} from "../api/stock";
import type { ProductResponse } from "../types/product";
import type { StockMovement } from "../types/report";
import "../styles/stock.css";

function formatDate(iso: string): string {
  try {
    return new Intl.DateTimeFormat("es-DO", {
      dateStyle: "short",
      timeStyle: "short",
    }).format(new Date(iso));
  } catch {
    return iso;
  }
}

function movementLabel(type: MovementType): string {
  switch (type) {
    case "IN":
      return "Entrada";
    case "OUT":
      return "Salida";
    case "ADJUSTMENT":
      return "Ajuste";
    default:
      return type;
  }
}

export default function Stock() {
  const { hasRole } = useAuth();
  const canManage = hasRole(PERMISSIONS.stockManage);
  const canViewProducts = hasRole(PERMISSIONS.productView);

  const [products, setProducts] = useState<ProductResponse[]>([]);
  const [movements, setMovements] = useState<StockMovement[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  // filters
  const [filterProductId, setFilterProductId] = useState<number | "">("");
  const [filterType, setFilterType] = useState<MovementType | "">("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const size = 10;

  // form
  const [productId, setProductId] = useState<number | "">("");
  const [movementType, setMovementType] = useState<MovementType>("IN");
  const [quantity, setQuantity] = useState(1);
  const [notes, setNotes] = useState("");
  const [saving, setSaving] = useState(false);

  const lowStock = useMemo(
    () => products.filter((p) => p.belowMinStock && p.active),
    [products],
  );

  const selectedProduct = useMemo(
    () =>
      productId === ""
        ? null
        : products.find((p) => p.id === productId) ?? null,
    [products, productId],
  );

  const loadProducts = useCallback(async () => {
    if (!canViewProducts) {
      setProducts([]);
      return;
    }
    try {
      const pageResult = await listProducts({
        page: 0,
        size: 200,
        active: true,
        sort: "name,asc",
      });
      setProducts(pageResult.content);
    } catch {
      // product list is optional for history-only users
      setProducts([]);
    }
  }, [canViewProducts]);

  const loadMovements = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await listMovements({
        page,
        size,
        productId: filterProductId === "" ? null : filterProductId,
        movementType: filterType,
        sort: "createdAt,desc",
      });
      setMovements(result.content);
      setTotalPages(result.totalPages);
      setTotalElements(result.totalElements);
    } catch (err) {
      setError(
        err instanceof ApiError
          ? `${err.status}: ${err.message}`
          : "Error al cargar movimientos",
      );
    } finally {
      setLoading(false);
    }
  }, [page, size, filterProductId, filterType]);

  useEffect(() => {
    void loadProducts();
  }, [loadProducts]);

  useEffect(() => {
    void loadMovements();
  }, [loadMovements]);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSuccess(null);

    if (productId === "") {
      setError("Selecciona un producto");
      return;
    }
    if (quantity < 0 || (movementType !== "ADJUSTMENT" && quantity < 1)) {
      setError(
        movementType === "ADJUSTMENT"
          ? "La cantidad del ajuste debe ser ≥ 0"
          : "La cantidad debe ser ≥ 1",
      );
      return;
    }

    setSaving(true);
    try {
      const created = await createMovement({
        productId,
        movementType,
        quantity,
        notes: notes.trim() || undefined,
      });
      setSuccess(
        `${movementLabel(movementType)} registrada: ${created.productName} (${created.quantityBefore} → ${created.quantityAfter})`,
      );
      setNotes("");
      setQuantity(movementType === "ADJUSTMENT" ? 0 : 1);
      await Promise.all([loadProducts(), loadMovements()]);
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.status === 403) {
          setError(
            "No tienes permiso para registrar movimientos (falta stock:manage).",
          );
        } else if (err.status === 400) {
          setError(err.message || "No se pudo registrar el movimiento.");
        } else if (err.status === 404) {
          setError(err.message || "Producto no encontrado.");
        } else {
          setError(err.message || "Error al registrar el movimiento.");
        }
      } else {
        setError("Error inesperado al registrar el movimiento");
      }
    } finally {
      setSaving(false);
    }
  }

  const quantityHint =
    movementType === "IN"
      ? "Unidades a agregar al stock"
      : movementType === "OUT"
        ? "Unidades a restar del stock"
        : "Nueva cantidad absoluta del producto";

  return (
    <div className="page page-left stock-page">
      <AppHeader
        title="Stock"
        subtitle="Entradas, salidas, ajustes e historial"
      />

      {lowStock.length > 0 && (
        <div
          className="alert alert-warn"
          role="status"
          data-testid="low-stock-alert"
        >
          <strong>
            {lowStock.length} producto{lowStock.length === 1 ? "" : "s"} en stock
            mínimo o por debajo:
          </strong>
          <ul>
            {lowStock.slice(0, 8).map((p) => (
              <li key={p.id}>
                {p.name} ({p.sku}): {p.quantity} / mín. {p.minStock}
              </li>
            ))}
            {lowStock.length > 8 && <li>…y {lowStock.length - 8} más</li>}
          </ul>
        </div>
      )}

      {error && (
        <div className="alert alert-error" role="alert" data-testid="stock-error">
          {error}
        </div>
      )}
      {success && (
        <div className="alert alert-ok" role="status" data-testid="stock-success">
          {success}
        </div>
      )}

      {canManage && (
        <section className="stock-form-card" data-testid="stock-form">
          <h2>Registrar movimiento</h2>
          <form onSubmit={handleSubmit}>
            <div className="stock-form-grid">
              <label>
                Producto
                <select
                  required
                  data-testid="stock-product"
                  value={productId === "" ? "" : String(productId)}
                  onChange={(e) =>
                    setProductId(e.target.value === "" ? "" : Number(e.target.value))
                  }
                >
                  <option value="">Seleccionar…</option>
                  {products.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name} ({p.sku}) — stock {p.quantity}
                      {p.belowMinStock ? " ⚠" : ""}
                    </option>
                  ))}
                </select>
              </label>

              <label>
                Tipo
                <select
                  data-testid="stock-type"
                  value={movementType}
                  onChange={(e) =>
                    setMovementType(e.target.value as MovementType)
                  }
                >
                  <option value="IN">Entrada (IN)</option>
                  <option value="OUT">Salida (OUT)</option>
                  <option value="ADJUSTMENT">Ajuste (ADJUSTMENT)</option>
                </select>
              </label>

              <label>
                Cantidad
                <input
                  type="number"
                  min={movementType === "ADJUSTMENT" ? 0 : 1}
                  required
                  data-testid="stock-quantity"
                  value={quantity}
                  onChange={(e) => setQuantity(Number(e.target.value))}
                />
                <span className="field-hint">{quantityHint}</span>
              </label>

              <label className="span-2">
                Notas (opcional)
                <input
                  type="text"
                  maxLength={500}
                  data-testid="stock-notes"
                  value={notes}
                  onChange={(e) => setNotes(e.target.value)}
                  placeholder="Motivo del movimiento…"
                />
              </label>
            </div>

            {selectedProduct && (
              <p className="stock-preview" data-testid="stock-preview">
                Stock actual: <strong>{selectedProduct.quantity}</strong>
                {selectedProduct.belowMinStock && (
                  <span className="badge-low"> Bajo mínimo</span>
                )}
                {movementType === "IN" && (
                  <> → quedaría {selectedProduct.quantity + quantity}</>
                )}
                {movementType === "OUT" && (
                  <>
                    {" "}
                    → quedaría{" "}
                    {Math.max(0, selectedProduct.quantity - quantity)}
                    {quantity > selectedProduct.quantity &&
                      " (insuficiente — la API rechazará)"}
                  </>
                )}
                {movementType === "ADJUSTMENT" && (
                  <> → se fijará en {quantity}</>
                )}
              </p>
            )}

            <button
              type="submit"
              disabled={saving}
              data-testid="stock-submit"
            >
              {saving ? "Registrando…" : "Registrar"}
            </button>
          </form>
        </section>
      )}

      {!canManage && (
        <p className="stock-readonly-note" data-testid="stock-readonly">
          Solo lectura
        </p>
      )}

      <section className="stock-history" data-testid="stock-history">
        <div className="stock-history-head">
          <h2>Historial de movimientos</h2>
          <div className="stock-filters">
            <label>
              Producto
              <select
                data-testid="filter-product"
                value={filterProductId === "" ? "" : String(filterProductId)}
                onChange={(e) => {
                  setPage(0);
                  setFilterProductId(
                    e.target.value === "" ? "" : Number(e.target.value),
                  );
                }}
              >
                <option value="">Todos</option>
                {products.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Tipo
              <select
                data-testid="filter-type"
                value={filterType}
                onChange={(e) => {
                  setPage(0);
                  setFilterType(e.target.value as MovementType | "");
                }}
              >
                <option value="">Todos</option>
                <option value="IN">Entrada</option>
                <option value="OUT">Salida</option>
                <option value="ADJUSTMENT">Ajuste</option>
              </select>
            </label>
          </div>
        </div>

        <p className="results-meta">
          {totalElements} movimiento{totalElements === 1 ? "" : "s"}
        </p>

        {loading ? (
          <p>Cargando historial…</p>
        ) : (
          <>
            <div className="table-wrap">
              <table data-testid="movements-table">
                <thead>
                  <tr>
                    <th>Fecha</th>
                    <th>Tipo</th>
                    <th>Producto</th>
                    <th>Δ</th>
                    <th>Antes → Después</th>
                    <th>Usuario</th>
                    <th>Notas</th>
                  </tr>
                </thead>
                <tbody>
                  {movements.length === 0 ? (
                    <tr>
                      <td colSpan={7}>No hay movimientos</td>
                    </tr>
                  ) : (
                    movements.map((m) => (
                      <tr key={m.id}>
                        <td>{formatDate(m.createdAt)}</td>
                        <td>
                          <span
                            className={`mv-badge mv-${m.movementType.toLowerCase()}`}
                          >
                            {movementLabel(m.movementType)}
                          </span>
                        </td>
                        <td>
                          {m.productName}
                          <span className="muted"> · {m.productSku}</span>
                        </td>
                        <td>
                          {m.quantityDelta > 0
                            ? `+${m.quantityDelta}`
                            : m.quantityDelta}
                        </td>
                        <td>
                          {m.quantityBefore} → {m.quantityAfter}
                        </td>
                        <td>{m.performedBy ?? "—"}</td>
                        <td>{m.notes ?? "—"}</td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>

            <div className="pagination" data-testid="stock-pagination">
              <button
                type="button"
                disabled={page <= 0}
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
                onClick={() => setPage((p) => p + 1)}
              >
                Siguiente
              </button>
            </div>
          </>
        )}
      </section>
    </div>
  );
}
