import fs from 'node:fs/promises';
import path from 'node:path';
import pixelmatch from 'pixelmatch';
import { PNG } from 'pngjs';

const BASELINE_DIR = path.resolve('tests/baselines/pii25');
const CURRENT_DIR = path.resolve('tests/results/artifacts');
const REPORT_DIR = path.resolve('tests/results/drift');
const thresholdRatio = 0.001;

await fs.mkdir(REPORT_DIR, { recursive: true });
const files = (await fs.readdir(BASELINE_DIR).catch(() => [])).filter((file) => file.endsWith('.png'));
if (!files.length) {
  console.error('[drift] No PII-25 baselines found. Run npm run e2e:drift:baseline first.');
  process.exit(1);
}

const findings = [];
for (const file of files) {
  const baselinePath = path.join(BASELINE_DIR, file);
  const currentPath = path.join(CURRENT_DIR, file);
  const currentExists = await exists(currentPath);
  if (!currentExists) {
    findings.push({ file, status: 'missing-current', diffRatio: 1 });
    continue;
  }
  const baseline = PNG.sync.read(await fs.readFile(baselinePath));
  const current = PNG.sync.read(await fs.readFile(currentPath));
  if (baseline.width !== current.width || baseline.height !== current.height) {
    findings.push({ file, status: 'dimension-mismatch', diffRatio: 1, baseline: [baseline.width, baseline.height], current: [current.width, current.height] });
    continue;
  }
  const diff = new PNG({ width: baseline.width, height: baseline.height });
  const diffPixels = pixelmatch(baseline.data, current.data, diff.data, baseline.width, baseline.height, { threshold: 0.1 });
  const diffRatio = diffPixels / (baseline.width * baseline.height);
  if (diffRatio > thresholdRatio) {
    await fs.writeFile(path.join(REPORT_DIR, `diff-${file}`), PNG.sync.write(diff));
    findings.push({ file, status: 'drift', diffPixels, diffRatio });
  } else {
    findings.push({ file, status: 'pass', diffPixels, diffRatio });
  }
}

await fs.writeFile(path.join(REPORT_DIR, 'pii25-drift-report.json'), JSON.stringify({ thresholdRatio, findings }, null, 2));
const failures = findings.filter((finding) => finding.status !== 'pass');
console.log(`[drift] ${findings.length - failures.length}/${findings.length} passed at <= 0.1% pixel drift`);
if (failures.length) process.exit(1);

async function exists(filePath) {
  return fs.access(filePath).then(() => true, () => false);
}
