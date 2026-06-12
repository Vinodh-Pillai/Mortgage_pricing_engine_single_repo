import type { FullConfig } from '@playwright/test';
import fs from 'node:fs/promises';
import path from 'node:path';

const resultDirs = [
  'tests/results',
  'tests/results/artifacts',
  'tests/results/html-report',
  'tests/results/drift',
  'tests/baselines',
];

async function globalSetup(config: FullConfig) {
  const baseURL = String(config.projects[0]?.use?.baseURL ?? 'http://localhost:3000');
  console.log(`[PII-25 E2E] Preparing mocked Playwright run against ${baseURL}`);

  await Promise.all(resultDirs.map((dir) => fs.mkdir(path.resolve(dir), { recursive: true })));
  await fs.writeFile(
    path.resolve('tests/results/pii25-run-context.json'),
    JSON.stringify({ baseURL, startedAt: new Date().toISOString(), mockedBackend: true }, null, 2),
  );
}

export default globalSetup;
