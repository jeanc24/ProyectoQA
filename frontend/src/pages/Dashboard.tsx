import { useCallback, useEffect, useState } from "react";
import { ApiError } from "../api/client";
import {
  getInventorySummary,
  getLowStockProducts,
  getRecentMovements,
  getTopProducts,
} from "../api/reports";
import Layout from "../components/Layout";
import type {
  InventorySummaryResponse,
  ProductResponse,
  StockMovementResponse,
  TopProductResponse,
} from "../types/report";

const MOVEMENT_LABELS: Record<string, string> = {
  IN: "Entrada",
  OUT: "Salida",
  ADJUSTMENT: "Ajuste",
};

function formatCurrency(value: number) {
  return new Intl.NumberFormat("es-DO", {
    style: "currency",
    currency: "DOP",
    minimumFractionDigits: 2,
  }).format(value);
}

function formatDate(iso: string) {
  return new Intl.DateTimeFormat("es-DO", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(iso));
}

export default function Dashboard() {
  const [summary, setSummary] = useState<InventorySummaryResponse | null>(null);
  const [lowStock, setLowStock] = useState<ProductResponse[]>([]);
  const [movements, setMovements] = useState<StockMovementResponse[]>([]);
  const [topProducts, setTopProducts] = useState<TopProductResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const grafanaUrl =
    import.meta.env.VITE_GRAFANA_URL ?? "http://localhost:3001";

  const loadDashboard = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [summaryData, lowStockData, movementsData, topData] =
        await Promise.all([
          getInventorySummary(),
          getLowStockProducts(10),
          getRecentMovements(10),
          getTopProducts(10),
        ]);
      setSummary(summaryData);
      setLowStock(lowStockData);
      setMovements(movementsData);
      setTopProducts(topData);
    } catch (err) {
      setError(
        err instanceof ApiError
          ? err.message
          : "Error al cargar el dashboard",
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadDashboard();
  }, [loadDashboard]);

  return (
    <Layout>
      <div className="page page-left">
        <header className="page-header">
          <div>
            <h1>Dashboard</h1>
            <p>Resumen operacional del inventario</p>
          </div>
          <div className="actions">
            <button type="button" onClick={() => void loadDashboard()}>
              Actualizar
            </button>
          </div>
        </header>

        {error && (
          <div className="alert alert-error" role="alert">
            {error}
          </div>
        )}

        {loading ? (
          <p data-testid="dashboard-loading">Cargando dashboard...</p>
        ) : (
          <>
            <section
              className="dashboard-stats"
              aria-label="Indicadores principales"
              data-testid="dashboard-stats"
            >
              <article className="stat-card">
                <h2>Total productos</h2>
                <p className="stat-value">{summary?.totalProducts ?? 0}</p>
              </article>
              <article className="stat-card">
                <h2>Activos</h2>
                <p className="stat-value">{summary?.activeProducts ?? 0}</p>
              </article>
              <article className="stat-card stat-card-warning">
                <h2>Stock crítico</h2>
                <p className="stat-value">{summary?.lowStockProducts ?? 0}</p>
              </article>
              <article className="stat-card">
                <h2>Valor inventario</h2>
                <p className="stat-value">
                  {formatCurrency(summary?.inventoryValue ?? 0)}
                </p>
              </article>
            </section>

            <section className="card card-link">
              <h2>Métricas del sistema</h2>
              <p>
                Consulta indicadores operacionales de la API (JVM, HTTP, errores)
                en Grafana.
              </p>
              <a
                href={grafanaUrl}
                target="_blank"
                rel="noopener noreferrer"
                data-testid="grafana-link"
              >
                Abrir Grafana
              </a>
            </section>

            <div className="dashboard-grid">
              <section className="card">
                <h2>Productos críticos</h2>
                <p className="card-subtitle">Stock actual ≤ mínimo</p>
                {lowStock.length === 0 ? (
                  <p>Sin productos en stock crítico.</p>
                ) : (
                  <div className="table-wrap">
                    <table
                      className="data-table"
                      data-testid="low-stock-table"
                    >
                      <thead>
                        <tr>
                          <th>Producto</th>
                          <th>SKU</th>
                          <th>Stock</th>
                          <th>Mínimo</th>
                        </tr>
                      </thead>
                      <tbody>
                        {lowStock.map((product) => (
                          <tr key={product.id}>
                            <td>{product.name}</td>
                            <td>{product.sku}</td>
                            <td>{product.quantity}</td>
                            <td>{product.minStock}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </section>

              <section className="card">
                <h2>Movimientos recientes</h2>
                {movements.length === 0 ? (
                  <p>Sin movimientos registrados.</p>
                ) : (
                  <div className="table-wrap">
                    <table
                      className="data-table"
                      data-testid="recent-movements-table"
                    >
                      <thead>
                        <tr>
                          <th>Fecha</th>
                          <th>Producto</th>
                          <th>Tipo</th>
                          <th>Δ</th>
                          <th>Usuario</th>
                        </tr>
                      </thead>
                      <tbody>
                        {movements.map((movement) => (
                          <tr key={movement.id}>
                            <td>{formatDate(movement.createdAt)}</td>
                            <td>{movement.productName}</td>
                            <td>
                              {MOVEMENT_LABELS[movement.movementType] ??
                                movement.movementType}
                            </td>
                            <td>{movement.quantityDelta}</td>
                            <td>{movement.performedBy}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </section>

              <section className="card dashboard-span-full">
                <h2>Más vendidos</h2>
                <p className="card-subtitle">Top salidas de inventario</p>
                {topProducts.length === 0 ? (
                  <p>Sin salidas registradas aún.</p>
                ) : (
                  <div className="table-wrap">
                    <table
                      className="data-table"
                      data-testid="top-products-table"
                    >
                      <thead>
                        <tr>
                          <th>#</th>
                          <th>Producto</th>
                          <th>SKU</th>
                          <th>Unidades salidas</th>
                        </tr>
                      </thead>
                      <tbody>
                        {topProducts.map((product, index) => (
                          <tr key={product.productId}>
                            <td>{index + 1}</td>
                            <td>{product.productName}</td>
                            <td>{product.productSku}</td>
                            <td>{product.unitsOut}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </section>
            </div>
          </>
        )}
      </div>
    </Layout>
  );
}
