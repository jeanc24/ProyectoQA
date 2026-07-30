import { test, expect } from "@playwright/test";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { loginAs } from "./helpers/auth";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const EVIDENCE_DIR = path.resolve(
  __dirname,
  "../../docs/final/testing/e2e/evidencias",
);

test.describe("Dashboard (report:view)", () => {
  /**
   * Caso E2E #1: Dashboard con report:view
   * Verifica que un admin ve KPIs (total, activos, stock bajo) y los paneles
   * de stock bajo y movimientos recientes.
   */
  test("admin with report:view sees dashboard KPIs", async ({ page }) => {
    await loginAs(page, "admin");

    await page.getByTestId("nav-dashboard").click();
    await expect(page).toHaveURL(/\/dashboard$/);

    await expect(page.getByTestId("dashboard-kpis")).toBeVisible();
    await expect(page.getByTestId("kpi-total")).toBeVisible();
    await expect(page.getByTestId("kpi-active")).toBeVisible();
    await expect(page.getByTestId("kpi-low-stock")).toBeVisible();
    await expect(page.getByTestId("panel-low-stock")).toBeVisible();
    await expect(page.getByTestId("panel-movements")).toBeVisible();
  });

  /**
   * Caso E2E #2: Evidencia responsive (móvil)
   * Verifica la página de productos en viewport 375×812 y guarda screenshot
   * en docs/final/testing/e2e/evidencias.
   */
  test("mobile viewport — products page screenshot for evidence", async ({
    page,
  }) => {
    await page.setViewportSize({ width: 375, height: 812 });
    await loginAs(page, "admin");

    await expect(page.getByTestId("products-table")).toBeVisible();
    await expect(page.getByTestId("app-nav")).toBeVisible();

    await page.screenshot({
      path: path.join(EVIDENCE_DIR, "responsive-products-mobile.png"),
      fullPage: true,
    });
  });
});
