import { readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const evidenceDir = 'tests/results/live-functional-e2e';
const jsonlPath = join(evidenceDir, 'functional-results.jsonl');
const reportPath = join(evidenceDir, 'functional-e2e-usability-report.md');
const lines = readFileSync(jsonlPath, 'utf8').trim().split(/\r?\n/).filter(Boolean);
const results = lines.map((line) => JSON.parse(line));
const screenshots = new Set(readdirSync(join(evidenceDir, 'screenshots')));
const failures = [];

for (const result of results) {
  const screenshotName = result.screenshot.split('/').pop();
  if (!screenshots.has(screenshotName)) failures.push(`${result.id}/${result.viewport}: missing screenshot ${screenshotName}`);
  const finalPath = new URL(result.finalUrl).pathname;
  if (finalPath === '/login') failures.push(`${result.id}/${result.viewport}: ended on login page`);
}

const out = [];
out.push('# Pricing Workbench Functional E2E And Usability Report');
out.push('');
out.push(`Generated: ${new Date().toISOString()}`);
out.push('');
out.push('## Summary');
out.push('');
out.push(`- Functional checks: ${results.length}`);
out.push(`- Passed: ${failures.length === 0 ? results.length : results.length - failures.length}`);
out.push(`- Failed gates: ${failures.length}`);
out.push('- Gate quality: screenshots are accepted only after real login, requested route retention, no auth/loading/access-denied shell, route-specific text, and keyboard focus movement.');
out.push('');
if (failures.length) {
  out.push('## Failures');
  out.push('');
  for (const failure of failures) out.push(`- ${failure}`);
  out.push('');
}
out.push('## Functional Evidence');
out.push('');
for (const result of results) {
  out.push(`### ${result.id} (${result.viewport})`);
  out.push('');
  out.push(`- Requested route: \`${result.path}\``);
  out.push(`- Final URL: \`${result.finalUrl}\``);
  out.push(`- Assertions: ${result.assertions.join(', ')}`);
  out.push(`![${result.id} ${result.viewport}](${result.screenshot.replace('tests/results/live-functional-e2e/', '')})`);
  out.push('');
}
writeFileSync(reportPath, `${out.join('\n')}\n`);
console.log(reportPath);
