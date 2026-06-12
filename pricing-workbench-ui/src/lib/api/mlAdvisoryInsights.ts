export type AdvisoryRecommendationInsight = {
  recommendationId: string;
  modelVersion: string;
  confidence: string | number;
  explanation: string;
  allowedActions: string[];
  auditRefs: string[];
  automaticDecisionApplied: boolean;
  featureImportance?: string[];
  counterfactual?: string;
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

export type MlAdvisoryFeedbackOutcome = 'ACCEPTED' | 'REJECTED' | 'MODIFIED';

export type MlAdvisoryFeedbackRequest = {
  recommendationId: string;
  modelVersion: string;
  rating: number;
  comment: string;
  outcome: MlAdvisoryFeedbackOutcome;
  modifiedValues?: string;
};

export type MlAdvisoryFeedbackResult = {
  recommendationId: string;
  status: string;
  message: string;
  evidenceRef?: string;
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

export async function submitMlAdvisoryFeedback(
  feedback: MlAdvisoryFeedbackRequest,
  fetchImpl: typeof fetch = fetch,
): Promise<MlAdvisoryFeedbackResult> {
  const response = await fetchImpl('/api/v1/ml-advisory/feedback', {
    method: 'POST',
    headers: { ...mlAdvisoryHeaders, 'Content-Type': 'application/json' },
    body: JSON.stringify(feedback),
  });
  if (response.status >= 500) throw new Error('BFF ML advisory feedback boundary is temporarily unavailable.');
  return (await response.json()) as MlAdvisoryFeedbackResult;
}
