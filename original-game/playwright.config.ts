import "dotenv/config";
import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests/e2e",
  fullyParallel: false,
  workers: 1,
  timeout: 45000,
  expect: { timeout: 10000 },
  reporter: "list",
  outputDir: "/tmp/abyss-browser-results",
  use: { baseURL: "http://127.0.0.1:3000", headless: true, trace: "retain-on-failure", launchOptions: { args: ["--no-sandbox", "--disable-dev-shm-usage"] } },
});
