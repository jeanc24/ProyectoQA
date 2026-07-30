/// <reference types="node" />
import { test, expect } from "@playwright/test";
import {
  createProductViaApi,
  getAccessToken,
  loginAs,
} from "./helpers/auth";

const API_BASE = process.env.API_BASE ?? "http://localhost:8080";

test.describe("Permissions by role", () => {
  /**
   * Caso E2E #1: Viewer — UI solo lectura y dashboard bloqueado
   * Verifica que viewer no ve crear/auditoría, al ir a /dashboard cae en
   * /unauthorized, y en stock solo ve modo lectura (sin formulario).
   */
  test("viewer cannot manage products and is blocked from dashboard", async ({
    page,
  }) => {
    await loginAs(page, "viewer");

    await expect(page.getByTestId("create-product-button")).toHaveCount(0);
    await expect(page.getByTestId("audit-history-button")).toHaveCount(0);

    await page.goto("/dashboard");
    await expect(page).toHaveURL(/\/unauthorized$/);
    await expect(page.getByRole("heading", { name: "Acceso denegado" })).toBeVisible();

    await page.goto("/stock");
    await expect(page.getByTestId("stock-readonly")).toBeVisible();
    await expect(page.getByTestId("stock-form")).toHaveCount(0);
  });

  /**
   * Caso E2E #2: Viewer — 403 en product:manage y audit:view (API)
   * Verifica que el token viewer recibe 403 al crear productos y al consultar
   * historial de auditoría de un producto existente.
   */
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

  /**
   * Caso E2E #3: Admin — historial de auditoría por API
   * Verifica que con audit:view el admin obtiene 200 y al menos una revisión
   * tras crear un producto.
   */
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

  /**
   * Caso E2E #4: Admin — panel Historial en UI
   * Verifica que el admin ve el botón Historial, abre el panel y la tabla
   * de revisiones; el producto de prueba se crea vía API.
   */
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

  /**
   * Caso E2E #5: Admin — directorio de usuarios
   * Verifica que el admin ve nav Usuarios, entra a /users y ve la tabla
   * con el usuario demo admin.
   */
  test("admin can open users directory", async ({ page }) => {
    await loginAs(page, "admin");
    await expect(page.getByTestId("nav-users")).toBeVisible();
    await page.getByTestId("nav-users").click();
    await expect(page).toHaveURL(/\/users$/);
    await expect(page.getByTestId("users-keycloak-note")).toBeVisible();
    await expect(page.getByTestId("users-table")).toBeVisible();
    await expect(page.getByText("admin", { exact: true }).first()).toBeVisible();
  });

  /**
   * Caso E2E #6: Viewer — sin acceso a usuarios
   * Verifica que viewer no tiene nav Usuarios y al forzar /users termina en
   * /unauthorized.
   */
  test("viewer cannot open users directory", async ({ page }) => {
    await loginAs(page, "viewer");
    await expect(page.getByTestId("nav-users")).toHaveCount(0);
    await page.goto("/users");
    await expect(page).toHaveURL(/\/unauthorized$/);
  });

  /**
   * Caso E2E #7: Auditor — Historial sí, dashboard no
   * Verifica que auditor ve Historial (sin crear producto ni nav dashboard/users)
   * y al ir a /dashboard cae en /unauthorized.
   */
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

  /**
   * Caso E2E #8: Viewer — 403 en GET /users; admin 200
   * Verifica que viewer no lista usuarios por API y que admin sí obtiene
   * la lista con el usuario admin.
   */
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
