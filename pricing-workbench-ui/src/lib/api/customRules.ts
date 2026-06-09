export type CustomFieldMetadata = {
  fieldId: string;
  label: string;
  dataType: string;
  allowedValues: string[];
  helpText: string;
  decisionQuality: string;
  sourceRef: string;
  validationMessages: string[];
  requiredForRules: boolean;
};

export type RuleEvidenceRow = {
  ruleRef: string;
  versionRef: string;
  outcome: string;
  reasonCode: string;
  factRefs: string[];
};

export type CalculationEvidence = {
  matchedRules: RuleEvidenceRow[];
  skippedRules: RuleEvidenceRow[];
  reasonCodes: string[];
  precision: string;
  replayHashRef: string;
};

export type DesignEvidenceStatus = {
  status: string;
  blocker: string;
  safeOptions: string[];
};

export type CustomRuleEvidenceView = {
  tenantContext: string;
  dependencyStatus: string;
  uiTraceId: string;
  fields: CustomFieldMetadata[];
  evidence: CalculationEvidence;
  commitBlockers: string[];
  commitDisabled: boolean;
  designEvidence: DesignEvidenceStatus;
  events: string[];
  fallbackReason: string;
};

const customRulesHeaders = {
  Accept: 'application/json',
  'X-Ui-Trace-Id': 'cr-s01-local-trace',
  'X-Tenant-Context': 'ui-preview-tenant',
};

export async function fetchCustomRuleEvidence(fetchImpl: typeof fetch = fetch): Promise<CustomRuleEvidenceView> {
  const response = await fetchImpl('/api/v1/custom-rules/evidence', { headers: customRulesHeaders });
  if (response.status >= 500) throw new Error('BFF custom rule evidence boundary is temporarily unavailable.');
  return (await response.json()) as CustomRuleEvidenceView;
}
