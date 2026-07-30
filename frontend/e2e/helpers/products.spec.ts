import { test, expect } from "@playwright/test";
import { loginAsAdmin } from "./auth.ts";

/**
 * Caso E2E #1: Crear producto
 * Verifica que un admin puede abrir el formulario, crear un producto
 * y ver la fila correspondiente (nombre + SKU) en la tabla.
 */
test("successfully create a product and verify the row in the table", async ({ page }) => {
  await loginAsAdmin(page);

  const sku = `E2E-${Date.now()}`;
  const name = `Producto E2E ${sku}`;

  await page.getByTestId("create-product-button").click();

  await page.getByTestId("product-name").fill(name);
  await page.getByTestId("product-sku").fill(sku);
  await page.getByTestId("product-price").fill("19.99");
  await page.getByTestId("product-quantity").fill("5");
  await page.getByTestId("product-min-stock").fill("1");

  await page.getByTestId("product-submit").click();

  const row = page.getByRole("row", { name: new RegExp(name) });
  await expect(row).toBeVisible();
  await expect(row).toContainText(sku);
});
