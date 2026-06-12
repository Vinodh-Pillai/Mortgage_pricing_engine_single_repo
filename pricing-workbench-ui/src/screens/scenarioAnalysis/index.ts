export { ScenarioAnalysisWorkspaceScreen } from './ScenarioAnalysisWorkspace';
export { LockPeriodComparisonScreen, ProductComparisonScreen } from './comparison';
export { FicoSensitivityScreen, LtvSensitivityScreen } from './sensitivity';

export const scenarioAnalysisRoutes = ['/quote/:runId/what-if', '/quote/:runId/what-if/fico-sensitivity', '/quote/:runId/what-if/ltv-sensitivity', '/quote/:runId/what-if/product-comparison', '/quote/:runId/what-if/lock-period-comparison'];
export const scenarioAnalysisStateCoverage = [
  'load-state',
  'dimensions',
  'guardrails',
  'batch-grid',
  'saved-analysis',
  'export-replay',
  'backend-recalculation',
  'fico-sensitivity',
  'fico-bands-table',
  'fico-rate-chart',
  'fico-price-chart',
  'fico-eligibility-heatmap',
  'ltv-sensitivity',
  'ltv-bands-table',
  'ltv-rate-chart',
  'ltv-price-chart',
  'ltv-mip-chart',
  'ltv-eligibility-heatmap',
  'product-comparison',
  'product-selector',
  'comparison-matrix',
  'total-cost',
  'feature-checklist',
  'lock-period-comparison',
  'lock-periods-table',
  'lock-period-rate-price-chart',
  'lock-period-extension-analysis',
  'lock-period-float-down',
] as const;
export const scenarioAnalysisEvidenceTarget = '.local-harness/evidence/PII-24-S29/scenario-analysis-workspace.json';
export const ficoSensitivityEvidenceTarget = '.local-harness/evidence/PII-24-S30/fico-sensitivity.json';
export const ltvSensitivityEvidenceTarget = '.local-harness/evidence/PII-24-S31/ltv-sensitivity.json';
export const productComparisonEvidenceTarget = '.local-harness/evidence/PII-24-S32/product-comparison.json';
export const lockPeriodComparisonEvidenceTarget = '.local-harness/evidence/PII-24-S33/lock-period-comparison.json';
