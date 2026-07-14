import { apiFetch } from "./client";

export type Category = {
  id: number;
  name: string;
  description: string | null;
};

export function listCategories() {
  return apiFetch<Category[]>("/api/v1/categories");
}
