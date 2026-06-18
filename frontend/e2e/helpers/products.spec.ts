import { test, expect } from "@playwright/test";
import { loginAsAdmin } from "./auth.ts";

// Test to verify that a product can be created and that the row is visible in the table
test("successfully create a product and verify the row in the table", async ({ page }) => {
  // Wait for the admin login process 
  await loginAsAdmin(page);

  const sku = `E2E-${Date.now()}`;
  const name = `Producto E2E ${sku}`;

  // Click the product creation button to open the form
  await page.getByTestId("create-product-button").click();

  // Fill the product creation form
  await page.getByTestId("product-name").fill(name);
  await page.getByTestId("product-sku").fill(sku);
  await page.getByTestId("product-price").fill("19.99");
  await page.getByTestId("product-quantity").fill("5");
  await page.getByTestId("product-min-stock").fill("1");

  // Product creation button
  await page.getByTestId("product-submit").click();

  // Wait for the product row to be visible in the table
  const row = page.getByRole("row", { name: new RegExp(name) });
  // Wait for the product row to be visible in the table
  await expect(row).toBeVisible();
  // Wait for the product SKU to be visible in the row
  await expect(row).toContainText(sku);

  // suelta ✓  1 [chromium] › e2e\helpers\products.spec.ts:32:1 › successfully create a product and verify the row in the table (5.2s)
});