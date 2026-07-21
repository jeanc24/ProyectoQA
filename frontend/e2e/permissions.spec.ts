import { test, expect } from "@playwright/test";
import {
  createProductViaApi,
  getAccessToken,
  loginAs,
} from "./helpers/auth";

const API_BASE = "http://localhost:8080";

test.describe("Permissions by role", () => {
  test("viewer cannot manage products and is blocked from dashboard", async ({
    page,
  }) => {
    await loginAs(page, "viewer");

    // Solo lectura en productos: sin botón de crear
    await expect(page.getByTestId("create-product-button")).toHaveCount(0);

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

  test("auditor can read product audit history (audit:view)", async ({
    request,
  }) => {
    const adminToken = await getAccessToken(request, "admin");
    const product = await createProductViaApi(request, adminToken, {
      name: `Audit target ${Date.now()}`,
    });

    const auditorToken = await getAccessToken(request, "auditor");
    const auditRes = await request.get(
      `${API_BASE}/api/v1/audit/products/${product.id}`,
      { headers: { Authorization: `Bearer ${auditorToken}` } },
    );

    expect(auditRes.status()).toBe(200);
    const revisions = (await auditRes.json()) as unknown[];
    expect(revisions.length).toBeGreaterThanOrEqual(1);
  });

  test("auditor UI: can view products/stock, no create product, no dashboard", async ({
    page,
  }) => {
    await loginAs(page, "auditor");

    await expect(page.getByTestId("nav-products")).toBeVisible();
    await expect(page.getByTestId("nav-stock")).toBeVisible();
    await expect(page.getByTestId("nav-dashboard")).toHaveCount(0);
    await expect(page.getByTestId("create-product-button")).toHaveCount(0);

    await page.goto("/dashboard");
    await expect(page).toHaveURL(/\/unauthorized$/);
  });
});
