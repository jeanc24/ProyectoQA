/// <reference types="node" />
import { defineConfig, devices } from "@playwright/test";

/**
 * Configuración E2E (Playwright).
 *
 * Requisitos previos:
 * - Frontend en PLAYWRIGHT_BASE_URL (por defecto http://localhost:3000)
 * - API / Keycloak levantados si los specs usan login o llamadas HTTP
 *
 * Staging: PLAYWRIGHT_BASE_URL=http://localhost:3008
 */
export default defineConfig({
  /** Carpeta de specs: frontend/e2e */
  testDir: "./e2e",

  /** Un worker a la vez: evita choques de sesión Keycloak entre roles */
  fullyParallel: false,

  /** Reintentos: 2 en CI, 1 en local */
  retries: process.env.CI ? 2 : 1,

  /** Tiempo máximo por test (60 s) */
  timeout: 60_000,

  /** Tiempo máximo por expect / assertion (10 s) */
  expect: { timeout: 10_000 },

  use: {
    /**
     * URL del frontend bajo prueba.
     * Override: PLAYWRIGHT_BASE_URL=http://localhost:3008
     */
    baseURL: process.env.PLAYWRIGHT_BASE_URL ?? "http://localhost:3000",

    /** Traza solo en el primer reintento (debug de flaky) */
    trace: "on-first-retry",

    /** Screenshot solo si el test falla */
    screenshot: "only-on-failure",

    /** Video solo si el test falla */
    video: "retain-on-failure",
  },

  /** Browser único: Chromium escritorio */
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});
