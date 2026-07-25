/// <reference types="node" />
import { expect, type APIRequestContext, type Page } from "@playwright/test";

const KEYCLOAK_URL = process.env.KEYCLOAK_URL ?? "http://localhost:8081";
const KEYCLOAK_TOKEN_URL =
  process.env.KEYCLOAK_TOKEN_URL ??
  `${KEYCLOAK_URL}/realms/inventory/protocol/openid-connect/token`;
const API_BASE = process.env.API_BASE ?? "http://localhost:8080";

export type DemoUser = "admin" | "viewer" | "stock-manager";

/** Usuarios demo del realm Keycloak (password = username). */
export const DEMO_USERS: Record<DemoUser, { username: string; password: string }> = {
  admin: { username: "admin", password: "admin" },
  viewer: { username: "viewer", password: "viewer" },
  "stock-manager": { username: "stock-manager", password: "stock-manager" },
};

/**
 * Login por UI vía Keycloak (frontend en baseURL de Playwright).
 */
export async function loginAs(page: Page, user: DemoUser) {
  const { username, password } = DEMO_USERS[user];
  const kcHost = new URL(KEYCLOAK_URL).host;

  await page.goto("/login");
  await page.getByTestId("login-button").click();
  await page.waitForURL(new RegExp(kcHost.replace(/\./g, "\\.")));

  await page.locator("#username").fill(username);
  await page.locator("#password").fill(password);
  await page.locator("#kc-login").click();

  // Keycloak returns to /management, which redirects to /products
  await expect(page).toHaveURL(/\/products$/, { timeout: 30_000 });
  await expect(page.getByRole("heading", { name: "Productos" })).toBeVisible();
}

export async function loginAsAdmin(page: Page) {
  await loginAs(page, "admin");
}

/**
 * Obtiene un JWT con password grant (cliente inventory-api).
 * Útil para preparar datos o assert 200/403 en la API.
 */
export async function getAccessToken(
  request: APIRequestContext,
  user: DemoUser,
): Promise<string> {
  const { username, password } = DEMO_USERS[user];

  const response = await request.post(KEYCLOAK_TOKEN_URL, {
    form: {
      grant_type: "password",
      client_id: "inventory-api",
      client_secret: "inventory-api-secret",
      username,
      password,
    },
  });

  expect(response.ok(), `Token Keycloak para ${username}`).toBeTruthy();
  const body = (await response.json()) as { access_token: string };
  expect(body.access_token).toBeTruthy();
  return body.access_token;
}

/** Crea un producto vía API (requiere product:manage, p. ej. admin). */
export async function createProductViaApi(
  request: APIRequestContext,
  token: string,
  overrides: Partial<{
    name: string;
    sku: string;
    price: number;
    quantity: number;
    minStock: number;
  }> = {},
): Promise<{ id: number; name: string; sku: string }> {
  const sku = overrides.sku ?? `E2E-API-${Date.now()}`;
  const name = overrides.name ?? `Producto API ${sku}`;

  const response = await request.post(`${API_BASE}/api/v1/products`, {
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    data: {
      name,
      sku,
      price: overrides.price ?? 12.5,
      quantity: overrides.quantity ?? 10,
      minStock: overrides.minStock ?? 1,
      active: true,
    },
  });

  expect(response.ok(), `POST /products → ${response.status()}`).toBeTruthy();
  const body = (await response.json()) as { id: number; name: string; sku: string };
  return { id: body.id, name: body.name, sku: body.sku };
}
