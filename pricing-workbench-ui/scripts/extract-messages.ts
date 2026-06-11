import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';

const sourceRoot = join(process.cwd(), 'src');
const tCallPattern = /\bt\(\s*['"]([^'"]+)['"]/g;

function walk(dir: string): string[] {
  return readdirSync(dir).flatMap((entry) => {
    const path = join(dir, entry);
    if (statSync(path).isDirectory()) return walk(path);
    return /\.(ts|tsx)$/.test(path) ? [path] : [];
  });
}

const keys = new Set<string>();
for (const file of walk(sourceRoot)) {
  const text = readFileSync(file, 'utf8');
  for (const match of text.matchAll(tCallPattern)) keys.add(match[1]);
}

console.log(JSON.stringify({ sourceRoot: relative(process.cwd(), sourceRoot), keyCount: keys.size, keys: Array.from(keys).sort() }, null, 2));
