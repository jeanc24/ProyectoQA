import { Page, expect } from "@playwright/test";
// Sirve para hacer login como admin en la aplicación
export async function loginAsAdmin(page: Page) {
  await page.goto("/login");
  await page.getByTestId("login-button").click();

  // Esperar a que se redirija a la pantalla de Keycloak
  await page.waitForURL(/localhost:8081/);

  // Rellenar el formulario de login de Keycloak
  await page.locator("#username").fill("admin");
  await page.locator("#password").fill("admin");
  await page.locator("#kc-login").click();

  // Esperar a que se redirija a la página de productos
  await expect(page).toHaveURL(/\/products$/);
  
  // Esperar a que se muestre el heading de productos
  await expect(page.getByRole("heading", { name: "Productos" })).toBeVisible();
}