/// <reference types="node" />
import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  // Directorio de pruebas
  testDir: "./e2e",
  // Modo de ejecución
  fullyParallel: false,
  // Retries en caso de fallo
  retries: process.env.CI ? 2 : 1,
  // Timeout de las pruebas
  timeout: 60_000,
  // Timeout de las expectativas
  expect: { timeout: 10_000 },
  use: {
    // URL de la aplicación
    baseURL: "http://localhost:3000",
    // Traza de las pruebas
    trace: "on-first-retry",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});