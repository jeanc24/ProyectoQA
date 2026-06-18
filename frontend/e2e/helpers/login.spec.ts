import { test, expect } from "@playwright/test";
import { loginAsAdmin } from "./auth.ts";

// Test to verify that the admin login arrives at the products page
test("admin login redirects to /products", async ({ page }) => {
  // Login as admin
  await loginAsAdmin(page);
  // Wait for the products table to be visible
  await expect(page.getByTestId("products-table")).toBeVisible();


  // suelta ✓  1 [chromium] › e2e\helpers\login.spec.ts:5:1 › admin login redirects to /products (5.2s)
});