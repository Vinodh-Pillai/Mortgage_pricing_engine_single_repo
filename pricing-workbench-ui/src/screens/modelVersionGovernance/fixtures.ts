import type { ModelVersionGovernanceView } from '../../lib/api/modelVersionGovernance';

export const blockedModelVersionGovernanceView: ModelVersionGovernanceView = {
  tenantContext: 'ui-preview-tenant',
  dependencyStatus: 'ML_ADVISORY_GOVERNANCE_CONTRACT_NOT_CONFIGURED',
  uiTraceId: 'mvg-s26-local-trace',
  fallbackReason: 'Configured ML advisory governance APIs are unavailable; fallback records only non-secret references, blocked action state, and backend-owned threshold references.',
  registry: [
    {
      modelId: 'pricing-advisory-model',
      version: 'model-version-ref-required',
      status: 'PENDING_APPROVAL',
      registeredBy: 'data-science-owner-ref',
      registeredAt: 'timestamp-supplied-by-service',
      artifacts: [
        { artifactId: 'model-card', checksum: 'checksum-ref-required', locationRef: 'artifact-store-ref-required' },
        { artifactId: 'validation-report', checksum: 'validation-checksum-ref-required', locationRef: 'validation-report-ref-required' },
      ],
      metadata: ['model-card-ref-required', 'data-card-ref-required', 'intended-use-ref-required'],
      validationStatus: 'VALIDATION_EVIDENCE_REQUIRED',
      validationMessages: ['Configured validation evidence is required before approval can proceed.'],
    },
  ],
  approvals: [
    {
      modelId: 'pricing-advisory-model',
      version: 'model-version-ref-required',
      approvalStatus: 'IN_REVIEW',
      requestedBy: 'data-science-owner-ref',
      requestedAt: 'timestamp-supplied-by-service',
      approvers: ['risk-reviewer-ref', 'model-governance-reviewer-ref'],
      criteria: [
        { criterionId: 'performance', label: 'Performance evidence', status: 'CONFIGURED_THRESHOLD_REQUIRED', evidenceRef: 'performance-evidence-ref-required', configuredValueRef: 'performance-threshold-ref-required' },
        { criterionId: 'drift', label: 'Drift tests', status: 'CONFIGURED_THRESHOLD_REQUIRED', evidenceRef: 'drift-evidence-ref-required', configuredValueRef: 'drift-threshold-ref-required' },
        { criterionId: 'bias', label: 'Bias tests', status: 'CONFIGURED_THRESHOLD_REQUIRED', evidenceRef: 'bias-evidence-ref-required', configuredValueRef: 'bias-threshold-ref-required' },
        { criterionId: 'security', label: 'Security scan', status: 'EVIDENCE_REQUIRED', evidenceRef: 'security-scan-ref-required' },
        { criterionId: 'documentation', label: 'Model and data cards', status: 'EVIDENCE_REQUIRED', evidenceRef: 'documentation-ref-required' },
      ],
      decision: 'DECISION_BLOCKED_UNTIL_CONFIGURED_EVIDENCE',
      auditEvents: ['ModelApprovalRequested', 'ModelApprovalReviewOpened'],
    },
  ],
  lifecycle: [
    {
      modelId: 'pricing-advisory-model',
      version: 'model-version-ref-required',
      currentStatus: 'PENDING_APPROVAL',
      transitionBlockers: ['Approval decision required', 'Compatibility check required', 'Monitoring config required'],
      monitoringConfigRef: 'monitoring-config-ref-required',
      compatibilityCheckRef: 'compatibility-check-ref-required',
      replacementModel: 'replacement-model-ref-required',
      migrationTimeline: 'migration-timeline-ref-required',
    },
  ],
  compatibility: [
    {
      modelVersion: 'model-version-ref-required',
      consumerService: 'pricing-service',
      status: 'UNTESTED',
      breakingChanges: ['input-output-schema-check-required'],
      migrationPath: 'migration-path-ref-required',
      testResults: ['compatibility-test-result-ref-required'],
    },
    {
      modelVersion: 'model-version-ref-required',
      consumerService: 'advisory-service',
      status: 'COMPATIBILITY_EVIDENCE_REQUIRED',
      breakingChanges: [],
      migrationPath: 'migration-path-ref-required',
      testResults: ['compatibility-test-result-ref-required'],
    },
  ],
  monitoring: {
    drift: [
      { metricId: 'feature-drift', label: 'Feature drift', status: 'BASELINE_REQUIRED', valueLabel: 'value supplied by monitoring API', thresholdRef: 'feature-drift-threshold-ref-required', evidenceRef: 'feature-drift-evidence-ref-required' },
      { metricId: 'prediction-drift', label: 'Prediction drift', status: 'BASELINE_REQUIRED', valueLabel: 'value supplied by monitoring API', thresholdRef: 'prediction-drift-threshold-ref-required', evidenceRef: 'prediction-drift-evidence-ref-required' },
      { metricId: 'population-stability-index', label: 'Population stability index', status: 'BASELINE_REQUIRED', valueLabel: 'value supplied by monitoring API', thresholdRef: 'psi-threshold-ref-required', evidenceRef: 'psi-evidence-ref-required' },
    ],
    performance: [
      { metricId: 'latency', label: 'Latency', status: 'SLA_REF_REQUIRED', valueLabel: 'latency value supplied by monitoring API', thresholdRef: 'latency-sla-ref-required', evidenceRef: 'latency-evidence-ref-required' },
      { metricId: 'throughput', label: 'Throughput', status: 'BASELINE_REQUIRED', valueLabel: 'throughput supplied by monitoring API', thresholdRef: 'throughput-baseline-ref-required', evidenceRef: 'throughput-evidence-ref-required' },
      { metricId: 'accuracy', label: 'Accuracy when labeled', status: 'LABELLED_SAMPLE_REQUIRED', valueLabel: 'accuracy supplied by monitoring API', thresholdRef: 'accuracy-threshold-ref-required', evidenceRef: 'accuracy-evidence-ref-required' },
    ],
    alerting: [
      { alertId: 'drift-alert-config', severity: 'severity supplied by alert API', status: 'ALERT_RULE_REQUIRED', notificationChannels: ['channel-ref-required'], escalationOwner: 'model-operations-owner-ref', blocker: 'Configured alert rule and escalation policy are required.' },
    ],
  },
  actions: {
    registerEnabled: false,
    approvalDecisionEnabled: false,
    lifecycleTransitionEnabled: false,
    compatibilityCheckEnabled: false,
  },
  events: ['ModelVersionGovernanceOpened', 'ModelVersionGovernanceActionsBlocked'],
};
