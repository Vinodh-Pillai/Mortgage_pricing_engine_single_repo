import { defineConfig, devices } from '@playwright/test';

const baseURL = process.env.BASE_URL || 'http://localhost:3000';

export default defineConfig({
  testDir: './tests/e2e',
  testIgnore: ['**/governance/**', '**/operations/**', '**/pricing-engine/**'],
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 4 : 4,
  reporter: [
    ['html', { outputFolder: 'tests/results/html-report', open: 'never' }],
    ['json', { outputFile: 'tests/results/test-results.json' }],
    ['junit', { outputFile: 'tests/results/junit-results.xml' }],
    ['line'],
  ],
  timeout: 120_000,
  expect: {
    timeout: 10_000,
    toHaveScreenshot: {
      threshold: 0.001,
      maxDiffPixelRatio: 0.001,
      animations: 'disabled',
    },
  },
  outputDir: 'tests/results/artifacts',
  globalSetup: './tests/e2e/core/global-setup.ts',
  globalTeardown: './tests/e2e/core/global-teardown.ts',
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
    {
      name: 'firefox',
      use: { ...devices['Desktop Firefox'], viewport: { width: 1440, height: 900 } },
    },
    {
      name: 'webkit',
      use: { ...devices['Desktop Safari'], viewport: { width: 1440, height: 900 } },
    },
    {
      name: 'mobile-chrome',
      use: { ...devices['Pixel 5'] },
    },
    {
      name: 'mobile-safari',
      use: { ...devices['iPhone 12'] },
    },
    {
      name: 'tablet',
      use: { ...devices['iPad Pro'] },
    },
    {
      name: 'demo-headed',
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 1920, height: 1080 },
        headless: false,
        slowMo: 500,
        trace: 'on',
        video: 'on',
      },
    },
  ],
  webServer: {
    command: 'npm run dev -- --host 127.0.0.1 --port 3000',
    url: baseURL,
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
    env: {
      VITE_API_BASE: process.env.VITE_API_BASE || 'http://127.0.0.1:18080',
      VITE_BFF_API_BASE_URL: process.env.VITE_BFF_API_BASE_URL || process.env.VITE_API_BASE || 'http://127.0.0.1:18080',
    },
  },
  snapshotDir: './tests/baselines',
  snapshotPathTemplate: '{snapshotDir}/{projectName}/{testFileDir}/{arg}{ext}',
});
