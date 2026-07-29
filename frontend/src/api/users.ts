import { apiFetch } from "./client";
import type { UserResponse } from "../types/user";

export function listUsers() {
  return apiFetch<UserResponse[]>("/api/v1/users");
}
