import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./live",
  timeout: 360000,
  outputDir: "../.verification/frontend-live/browser-results",
  expect: { timeout: 20000 },
  workers: 1,
  retries: 0,
  reporter: [["list"]],
  use: {
    actionTimeout: 20000,
    baseURL: "http://127.0.0.1:5173",
    channel: "msedge",
    trace: "off",
    screenshot: "off",
    video: "off",
    viewport: { width: 1440, height: 1000 },
  },
  webServer: {
    command: "npm run dev",
    url: "http://127.0.0.1:5173",
    reuseExistingServer: true,
  },
});
