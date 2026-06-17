import { test, expect } from "@playwright/test";
import { loginAsAdmin } from "./auth.ts";

// Prueba para verificar que el login como admin llega a la página de productos
test("admin login llega a /products", async ({ page }) => {
  await loginAsAdmin(page);
  // Esperar a que se muestre la tabla de productos
  await expect(page.getByTestId("products-table")).toBeVisible();
});