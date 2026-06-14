import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const budget = JSON.parse(readFileSync(join(here, 'pii54-lighthouse-budget.json'), 'utf8'));

const requiredRoutes = ['/home', '/admin/tenants', '/admin/products'];
const requiredBudgets = [
  'performance_score_minimum',
  'accessibility_score_minimum',
  'best_practices_score_minimum',
  'largest_contentful_paint_ms_maximum',
  'cumulative_layout_shift_maximum',
  'total_blocking_time_ms_maximum',
];

const missingRoutes = requiredRoutes.filter((route) => !budget.routes.includes(route));
const missingBudgets = requiredBudgets.filter((key) => typeof budget.budgets[key] !== 'number');

if (missingRoutes.length || missingBudgets.length) {
  console.error(JSON.stringify({ missingRoutes, missingBudgets }, null, 2));
  process.exit(1);
}

console.log(JSON.stringify({ story_id: budget.story_id, routes: budget.routes.length, budgets: requiredBudgets.length, status: 'budget-artifact-valid' }));
