import { FullConfig } from '@playwright/test';
import { chromium } from 'playwright';
import fs from 'fs';
import path from 'path';

async function globalSetup(config: FullConfig) {
  const baseURL = config.projects[0].use?.baseURL || 'http://localhost:3000';
  const apiBase = process.env.VITE_API_BASE || 'http://localhost:8080';

  console.log('[Global Setup] Starting E2E test environment setup...');

  // Wait for UI to be ready
  await waitForService(baseURL, 'UI');
  
  // Wait for API to be ready
  await waitForService(`${apiBase}/actuator/health`, 'API');

  // Create test tenant and users if needed
  await setupTestData(apiBase);

  // Capture baseline if requested
  if (process.env.CAPTURE_BASELINE === 'true') {
    await captureBaselines(baseURL);
  }

  // Verify drift detection is configured
  if (process.env.DRIFT_DETECTION === 'true') {
    console.log('[Global Setup] Drift detection enabled');
  }

  console.log('[Global Setup] Setup complete');
}

async function waitForService(url: string, name: string, maxAttempts = 30): Promise<void> {
  console.log(`[Global Setup] Waiting for ${name} at ${url}...`);
  
  for (let i = 0; i < maxAttempts; i++) {
    try {
      const response = await fetch(url, { method: 'GET' });
      if (response.ok) {
        console.log(`[Global Setup] ${name} is ready`);
        return;
      }
    } catch {
      // Service not ready yet
    }
    await new Promise(resolve => setTimeout(resolve, 2000));
  }
  throw new Error(`${name} at ${url} did not become ready in time`);
}

async function setupTestData(apiBase: string): Promise<void> {
  console.log('[Global Setup] Setting up test data...');
  
  // This would typically call test data setup APIs
  // For now, we'll just log that it's a placeholder
  console.log('[Global Setup] Test data setup placeholder - implement based on backend APIs');
}

async function captureBaselines(baseURL: string): Promise<void> {
  console.log('[Global Setup] Capturing baselines...');
  // Baseline capture logic would go here
}

export default globalSetup;