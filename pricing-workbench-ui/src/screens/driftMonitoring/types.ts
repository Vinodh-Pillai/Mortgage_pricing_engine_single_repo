export type DriftStatus = 'STABLE' | 'WARNING' | 'CRITICAL';
export type AlertSeverity = 'INFO' | 'WARNING' | 'CRITICAL';
export type AlertStatus = 'ACTIVE' | 'ACKNOWLEDGED' | 'RESOLVED' | 'SUPPRESSED';

export type FeatureDriftRow = {
  featureName: string;
  psi: number;
  ksStatistic: number;
  pValue: number;
  histogram: string[];
  status: DriftStatus;
  evidenceRef: string;
};

export type PredictionDriftView = {
  meanShiftStdDev: number;
  meanShiftStatus: DriftStatus;
  distributionShift: string;
  accuracy: number | null;
  accuracyState: string;
  calibrationRef: string;
};

export type PopulationStabilityView = {
  psiOverTime: Array<{ bucket: string; psi: number }>;
  cohorts: Array<{ cohort: string; psi: number; topFeature: string; status: DriftStatus }>;
};

export type DriftAlertView = {
  alertId: string;
  rule: string;
  severity: AlertSeverity;
  modelVersion: string;
  metric: string;
  triggeredAt: string;
  status: AlertStatus;
  owner: string;
  currentValue: string;
  threshold: string;
  history: string[];
};

export type DriftInvestigationView = {
  selectedAlertId: string | null;
  rootCauseNotes: string[];
  dataQualityChecks: string[];
  featureAttribution: string[];
  remediationPlan: Array<{ action: string; owner: string; dueDate: string; status: string }>;
  exportEvidenceRef: string;
  replayHashRef: string;
};

export type DriftMonitoringView = {
  tenantContext: string;
  modelVersion: string;
  modelVersionStatus: string;
  timeRange: '1h' | '6h' | '24h' | '7d' | '30d';
  dependencyStatus: string;
  uiTraceId: string;
  fallbackReason: string;
  featureDrift: FeatureDriftRow[];
  predictionDrift: PredictionDriftView;
  populationStability: PopulationStabilityView;
  alerts: DriftAlertView[];
  investigation: DriftInvestigationView;
  events: string[];
};
