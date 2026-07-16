import { apiFetch } from "./client";

export type ProductRevision = {
  productId: number;
  revision: number;
  revisionType: string;
  timestamp: string;
  username: string | null;
  name: string;
  sku: string;
  description: string | null;
  categoryId: number | null;
  price: number;
  quantity: number;
  minStock: number;
  active: boolean;
};

export function getProductAuditHistory(productId: number) {
  return apiFetch<ProductRevision[]>(`/api/v1/audit/products/${productId}`);
}
