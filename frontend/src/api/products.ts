import { apiFetch } from "./client";
import type { PageResponse, ProductRequest, ProductResponse } from "../types/product";

export type ListProductsParams = {
  page?: number;
  size?: number;
  name?: string;
  sku?: string;
  categoryId?: number | null;
  active?: boolean | null;
  sort?: string; // e.g. "name,asc" | "price,desc" | "quantity,asc"
};

function toQuery(params: ListProductsParams): string {
  const q = new URLSearchParams();
  q.set("page", String(params.page ?? 0));
  q.set("size", String(params.size ?? 20));

  if (params.name?.trim()) q.set("name", params.name.trim());
  if (params.sku?.trim()) q.set("sku", params.sku.trim());
  if (params.categoryId != null) q.set("categoryId", String(params.categoryId));
  if (params.active != null) q.set("active", String(params.active));
  if (params.sort?.trim()) q.set("sort", params.sort.trim());

  return q.toString();
}

export function listProducts(params: ListProductsParams = {}) {
  return apiFetch<PageResponse<ProductResponse>>(
    `/api/v1/products?${toQuery(params)}`,
  );
}

export function createProduct(data: ProductRequest) {
  return apiFetch<ProductResponse>("/api/v1/products", {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export function updateProduct(id: number, data: ProductRequest) {
  return apiFetch<ProductResponse>(`/api/v1/products/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  });
}

export function deleteProduct(id: number) {
  return apiFetch<void>(`/api/v1/products/${id}`, {
    method: "DELETE",
  });
}
