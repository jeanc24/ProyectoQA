import { apiFetch } from "./client";
import type {
  InventorySummaryResponse,
  ProductResponse,
  StockMovementResponse,
  TopProductResponse,
} from "../types/report";

export function getInventorySummary() {
  return apiFetch<InventorySummaryResponse>("/api/v1/reports/inventory-summary");
}

export function getLowStockProducts(limit = 20) {
  return apiFetch<ProductResponse[]>(
    `/api/v1/reports/low-stock?limit=${limit}`,
  );
}

export function getRecentMovements(limit = 10) {
  return apiFetch<StockMovementResponse[]>(
    `/api/v1/reports/recent-movements?limit=${limit}`,
  );
}

export function getTopProducts(limit = 10) {
  return apiFetch<TopProductResponse[]>(
    `/api/v1/reports/top-products?limit=${limit}`,
  );
}
