import { keycloak } from "../auth/keycloak";
import type { ErrorResponse } from "../types/product";


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

// Injects the jwt token into the all the api calls if the user is authenticated to avoid expired tokens
// this is defined in the keycloak.ts file using the keycloak-js library
export async function apiFetch<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {

  // Update the token if the user is authenticated to avoid expired tokens
  if (keycloak.authenticated) {
    await keycloak.updateToken(30);
  }

  // Set the headers for the api call
  const headers: Record<string, string> = {
    ...(options.body ? { "Content-Type": "application/json" } : {}),
    ...(options.headers as Record<string, string>),
  };

  // Set the authorization header for the api call
  if (keycloak.token) {
    headers.Authorization = `Bearer ${keycloak.token}`;
  }

  // Make the api call
  const response = await fetch(
    `${import.meta.env.VITE_API_URL}${path}`,
    { ...options, headers },
  );

  // If the response is 204, return undefined
  if (response.status === 204) {
    return undefined as T;
  }

  // If the response is not ok, throw an error
  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as ErrorResponse | null;
    // Create a new ApiError with the response status, message and field errors
    throw new ApiError(
      response.status,
      body?.message ?? response.statusText,
      body?.fieldErrors,
    );
  }

  // Return the response as a promise
  return response.json() as Promise<T>;
}