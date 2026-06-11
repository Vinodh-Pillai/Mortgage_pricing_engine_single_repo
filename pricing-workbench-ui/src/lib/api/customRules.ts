export type CustomFieldMetadata = {
  fieldId: string;
  label: string;
  dataType: string;
  allowedValues: string[];
  helpText: string;
  decisionQuality: string;
  sourceRef: string;
  recoveryOwner?: string;
  sourceRefs?: string[];
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

export type RuleCalculationEvidenceRow = RuleEvidenceRow & {
  sourceService: string;
  auditRefs: string[];
  replayHashRefs: string[];
};

export type CalculationStepEvidence = {
  stepRef: string;
  sourceService: string;
  status: string;
  evidenceRef: string;
  summary: string;
  ledgerStepRef?: string;
  precision?: string;
  rounding?: string;
  units?: string;
};

export type CustomRuleUiError = {
  code: string;
  sourceService: string;
  message: string;
};

export type RuleCalculationEvidenceView = {
  quoteId: string;
  tenantContext: string;
  dependencyStatus: string;
  resultStatus: string;
  uiTraceId: string;
  matchedRules: RuleCalculationEvidenceRow[];
  skippedRules: RuleCalculationEvidenceRow[];
  blockedRules: RuleCalculationEvidenceRow[];
  calculationSteps: CalculationStepEvidence[];
  reasonCodes: string[];
  auditRefs: string[];
  replayHashRefs: string[];
  errors: CustomRuleUiError[];
  events: string[];
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

export async function fetchCustomRuleCalculationEvidence(
  quoteId = 'quote-ref-required',
  fetchImpl: typeof fetch = fetch,
): Promise<RuleCalculationEvidenceView> {
  const query = new URLSearchParams({ quoteId });
  const response = await fetchImpl(`/api/ui/custom-rules/evidence?${query.toString()}`, { headers: customRulesHeaders });
  if (response.status >= 500) throw new Error('BFF custom rule calculation evidence boundary is temporarily unavailable.');
  return (await response.json()) as RuleCalculationEvidenceView;
}
