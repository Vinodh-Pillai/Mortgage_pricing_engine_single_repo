import { blockedDriftMonitoringView } from './fixtures';
import type { DriftAlertView, DriftInvestigationView, DriftMonitoringView } from './types';

const driftHeaders = {
  Accept: 'application/json',
  'X-Ui-Trace-Id': 'drift-s27-local-trace',
  'X-Tenant-Context': 'ui-preview-tenant',
};

export async function fetchDriftMonitoringView(fetchImpl: typeof fetch = fetch): Promise<DriftMonitoringView> {
  const modelVersion = blockedDriftMonitoringView.modelVersion;
  const timeRange = blockedDriftMonitoringView.timeRange;
  const [feature, prediction, population, alerts] = await Promise.all([
    fetchJson(fetchImpl, `/api/v1/ml-advisory/drift/feature?modelVersion=${encodeURIComponent(modelVersion)}&timeRange=${timeRange}`),
    fetchJson(fetchImpl, `/api/v1/ml-advisory/drift/prediction?modelVersion=${encodeURIComponent(modelVersion)}&timeRange=${timeRange}`),
    fetchJson(fetchImpl, `/api/v1/ml-advisory/drift/population?modelVersion=${encodeURIComponent(modelVersion)}&timeRange=${timeRange}`),
    fetchJson(fetchImpl, `/api/v1/ml-advisory/drift/alerts?modelVersion=${encodeURIComponent(modelVersion)}`),
  ]);

  const alertRows = normalizeAlerts(alerts);
  const selectedAlertId = alertRows[0]?.alertId ?? blockedDriftMonitoringView.investigation.selectedAlertId;
  let investigation: DriftInvestigationView = blockedDriftMonitoringView.investigation;
  if (selectedAlertId) {
    try {
      investigation = normalizeInvestigation(await fetchJson(fetchImpl, `/api/v1/ml-advisory/drift/investigation?alertId=${encodeURIComponent(selectedAlertId)}`));
    } catch {
      investigation = blockedDriftMonitoringView.investigation;
    }
  }

  return {
    ...blockedDriftMonitoringView,
    featureDrift: Array.isArray(feature) ? feature : blockedDriftMonitoringView.featureDrift,
    predictionDrift: typeof prediction === 'object' && prediction !== null ? { ...blockedDriftMonitoringView.predictionDrift, ...prediction } : blockedDriftMonitoringView.predictionDrift,
    populationStability: typeof population === 'object' && population !== null ? { ...blockedDriftMonitoringView.populationStability, ...population } : blockedDriftMonitoringView.populationStability,
    alerts: alertRows,
    investigation,
  };
}

async function fetchJson(fetchImpl: typeof fetch, url: string) {
  const response = await fetchImpl(url, { headers: driftHeaders });
  if (!response.ok) throw new Error('ML advisory drift boundary is temporarily unavailable.');
  return response.json();
}

function normalizeAlerts(value: unknown): DriftAlertView[] {
  return Array.isArray(value) ? value as DriftAlertView[] : blockedDriftMonitoringView.alerts;
}

function normalizeInvestigation(value: unknown): DriftInvestigationView {
  return typeof value === 'object' && value !== null ? { ...blockedDriftMonitoringView.investigation, ...value } : blockedDriftMonitoringView.investigation;
}
