/// <reference types="node" />
import { test, expect } from "@playwright/test";
import {
  createProductViaApi,
  getAccessToken,
  loginAs,
} from "./helpers/auth";

const API_BASE = process.env.API_BASE ?? "http://localhost:8080";

test.describe("Permissions by role", () => {
  test("viewer cannot manage products and is blocked from dashboard", async ({
    page,
  }) => {
    await loginAs(page, "viewer");

    // Solo lectura en productos: sin crear ni historial de auditoría
    await expect(page.getByTestId("create-product-button")).toHaveCount(0);
    await expect(page.getByTestId("audit-history-button")).toHaveCount(0);

    // Dashboard requiere report:view → Acceso denegado
    await page.goto("/dashboard");
    await expect(page).toHaveURL(/\/unauthorized$/);
    await expect(page.getByRole("heading", { name: "Acceso denegado" })).toBeVisible();

    // Stock visible pero solo lectura (sin stock:manage)
    await page.goto("/stock");
    await expect(page.getByTestId("stock-readonly")).toBeVisible();
    await expect(page.getByTestId("stock-form")).toHaveCount(0);
  });

  test("viewer gets 403 on product:manage and audit:view APIs", async ({
    request,
  }) => {
    const viewerToken = await getAccessToken(request, "viewer");

    const createRes = await request.post(`${API_BASE}/api/v1/products`, {
      headers: {
        Authorization: `Bearer ${viewerToken}`,
        "Content-Type": "application/json",
      },
      data: {
        name: "Viewer forbidden",
        sku: `VIEWER-403-${Date.now()}`,
        price: 1,
        quantity: 1,
        minStock: 0,
        active: true,
      },
    });
    expect(createRes.status()).toBe(403);

    const adminToken = await getAccessToken(request, "admin");
    const product = await createProductViaApi(request, adminToken);

    const auditRes = await request.get(
      `${API_BASE}/api/v1/audit/products/${product.id}`,
      { headers: { Authorization: `Bearer ${viewerToken}` } },
    );
    expect(auditRes.status()).toBe(403);
  });

  test("admin can read product audit history (audit:view)", async ({
    request,
  }) => {
    const adminToken = await getAccessToken(request, "admin");
    const product = await createProductViaApi(request, adminToken, {
      name: `Audit target ${Date.now()}`,
    });

    const auditRes = await request.get(
      `${API_BASE}/api/v1/audit/products/${product.id}`,
      { headers: { Authorization: `Bearer ${adminToken}` } },
    );

    expect(auditRes.status()).toBe(200);
    const revisions = (await auditRes.json()) as unknown[];
    expect(revisions.length).toBeGreaterThanOrEqual(1);
  });

  test("admin UI shows Historial; viewer does not", async ({ page, request }) => {
    const adminToken = await getAccessToken(request, "admin");
    const product = await createProductViaApi(request, adminToken, {
      name: `Audit UI ${Date.now()}`,
    });

    await loginAs(page, "admin");
    await page.getByTestId("products-search").fill(product.name);
    await page.getByTestId("products-search-button").click();
    await expect(page.getByTestId("audit-history-button").first()).toBeVisible();
    await page.getByTestId("audit-history-button").first().click();
    await expect(page.getByTestId("product-audit-panel")).toBeVisible();
    await expect(page.getByTestId("audit-history-table")).toBeVisible();
  });

  test("admin can open users directory; viewer cannot", async ({ page }) => {
    await loginAs(page, "admin");
    await expect(page.getByTestId("nav-users")).toBeVisible();
    await page.getByTestId("nav-users").click();
    await expect(page).toHaveURL(/\/users$/);
    await expect(page.getByTestId("users-keycloak-note")).toBeVisible();
    await expect(page.getByTestId("users-table")).toBeVisible();
    await expect(page.getByText("admin", { exact: true }).first()).toBeVisible();

    await loginAs(page, "viewer");
    await expect(page.getByTestId("nav-users")).toHaveCount(0);
    await page.goto("/users");
    await expect(page).toHaveURL(/\/unauthorized$/);
  });

  test("auditor sees Historial; cannot open dashboard", async ({ page, request }) => {
    const adminToken = await getAccessToken(request, "admin");
    const product = await createProductViaApi(request, adminToken, {
      name: `Auditor UI ${Date.now()}`,
    });

    await loginAs(page, "auditor");
    await expect(page.getByTestId("create-product-button")).toHaveCount(0);
    await expect(page.getByTestId("nav-dashboard")).toHaveCount(0);
    await expect(page.getByTestId("nav-users")).toHaveCount(0);

    await page.getByTestId("products-search").fill(product.name);
    await page.getByTestId("products-search-button").click();
    await expect(page.getByTestId("audit-history-button").first()).toBeVisible();

    await page.goto("/dashboard");
    await expect(page).toHaveURL(/\/unauthorized$/);
  });

  test("viewer gets 403 on GET /api/v1/users", async ({ request }) => {
    const viewerToken = await getAccessToken(request, "viewer");
    const res = await request.get(`${API_BASE}/api/v1/users`, {
      headers: { Authorization: `Bearer ${viewerToken}` },
    });
    expect(res.status()).toBe(403);

    const adminToken = await getAccessToken(request, "admin");
    const adminRes = await request.get(`${API_BASE}/api/v1/users`, {
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    expect(adminRes.status()).toBe(200);
    const users = (await adminRes.json()) as { username: string }[];
    expect(users.some((u) => u.username === "admin")).toBe(true);
  });
});
