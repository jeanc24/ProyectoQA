import { test, expect } from "@playwright/test";
import { loginAsAdmin } from "./auth.ts";

/**
 * Caso E2E #1: Login admin
 * Verifica que el administrador autentica vía Keycloak y llega a /products
 * con la tabla de productos visible.
 */
test("admin login redirects to /products", async ({ page }) => {
  await loginAsAdmin(page);
  await expect(page.getByTestId("products-table")).toBeVisible();
});
