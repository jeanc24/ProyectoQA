/// <reference types="node" />
import { expect, type APIRequestContext, type Page } from "@playwright/test";

/** Keycloak / API usados por los helpers E2E (override por env en CI). */
const KEYCLOAK_URL = process.env.KEYCLOAK_URL ?? "http://localhost:8081";
const KEYCLOAK_REALM = process.env.KEYCLOAK_REALM ?? "inventory";
const KEYCLOAK_TOKEN_URL =
  process.env.KEYCLOAK_TOKEN_URL ??
  `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token`;
const API_BASE = process.env.API_BASE ?? "http://localhost:8080";
const KEYCLOAK_CLIENT_ID = process.env.KEYCLOAK_CLIENT_ID ?? "inventory-api";
/** Fallback demo — debe coincidir con keycloak/inventory-realm.json. Preferir env en CI. */
const KEYCLOAK_CLIENT_SECRET =
  process.env.KEYCLOAK_CLIENT_SECRET ?? "inventory-api-secret";

export type DemoUser = "admin" | "viewer" | "stock-manager" | "auditor";

/** Usuarios demo del realm Keycloak (password = username salvo override por env). */
export const DEMO_USERS: Record<DemoUser, { username: string; password: string }> = {
  admin: {
    username: process.env.E2E_ADMIN_USER ?? "admin",
    password: process.env.E2E_ADMIN_PASSWORD ?? "admin",
  },
  viewer: {
    username: process.env.E2E_VIEWER_USER ?? "viewer",
    password: process.env.E2E_VIEWER_PASSWORD ?? "viewer",
  },
  "stock-manager": {
    username: process.env.E2E_STOCK_MANAGER_USER ?? "stock-manager",
    password: process.env.E2E_STOCK_MANAGER_PASSWORD ?? "stock-manager",
  },
  auditor: {
    username: process.env.E2E_AUDITOR_USER ?? "auditor",
    password: process.env.E2E_AUDITOR_PASSWORD ?? "auditor",
  },
};

/**
 * Limpia cookies/storage para evitar que check-sso reautentique al cambiar de usuario.
 */
export async function clearAuthSession(page: Page) {
  await page.context().clearCookies();
  await page.goto("/login", { waitUntil: "domcontentloaded" });
  await page.evaluate(() => {
    localStorage.clear();
    sessionStorage.clear();
  });
  // Segunda pasada: init de Keycloak sin cookies SSO ni tokens en storage.
  await page.context().clearCookies();
  await page.goto("/login", { waitUntil: "domcontentloaded" });
}

/**
 * Login por UI vía Keycloak (frontend en baseURL de Playwright).
 * Deja la sesión en /products tras el redirect de /management.
 */
export async function loginAs(page: Page, user: DemoUser) {
  const { username, password } = DEMO_USERS[user];
  const kcHost = new URL(KEYCLOAK_URL).host;

  await clearAuthSession(page);
  await expect(page.getByTestId("login-button")).toBeVisible({ timeout: 30_000 });
  await page.getByTestId("login-button").click();
  await page.waitForURL(new RegExp(kcHost.replace(/\./g, "\\.")));

  await page.locator("#username").fill(username);
  await page.locator("#password").fill(password);
  await page.locator("#kc-login").click();

  await expect(page).toHaveURL(/\/products$/, { timeout: 30_000 });
  await expect(page.getByRole("heading", { name: "Productos" })).toBeVisible();
}

/** Atajo: loginAs(page, "admin"). */
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
      client_id: KEYCLOAK_CLIENT_ID,
      client_secret: KEYCLOAK_CLIENT_SECRET,
      username,
      password,
    },
  });

  expect(response.ok(), `Token Keycloak para ${username}`).toBeTruthy();
  const body = (await response.json()) as { access_token: string };
  expect(body.access_token).toBeTruthy();
  return body.access_token;
}

/**
 * Crea un producto vía API (requiere product:manage, p. ej. admin).
 * Usado para sembrar datos cuando el rol bajo prueba no puede crear productos.
 */
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
