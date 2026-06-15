import { apiFetch } from "./client";
import type { PageResponse, ProductRequest, ProductResponse } from "../types/product";

export function listProducts(page = 0, size = 20) {
  return apiFetch<PageResponse<ProductResponse>>(
    `/api/v1/products?page=${page}&size=${size}`,
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