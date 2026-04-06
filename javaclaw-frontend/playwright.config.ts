import { defineConfig, devices } from "@playwright/test";

/**
 * Playwright E2E configuration for JavaClaw frontend.
 *
 * By default tests run against a dev-server proxy at http://localhost:5173 (Vite).
 * For packaged-app testing, set BASE_URL=http://localhost:8080.
 *
 * Prerequisites:
 *   1. Backend must be running (either via `java -jar javaclaw-app/target/*.jar` or dev profile)
 *   2. Frontend dev server: `pnpm dev` (started automatically via webServer config below)
 *
 * Usage:
 *   pnpm e2e          # headless run
 *   pnpm e2e:ui       # interactive Playwright UI
 */
export default defineConfig({
  testDir: "./e2e",
  testMatch: "**/*.e2e.ts",
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: process.env.CI ? "github" : "html",
  timeout: 30_000,

  use: {
    baseURL: process.env.BASE_URL ?? "http://localhost:5173",
    trace: "on-first-retry",
    screenshot: "only-on-failure",
  },

  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],

  /* Start Vite dev server before tests if not already running */
  webServer: process.env.BASE_URL
    ? undefined
    : {
        command: "pnpm dev",
        url: "http://localhost:5173",
        reuseExistingServer: !process.env.CI,
        timeout: 30_000,
      },
});
