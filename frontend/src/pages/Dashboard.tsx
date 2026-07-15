import { useCallback, useEffect, useState } from "react";
import AppHeader from "../components/AppHeader";
import { ApiError } from "../api/client";
import {
  getInventorySummary,
  getLowStock,
  getRecentMovements,
  getTopProducts,
} from "../api/reports";
import type { ProductResponse } from "../types/product";
import type {
  InventorySummary,
  StockMovement,
  TopProduct,
} from "../types/report";
import "../styles/dashboard.css";

const GRAFANA_URL =
  import.meta.env.VITE_GRAFANA_URL ?? "http://localhost:3001";

function formatMoney(value: number | string): string {
  const n = typeof value === "number" ? value : Number(value);
  if (Number.isNaN(n)) return String(value);
  return new Intl.NumberFormat("es-DO", {
    style: "currency",
    currency: "DOP",
    maximumFractionDigits: 2,
  }).format(n);
}

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

function movementLabel(type: StockMovement["movementType"]): string {
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

export default function Dashboard() {
  const [summary, setSummary] = useState<InventorySummary | null>(null);
  const [lowStock, setLowStock] = useState<ProductResponse[]>([]);
  const [movements, setMovements] = useState<StockMovement[]>([]);
  const [topProducts, setTopProducts] = useState<TopProduct[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [s, low, recent, top] = await Promise.all([
        getInventorySummary(),
        getLowStock(20),
        getRecentMovements(15),
        getTopProducts(10),
      ]);
      setSummary(s);
      setLowStock(low);
      setMovements(recent);
      setTopProducts(top);
    } catch (err) {
      setError(
        err instanceof ApiError
          ? `${err.status}: ${err.message}`
          : "Error al cargar el dashboard",
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <div className="page page-left dashboard-page">
      <AppHeader
        title="Dashboard"
        subtitle="Resumen operacional del inventario"
        actions={
          <button type="button" data-testid="dashboard-refresh" onClick={() => void load()}>
            Actualizar
          </button>
        }
      />

      {error && (
        <div className="alert alert-error" role="alert" data-testid="dashboard-error">
          {error}
        </div>
      )}

      {loading && !summary ? (
        <p data-testid="dashboard-loading">Cargando dashboard...</p>
      ) : summary ? (
        <>
          <section
            className="kpi-grid"
            data-testid="dashboard-kpis"
            aria-label="Indicadores"
          >
            <article className="kpi">
              <span className="kpi-label">Total productos</span>
              <strong className="kpi-value" data-testid="kpi-total">
                {summary.totalProducts}
              </strong>
            </article>
            <article className="kpi">
              <span className="kpi-label">Activos</span>
              <strong className="kpi-value" data-testid="kpi-active">
                {summary.activeProducts}
              </strong>
              <span className="kpi-hint">
                {summary.inactiveProducts} inactivo
                {summary.inactiveProducts === 1 ? "" : "s"}
              </span>
            </article>
            <article className={`kpi ${summary.lowStockProducts > 0 ? "kpi-warn" : ""}`}>
              <span className="kpi-label">Stock crítico</span>
              <strong className="kpi-value" data-testid="kpi-low-stock">
                {summary.lowStockProducts}
              </strong>
              <span className="kpi-hint">{summary.totalUnits} unidades en total</span>
            </article>
            <article className="kpi">
              <span className="kpi-label">Valor inventario</span>
              <strong className="kpi-value kpi-value-sm" data-testid="kpi-value">
                {formatMoney(summary.inventoryValue)}
              </strong>
            </article>
          </section>

          <p className="ops-link-row">
            <a
              href={GRAFANA_URL}
              target="_blank"
              rel="noreferrer"
              data-testid="link-grafana"
            >
              Métricas de sistema (Grafana) →
            </a>
          </p>

          <div className="dash-columns">
            <section className="dash-panel" data-testid="panel-low-stock">
              <h2>Productos críticos</h2>
              <p className="panel-sub">Cantidad ≤ stock mínimo</p>
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>Producto</th>
                      <th>SKU</th>
                      <th>Stock</th>
                      <th>Mín.</th>
                    </tr>
                  </thead>
                  <tbody>
                    {lowStock.length === 0 ? (
                      <tr>
                        <td colSpan={4}>Sin productos críticos</td>
                      </tr>
                    ) : (
                      lowStock.map((p) => (
                        <tr key={p.id} className="row-critical">
                          <td>{p.name}</td>
                          <td>{p.sku}</td>
                          <td>{p.quantity}</td>
                          <td>{p.minStock}</td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </section>

            <section className="dash-panel" data-testid="panel-top-products">
              <h2>Más salidas</h2>
              <p className="panel-sub">Top por unidades OUT</p>
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>#</th>
                      <th>Producto</th>
                      <th>SKU</th>
                      <th>Unidades</th>
                    </tr>
                  </thead>
                  <tbody>
                    {topProducts.length === 0 ? (
                      <tr>
                        <td colSpan={4}>Sin movimientos de salida aún</td>
                      </tr>
                    ) : (
                      topProducts.map((p, i) => (
                        <tr key={p.productId}>
                          <td>{i + 1}</td>
                          <td>{p.productName}</td>
                          <td>{p.productSku}</td>
                          <td>{p.unitsOut}</td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </section>
          </div>

          <section className="dash-panel" data-testid="panel-movements">
            <h2>Movimientos recientes</h2>
            <p className="panel-sub">Últimas entradas, salidas y ajustes</p>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Fecha</th>
                    <th>Tipo</th>
                    <th>Producto</th>
                    <th>Δ</th>
                    <th>Antes → Después</th>
                    <th>Usuario</th>
                  </tr>
                </thead>
                <tbody>
                  {movements.length === 0 ? (
                    <tr>
                      <td colSpan={6}>Sin movimientos recientes</td>
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
                        <td>{m.quantityDelta > 0 ? `+${m.quantityDelta}` : m.quantityDelta}</td>
                        <td>
                          {m.quantityBefore} → {m.quantityAfter}
                        </td>
                        <td>{m.performedBy ?? "—"}</td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </section>
        </>
      ) : null}
    </div>
  );
}
