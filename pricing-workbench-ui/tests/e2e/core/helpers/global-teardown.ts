import type { FullConfig } from '@playwright/test';
import fs from 'node:fs/promises';
import path from 'node:path';

async function globalTeardown(_config: FullConfig) {
  const finishedAt = new Date().toISOString();
  const contextPath = path.resolve('tests/results/pii25-run-context.json');
  const current = await fs.readFile(contextPath, 'utf8').catch(() => '{}');
  const payload = { ...JSON.parse(current), finishedAt };
  await fs.writeFile(contextPath, JSON.stringify(payload, null, 2));
  console.log(`[PII-25 E2E] Finished mocked Playwright run at ${finishedAt}`);
}

export default globalTeardown;
