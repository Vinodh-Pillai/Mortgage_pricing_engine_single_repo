import { defineConfig, devices } from '@playwright/test';
import path from 'path';

export default defineConfig({
  testDir: './tests/e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 2 : 4,
  reporter: [
    ['html', { outputFolder: 'tests/results/html-report', open: 'never' }],
    ['json', { outputFile: 'tests/results/test-results.json' }],
    ['junit', { outputFile: 'tests/results/junit-results.xml' }],
    ['line'],
  ],
  timeout: 120000,
  expect: {
    timeout: 10000,
    toHaveScreenshot: {
      maxDiffPixels: 100,
      threshold: 0.2,
    },
  },
  outputDir: 'tests/results/artifacts',
  globalSetup: require.resolve('./tests/e2e/core/helpers/global-setup.ts'),
  globalTeardown: require.resolve('./tests/e2e/core/helpers/global-teardown.ts'),
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 1920, height: 1080 },
        baseURL: process.env.BASE_URL || 'http://localhost:3000',
        trace: 'on-first-retry',
        screenshot: 'only-on-failure',
        video: 'retain-on-failure',
        actionTimeout: 30000,
        navigationTimeout: 60000,
      },
    },
    {
      name: 'firefox',
      use: {
        ...devices['Desktop Firefox'],
        viewport: { width: 1920, height: 1080 },
        baseURL: process.env.BASE_URL || 'http://localhost:3000',
      },
    },
    {
      name: 'webkit',
      use: {
        ...devices['Desktop Safari'],
        viewport: { width: 1920, height: 1080 },
        baseURL: process.env.BASE_URL || 'http://localhost:3000',
      },
    },
    {
      name: 'mobile-chrome',
      use: {
        ...devices['Pixel 5'],
        baseURL: process.env.BASE_URL || 'http://localhost:3000',
      },
    },
    {
      name: 'mobile-safari',
      use: {
        ...devices['iPhone 12'],
        baseURL: process.env.BASE_URL || 'http://localhost:3000',
      },
    },
    {
      name: 'tablet',
      use: {
        ...devices['iPad Pro'],
        baseURL: process.env.BASE_URL || 'http://localhost:3000',
      },
    },
    {
      name: 'demo-headed',
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 1920, height: 1080 },
        baseURL: process.env.BASE_URL || 'http://localhost:3000',
        headless: false,
        slowMo: 500,
        trace: 'on',
        video: 'on',
      },
    },
  ],
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:3000',
    reuseExistingServer: !process.env.CI,
    timeout: 120000,
    env: {
      VITE_API_BASE: process.env.VITE_API_BASE || 'http://localhost:8080',
    },
  },
  snapshotDir: './tests/baselines',
  snapshotPathTemplate: '{snapshotDir}/{projectName}/{testFileDir}/{arg}{ext}',
});