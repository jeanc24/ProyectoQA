import { test, expect } from "@playwright/test";
import {
  createProductViaApi,
  getAccessToken,
  loginAs,
} from "./helpers/auth";

test.describe("Stock movements (stock-manager)", () => {
  test("stock-manager can register an IN movement", async ({ page, request }) => {
    // Producto previo vía API (stock-manager no tiene product:manage)
    const adminToken = await getAccessToken(request, "admin");
    const product = await createProductViaApi(request, adminToken, {
      name: `Stock E2E ${Date.now()}`,
      quantity: 5,
      minStock: 1,
    });

    await loginAs(page, "stock-manager");

    await page.getByTestId("nav-stock").click();
    await expect(page).toHaveURL(/\/stock$/);
    await expect(page.getByTestId("stock-form")).toBeVisible();

    await page.getByTestId("stock-product").selectOption(String(product.id));
    await page.getByTestId("stock-type").selectOption("IN");
    await page.getByTestId("stock-quantity").fill("3");
    await page.getByTestId("stock-notes").fill("E2E entrada stock-manager");
    await page.getByTestId("stock-submit").click();

    await expect(page.getByTestId("stock-success")).toBeVisible();
    await expect(page.getByTestId("stock-success")).toContainText(/Entrada registrada/i);
    await expect(page.getByTestId("movements-table")).toContainText(product.name);
  });
});
