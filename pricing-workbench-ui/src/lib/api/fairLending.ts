export type FairLendingViolation = {
  outcome: string;
  protectedClass: string;
  group: string;
  violationType: string;
  value: number;
  threshold: number;
  severity: string;
  recommendedAction: string;
};

export type RegressionResult = {
  outcome: string;
  protectedClass: string;
  coefficients: Record<string, number>;
  pValues: Record<string, number>;
  confidenceIntervals: Record<string, string>;
  rSquared: number;
  sampleSize: number;
  significantDisparity: boolean;
  warnings: string[];
};

export type AIRTable = {
  outcome: string;
  protectedClass: string;
  airRatios: Record<string, number>;
  favorableCounts: Record<string, number>;
  totalCounts: Record<string, number>;
  referenceGroup: string;
  fourFifthsViolation: boolean;
  dataQualityFlags: string[];
};

export type FairLendingReport = {
  reportId: string;
  tenantId: string;
  startDate: string;
  endDate: string;
  sampleSize: number;
  regressionResults: RegressionResult[];
  airTables: AIRTable[];
  violations: FairLendingViolation[];
  recommendations: string[];
  dataQualityFlags: string[];
  createdAt: string;
};

export type FairLendingAnalysisRequest = {
  tenantId: string;
  startDate: string;
  endDate: string;
  protectedClasses: string[];
  outcomes: string[];
  controls: string[];
  marginalEffectThreshold?: number;
};

const headers = {
  Accept: 'application/json',
  'Content-Type': 'application/json',
  'X-Ui-Trace-Id': 'fair-lending-local-trace',
};

export async function runFairLendingAnalysis(request: FairLendingAnalysisRequest, fetchImpl: typeof fetch = fetch): Promise<FairLendingReport> {
  const response = await fetchImpl('/api/v1/fair-lending/analyze', { method: 'POST', headers, body: JSON.stringify(request) });
  if (response.status >= 500) throw new Error('Fair lending analysis service is unavailable.');
  return (await response.json()) as FairLendingReport;
}

export async function fetchFairLendingViolations(tenantId: string, fetchImpl: typeof fetch = fetch): Promise<FairLendingViolation[]> {
  const response = await fetchImpl(`/api/v1/fair-lending/violations?tenantId=${encodeURIComponent(tenantId)}`, { headers });
  if (response.status >= 500) throw new Error('Fair lending violations are unavailable.');
  return (await response.json()) as FairLendingViolation[];
}
