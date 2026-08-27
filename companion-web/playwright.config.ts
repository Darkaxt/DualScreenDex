import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  outputDir: '../output/playwright-tests',
  reporter: 'line',
  use: {
    baseURL: 'http://127.0.0.1:4175',
    viewport: { width: 1024, height: 768 },
    hasTouch: true,
  },
  webServer: {
    command: 'npm run build && npm run preview -- --host 127.0.0.1 --port 4175 --strictPort',
    url: 'http://127.0.0.1:4175',
    reuseExistingServer: false,
  },
});
