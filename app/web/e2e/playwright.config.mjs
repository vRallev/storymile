import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  forbidOnly: Boolean(process.env.CI),
  workers: 1,
  retries: 0,
  timeout: 60_000,
  expect: {
    timeout: 30_000,
    toHaveScreenshot: { maxDiffPixels: 50 },
  },
  snapshotPathTemplate: '{testDir}/snapshots/{arg}-{platform}{ext}',
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    browserName: 'chromium',
    baseURL: 'http://127.0.0.1:4173',
    viewport: { width: 1280, height: 720 },
    deviceScaleFactor: 1,
    colorScheme: 'light',
    locale: 'en-US',
    timezoneId: 'UTC',
    reducedMotion: 'reduce',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  webServer: {
    command: 'python3 -m http.server 4173 --bind 127.0.0.1 --directory ../build/dist/wasmJs/productionExecutable',
    url: 'http://127.0.0.1:4173',
    timeout: 15_000,
    reuseExistingServer: false,
  },
});
