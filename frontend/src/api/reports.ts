import { apiFetch } from "./client";
import type { ProductResponse } from "../types/product";
import type {
  InventorySummary,
  StockMovement,
  TopProduct,
} from "../types/report";

export function getInventorySummary() {
  return apiFetch<InventorySummary>("/api/v1/reports/inventory-summary");
}

export function getLowStock(limit = 20) {
  return apiFetch<ProductResponse[]>(
    `/api/v1/reports/low-stock?limit=${limit}`,
  );
}

export function getRecentMovements(limit = 15) {
  return apiFetch<StockMovement[]>(
    `/api/v1/reports/recent-movements?limit=${limit}`,
  );
}

export function getTopProducts(limit = 10) {
  return apiFetch<TopProduct[]>(
    `/api/v1/reports/top-products?limit=${limit}`,
  );
}
