import { keycloak } from "../auth/keycloak";
import type { ErrorResponse } from "../types/product";

/**
 * Error HTTP tipado (status + message + fieldErrors de la API).
 */
export class ApiError extends Error {
  status: number;
  fieldErrors?: { field: string; message: string }[];

  constructor(
    status: number,
    message: string,
    fieldErrors?: { field: string; message: string }[],
  ) {
    super(message);
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

/**
 * fetch autenticado hacia la API.
 *
 * Bloques:
 * 1. refresh del JWT si hace falta (updateToken)
 * 2. header Authorization: Bearer …
 * 3. llamada a VITE_API_URL + path
 * 4. maneja 204 / errores → ApiError
 *
 * Todos los módulos frontend/src/api/*.ts pasan por aquí.
 */
export async function apiFetch<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  // Evita enviar un access token a punto de expirar
  if (keycloak.authenticated) {
    await keycloak.updateToken(30);
  }

  const headers: Record<string, string> = {
    ...(options.body ? { "Content-Type": "application/json" } : {}),
    ...(options.headers as Record<string, string>),
  };

  if (keycloak.token) {
    headers.Authorization = `Bearer ${keycloak.token}`;
  }

  const response = await fetch(
    `${import.meta.env.VITE_API_URL}${path}`,
    { ...options, headers },
  );

  if (response.status === 204) {
    return undefined as T;
  }

  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as ErrorResponse | null;
    throw new ApiError(
      response.status,
      body?.message ?? response.statusText,
      body?.fieldErrors,
    );
  }

  return response.json() as Promise<T>;
}
