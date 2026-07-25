/// <reference types="node" />
import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  // Test directory
  testDir: "./e2e",
  fullyParallel: false,
  // Retries in case of failure
  retries: process.env.CI ? 2 : 1,
  // Test timeout
  timeout: 60_000,
  // Expectation timeout
  expect: { timeout: 10_000 },
  use: {
    // Application URL (staging: PLAYWRIGHT_BASE_URL=http://localhost:3008)
    baseURL: process.env.PLAYWRIGHT_BASE_URL ?? "http://localhost:3000",
    // Test trace
    trace: "on-first-retry",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});
