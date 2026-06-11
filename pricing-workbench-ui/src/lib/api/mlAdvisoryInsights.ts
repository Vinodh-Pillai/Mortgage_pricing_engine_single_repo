export type AdvisoryRecommendationInsight = {
  recommendationId: string;
  modelVersion: string;
  confidence: string;
  explanation: string;
  allowedActions: string[];
  auditRefs: string[];
  automaticDecisionApplied: boolean;
};

export type ModelVersionGovernanceInsight = {
  modelVersion: string;
  driftStatus: string;
  alertState: string;
  feedbackLoops: string[];
  exportEvidenceRefs: string[];
};

export type MlAdvisoryInsightsView = {
  tenantContext: string;
  dependencyStatus: string;
  uiTraceId: string;
  recommendations: AdvisoryRecommendationInsight[];
  modelVersions: ModelVersionGovernanceInsight[];
  advisoryUnavailable: boolean;
  fallbackReason: string;
  events: string[];
};

const mlAdvisoryHeaders = {
  Accept: 'application/json',
  'X-Ui-Trace-Id': 'ml-s14-local-trace',
  'X-Tenant-Context': 'ui-preview-tenant',
};

export async function fetchMlAdvisoryInsights(fetchImpl: typeof fetch = fetch): Promise<MlAdvisoryInsightsView> {
  const response = await fetchImpl('/api/v1/ml-advisory/insights', { headers: mlAdvisoryHeaders });
  if (response.status >= 500) throw new Error('BFF ML advisory insights boundary is temporarily unavailable.');
  return (await response.json()) as MlAdvisoryInsightsView;
}
