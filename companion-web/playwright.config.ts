import { defineConfig } from '@playwright/test';
import { existsSync } from 'node:fs';
import { join } from 'node:path';

const localBrowser = join(process.env.LOCALAPPDATA ?? '', 'imput', 'Helium', 'Application', 'chrome.exe');

export default defineConfig({
  testDir: './e2e',
  outputDir: '../output/playwright-tests',
  reporter: 'line',
  use: {
    baseURL: 'http://127.0.0.1:4175',
    viewport: { width: 1024, height: 768 },
    hasTouch: true,
    launchOptions: existsSync(localBrowser) ? { executablePath: localBrowser } : {},
  },
  webServer: {
    command: 'npm.cmd run build && npm.cmd run preview -- --host 127.0.0.1 --port 4175 --strictPort',
    url: 'http://127.0.0.1:4175',
    reuseExistingServer: false,
  },
});
