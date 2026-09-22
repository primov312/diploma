import { defineConfig } from '@playwright/test';

/**
 * Browser smoke test against the running diploma stack (default http://localhost:8080).
 *   npx playwright test            # headless
 *   BASE_URL=http://localhost:5173 npx playwright test   # against the Vite dev server
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  retries: 0,
  use: {
    baseURL: process.env.BASE_URL ?? 'http://localhost:8080',
    viewport: { width: 1280, height: 800 },
    screenshot: 'only-on-failure',
  },
  reporter: [['list']],
  outputDir: 'e2e/.results',
});
