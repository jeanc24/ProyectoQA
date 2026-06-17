import { test, expect } from "@playwright/test";
import { loginAsAdmin } from "./auth.ts";

// Prueba para verificar que se puede crear un producto y que se muestra en la tabla
test("crear producto y verificar fila en tabla", async ({ page }) => {
  // espera el proceso de login como admin
  await loginAsAdmin(page);
  // SKU del producto
  const sku = `E2E-${Date.now()}`;
  // Nombre del producto
  const name = `Producto E2E ${sku}`;
  // Botón de creación de producto para abrir el formulario
  await page.getByTestId("create-product-button").click();

  // Rellenar el formulario de creación de producto
  await page.getByTestId("product-name").fill(name);
  await page.getByTestId("product-sku").fill(sku);
  await page.getByTestId("product-price").fill("19.99");
  await page.getByTestId("product-quantity").fill("5");
  await page.getByTestId("product-min-stock").fill("1");

  // Botón de creación de producto
  await page.getByTestId("product-submit").click();

  // Esperar a que se muestre la fila del producto en la tabla
  const row = page.getByRole("row", { name: new RegExp(name) });
  // Esperar a que se muestre la fila del producto en la tabla
  await expect(row).toBeVisible();
  // Esperar a que se muestre el SKU del producto en la fila
  await expect(row).toContainText(sku);
});