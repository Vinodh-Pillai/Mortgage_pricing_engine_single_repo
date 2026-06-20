import { defineConfig, devices } from '@playwright/test';

const baseURL = 'http://127.0.0.1:3001';

export default defineConfig({
  testDir: './tests/e2e',
  testIgnore: ['**/governance/**', '**/operations/**', '**/pricing-engine/**'],
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1,
  reporter: [
    ['html', { outputFolder: 'tests/results/html-report', open: 'never' }],
    ['json', { outputFile: 'tests/results/test-results.json' }],
    ['line'],
  ],
  timeout: 120_000,
  expect: {
    timeout: 10_000,
  },
  use: {
    baseURL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    actionTimeout: 30_000,
    navigationTimeout: 60_000,
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } },
    },
  ],
});