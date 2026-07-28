import { apiFetch } from "./client";
import type { PageResponse } from "../types/product";
import type { StockMovement } from "../types/report";

export type MovementType = "IN" | "OUT" | "ADJUSTMENT";

export type StockMovementRequest = {
  productId: number;
  movementType: MovementType;
  quantity: number;
  notes?: string;
};

export type ListMovementsParams = {
  page?: number;
  size?: number;
  productId?: number | null;
  movementType?: MovementType | "";
  sort?: string;
};

function toQuery(params: ListMovementsParams): string {
  const q = new URLSearchParams();
  q.set("page", String(params.page ?? 0));
  q.set("size", String(params.size ?? 20));
  if (params.productId != null) q.set("productId", String(params.productId));
  if (params.movementType) q.set("movementType", params.movementType);
  if (params.sort?.trim()) q.set("sort", params.sort.trim());
  return q.toString();
}

export function listMovements(params: ListMovementsParams = {}) {
  return apiFetch<PageResponse<StockMovement>>(
    `/api/v1/stock/movements?${toQuery(params)}`,
  );
}

export function createMovement(data: StockMovementRequest) {
  return apiFetch<StockMovement>("/api/v1/stock/movements", {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export function getProductStockHistory(
  productId: number,
  page = 0,
  size = 20,
) {
  return apiFetch<PageResponse<StockMovement>>(
    `/api/v1/products/${productId}/stock/history?page=${page}&size=${size}`,
  );
}
