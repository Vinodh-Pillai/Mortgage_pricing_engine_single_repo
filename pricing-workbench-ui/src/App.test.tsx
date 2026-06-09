import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from './App';

describe('App shell', () => {
  beforeEach(() => {
    window.history.pushState({}, '', '/quote/start');
    sessionStorage.clear();
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = input.toString();
        if (url === '/api/ui/health') {
          return {
            ok: true,
            json: async () => ({
              service: 'pricing-bff',
              status: 'UP',
              ready: true,
              dependencyStatus: 'NO_UPSTREAMS_CONFIGURED',
              dependencies: [],
            }),
          };
        }

        if (url === '/api/v1/audit-replay/workbench') {
          return {
            ok: true,
            json: async () => ({
              tenantContext: 'ui-preview-tenant',
              dependencyStatus: 'AUDIT_REPLAY_SERVICE_CONTRACT_NOT_CONFIGURED',
              uiTraceId: 'ar-s10-local-trace',
              records: [
                {
                  eventId: 'event-id-required',
                  subjectType: 'quote',
                  subjectId: 'quote-id-required',
                  action: 'QUOTE_REPLAY_REQUESTED',
                  hashIntegrity: 'INTEGRITY_PENDING',
                  redactionProfile: 'redaction-profile-required',
                  retentionState: 'retention-policy-ref-required',
                  legalHold: false,
                  exportEligibility: 'BLOCKED_UNTIL_CONFIGURED_SERVICE',
                  evidenceRefs: ['audit-record-id-required', 'integrity-hash-required'],
                },
                {
                  eventId: 'event-id-lock-required',
                  subjectType: 'lock',
                  subjectId: 'lock-id-required',
                  action: 'LOCK_REPLAY_REQUESTED',
                  hashIntegrity: 'INTEGRITY_PENDING',
                  redactionProfile: 'legal-hold-redaction-profile-required',
                  retentionState: 'LEGAL_HOLD_ACTIVE',
                  legalHold: true,
                  exportEligibility: 'EXPORT_LOCKED_BY_LEGAL_HOLD',
                  evidenceRefs: ['audit-record-id-required', 'previous-hash-required'],
                },
              ],
              replayRuns: [
                {
                  runId: 'quote-replay-run-required',
                  replayType: 'QUOTE',
                  subjectId: 'quote-id-required',
                  status: 'BLOCKED',
                  originalHash: 'original-hash-required',
                  replayHash: 'replay-hash-required',
                  diffs: ['quote snapshot diff supplied by audit-replay-service'],
                  missingDependencyBlockers: ['quote-service replay dependency is unavailable'],
                  versionRefs: ['quote-version-ref-required'],
                },
              ],
              exportSummary: {
                exportId: 'evidence-export-required',
                status: 'BLOCKED',
                redactionProfile: 'redaction-profile-required',
                retentionUntil: 'retention-until-supplied-by-audit-replay-service',
                legalHold: true,
                downloadEligible: false,
                manifestHash: 'manifest-hash-required',
                blockers: ['legal hold prevents direct download until backend release decision is supplied'],
              },
              contractRefs: [
                { contractId: 'audit-record-search', route: '/api/v1/tenants/{tenantId}/audit-records', preservedDecision: 'Shows event ids, integrity hashes, redaction profile, retention date, and legal hold flags.' },
              ],
              blockers: ['Configured audit-replay-service endpoint is not wired to pricing-bff local fallback.'],
              events: ['AuditReplayWorkbenchOpened'],
              fallbackReason: 'Configured audit-replay-service contracts are unavailable; this response carries non-secret fallback evidence refs, blockers, retention states, and redaction states only.',
            }),
          };
        }

        if (url === '/api/v1/admin/governance') {
          return {
            ok: true,
            json: async () => ({
              tenantContext: 'ui-preview-tenant',
              adminRole: 'release-governance-preview',
              dependencyStatus: 'FALLBACK_STATIC_DEPENDENCIES_UNAVAILABLE',
              uiTraceId: 'ag-s09-local-trace',
              traceMetadata: {
                traceId: 'ag-s09-local-trace',
                artifactId: 'artifact-admin-governance-fallback',
                policyVersion: 'policy-version-required',
                environment: 'environment-config-required',
                signerMetadata: 'signer-contract-required',
              },
              descriptors: [
                {
                  stableId: 'config-lifecycle',
                  label: 'configuration lifecycle',
                  type: 'workflow',
                  allowedOperators: ['simulate', 'approve', 'publish', 'rollback'],
                  valueSources: ['governance-service.config-lifecycle', 'audit-replay-service.audit-records'],
                  decisionQualityRequirement: 'CONFIRMED_BACKEND_EVIDENCE',
                  validationMessages: ['Configured lifecycle validation evidence is required before publish.'],
                  versionRef: 'config-lifecycle-version-ref-required',
                },
              ],
              policies: [
                {
                  versionId: 'policy-v2.3.1',
                  owner: 'Policy owner required',
                  status: 'validation_pending',
                  environmentMapping: 'environment binding supplied by governance-service',
                  parentVersionId: 'policy-v2.3.0',
                  hashSignature: 'hash-placeholder-required',
                  diffImpacts: ['module constraint impact requires configured policy diff service'],
                },
              ],
              featureFlags: [
                {
                  flagId: 'flag-config-required',
                  environmentTarget: 'environment target required',
                  enabled: false,
                  unresolvedFlags: ['DEPENDENCY_CONTRACT_REQUIRED', 'OD-004_UNRESOLVED'],
                  activationDisabled: true,
                  emergencyToggleGate: 'Emergency disable path is blocked until OD-004 is resolved and dual-control evidence is configured.',
                },
              ],
              marketRules: [
                {
                  ruleId: 'market-rule-config-required',
                  ruleType: 'state-rule staging',
                  stagingStatus: 'staged',
                  missingRequiredFields: ['caps', 'disclosures', 'usury', 'antiRedlining'],
                  promotionDisabled: true,
                  completenessGate: 'Completeness gate blocks promotion until configured market-rule evidence supplies required fields.',
                },
              ],
              changeRequests: [
                {
                  requestId: 'CR-release-candidate-config-required',
                  requestType: 'release_candidate',
                  state: 'blocked',
                  riskLevel: 'P2',
                  owner: 'Release / Governance Manager',
                  requiredStateSequence: ['pending_review', 'compliance_check', 'governance_check', 'approved', 'deployed'],
                  promotionDisabled: true,
                  blockers: ['OD-001', 'OD-002', 'OD-004', 'OD-005'],
                },
              ],
              releaseCandidate: {
                candidateId: 'RC-config-required',
                readinessStatus: 'RED',
                environmentTarget: 'environment-config-required',
                deployDisabled: true,
                rollbackDisabled: true,
                releaseFingerprint: 'releaseFingerprint-required',
                manifestRef: 'manifestRef-required',
                signature: 'signature-required',
                gates: [
                  { gateName: 'smoke-tests', status: 'BLOCKED', mandatory: true, artifactRef: 'Configured smoke test evidence is required.' },
                  { gateName: 'quality-guardrails', status: 'FAIL', mandatory: true, artifactRef: 'Quality guardrails report open blockers from /api/v1/quality/dashboard.' },
                ],
                blockers: ['OD-001 unresolved blocks RBAC source and role-to-privilege ingestion.', 'OD-004 unresolved blocks emergency feature-flag disable routing.'],
                affectedSubsystems: ['pricing', 'workflow', 'notifications', 'disclosures'],
              },
              openDecisions: [
                { decisionId: 'OD-001', title: 'Enterprise identity and RBAC source for role-to-privilege ingestion', status: 'BLOCKING', resolutionRef: 'world-class-pricing-engine/11-assumptions-open-decisions.md' },
                { decisionId: 'OD-002', title: 'Exact approver quorum per environment', status: 'BLOCKING', resolutionRef: 'world-class-pricing-engine/11-assumptions-open-decisions.md' },
                { decisionId: 'OD-004', title: 'Emergency feature-flag disable path', status: 'BLOCKING', resolutionRef: 'world-class-pricing-engine/11-assumptions-open-decisions.md' },
                { decisionId: 'OD-005', title: 'Retention windows for override and diff artifact views', status: 'BLOCKING', resolutionRef: 'market-gap-update-plan.md' },
              ],
              driftAlerts: [{ alertId: 'drift-config-required', severity: 'HIGH', environment: 'environment-config-required', owner: 'SRE / Operations Lead', summary: 'Configured baseline and alert threshold are required; no numeric threshold is inferred.', acknowledged: false }],
              incidents: [{ incidentId: 'INC-release-gate-config-required', status: 'active', rollbackTarget: 'rollback-target-required', rcaLinked: false, correctiveActionDone: false, closeDisabled: true, closureGate: 'Rollback execution is disabled until configured rollback target, RCA, corrective action, and dual-control evidence exist.' }],
              overrideLedger: [{ ledgerId: 'override-ledger-config-required', actor: 'actor-required', timestamp: 'timestamp-required', fieldPath: 'fieldPath-required', oldValue: 'old-value-redacted', newValue: 'new-value-redacted', policyRef: 'policy_ref-required', reason: 'reason-required', approvalRequired: true, auditRef: 'auditRef-required' }],
              pendingReview: {
                reviewId: 'PCR-config-lifecycle-required',
                state: 'PENDING_REVIEW',
                simulationVisible: true,
                approvalVisible: true,
                publishVisible: true,
                rollbackVisible: true,
                auditRef: 'auditRef-required',
                downstreamConsumers: ['pricing-bff', 'pricing-workbench-ui', 'governance-service'],
                blockers: ['Configured simulation evidence is required before approval.'],
              },
              dynamicRuleEvidence: {
                matchedRules: [{ ruleRef: 'rule-evidence-contract-required', versionRef: 'rule-evidence-version-ref-required', outcome: 'MATCHED', reasonCode: 'RULE_EVIDENCE_VISIBLE', factRefs: ['fact:configured-governance-metadata'] }],
                skippedRules: [{ ruleRef: 'rule-skipped-unknown-fact', versionRef: 'rule-evidence-version-ref-required', outcome: 'SKIPPED', reasonCode: 'UNKNOWN_FACT_FAIL_CLOSED', factRefs: ['fact:unknown-governance-input'] }],
                actionOutputs: ['action-output-ref-required'],
                factRefs: ['fact:configured-governance-metadata', 'fact:unknown-governance-input'],
                precisionMetadataRef: 'precision-metadata-ref-required',
                replayHashRef: 'replay-hash-ref-required',
              },
              events: ['AdminGovernanceOpened'],
              fallbackReason: 'Configured governance, policy, release, drift, incident, and audit services are unavailable; this response carries non-secret UI fallback records only.',
            }),
          };
        }

        if (url === '/api/v1/custom-rules/evidence') {
          return {
            ok: true,
            json: async () => ({
              tenantContext: 'ui-preview-tenant',
              dependencyStatus: 'FALLBACK_STATIC_DEPENDENCIES_UNAVAILABLE',
              uiTraceId: 'cr-s01-local-trace',
              fields: [
                {
                  fieldId: 'custom-field-evidence-source',
                  label: 'Evidence source',
                  dataType: 'text',
                  allowedValues: ['backend metadata required'],
                  helpText: 'Source reference supplied by the configured rule evidence contract.',
                  decisionQuality: 'UNKNOWN',
                  sourceRef: 'configured-governance-metadata',
                  validationMessages: ['Backend metadata marks this required fact as UNKNOWN.'],
                  requiredForRules: true,
                },
                {
                  fieldId: 'custom-field-decision-quality',
                  label: 'Decision quality',
                  dataType: 'enumeration',
                  allowedValues: ['VERIFIED', 'UNKNOWN', 'CONFLICTING'],
                  helpText: 'Decision state supplied by typed fact evaluation.',
                  decisionQuality: 'CONFLICTING',
                  sourceRef: 'typed-fact-contract',
                  validationMessages: ['Conflicting required fact blocks commit until the backend returns a resolved state.'],
                  requiredForRules: true,
                },
              ],
              evidence: {
                matchedRules: [{ ruleRef: 'rule-evidence-contract-required', versionRef: 'version-ref-required', outcome: 'MATCHED', reasonCode: 'RULE_EVIDENCE_VISIBLE', factRefs: ['custom-field-evidence-source', 'custom-field-decision-quality'] }],
                skippedRules: [{ ruleRef: 'rule-skipped-conflicting-fact', versionRef: 'version-ref-required', outcome: 'SKIPPED', reasonCode: 'REQUIRED_FACT_CONFLICTING', factRefs: ['custom-field-decision-quality'] }],
                reasonCodes: ['RULE_EVIDENCE_VISIBLE', 'REQUIRED_FACT_UNKNOWN', 'REQUIRED_FACT_CONFLICTING'],
                precision: 'Precision metadata supplied by configured backend evidence.',
                replayHashRef: 'replay-hash-ref-required',
              },
              commitBlockers: ['Required fact custom-field-evidence-source is UNKNOWN.', 'Required fact custom-field-decision-quality is CONFLICTING.'],
              commitDisabled: true,
              designEvidence: {
                status: 'DESIGN_EVIDENCE_BLOCKED',
                blocker: 'External screenshot/PDF evidence is unavailable until copied into a project-relative evidence path.',
                safeOptions: ['Copy approved files under .local-harness/screenshots/PII-21-S01/source/'],
              },
              events: ['CustomRuleEvidenceOpened'],
              fallbackReason: 'Configured typed-fact and rule evidence services are unavailable; this response carries non-secret fallback metadata and blockers only.',
            }),
          };
        }

        if (url === '/api/v1/platform/tenant-context') {
          return {
            ok: true,
            json: async () => ({
              tenantContext: 'ui-preview-tenant',
              dependencyStatus: 'TENANT_CONTEXT_SERVICE_CONTRACT_NOT_CONFIGURED',
              uiTraceId: 'tc-s08-local-trace',
              trace: {
                tenantIdRef: 'tenant-id-visible-from-request',
                correlationIdRef: 'correlation-id-required',
                idempotencyKeyRef: 'idempotency-key-required',
                eventEnvelopeRef: 'event-envelope-ref-required',
                auditRef: 'audit:tenant-context-platform-required',
                replayHashRef: 'replay-hash-required',
              },
              controls: [
                { controlId: 'tenant-resolution', label: 'Tenant resolution', status: 'VISIBLE', guidance: 'tenant-context-service resolves request tenant, actor, channel, and permitted tenant scope.', evidenceRefs: ['tenant-id', 'actor-id'], blockers: [] },
                { controlId: 'cache-scope', label: 'Tenant-scoped cache keys', status: 'VISIBLE', guidance: 'Cache evidence stays tenant-scoped and shows invalidation references instead of cache contents.', evidenceRefs: ['cache-scope-ref', 'cache-invalidation-event-ref'], blockers: [] },
                { controlId: 'rate-limit', label: 'Rate limiting guard', status: 'BLOCKED', guidance: 'Rate limit outcomes are visible only when tenant-context-service supplies a configured policy decision.', evidenceRefs: ['rate-limit-policy-ref-required'], blockers: ['Configured tenant rate-limit policy contract is unavailable in local fallback mode.'] },
                { controlId: 'audit-outbox', label: 'Audit and event envelope', status: 'VISIBLE', guidance: 'Audit refs, outbox refs, event envelope refs, and replay hashes are displayed as backend-owned evidence.', evidenceRefs: ['audit-ref', 'outbox-event-ref', 'event-envelope-ref', 'replay-hash-ref'], blockers: [] },
              ],
              blockers: [
                { code: 'CONFIGURED_TENANT_CONTEXT_CONTRACT_REQUIRED', owner: 'tenant-context-service', message: 'Configured tenant-context diagnostics are required before live platform coverage can be marked ready.' },
                { code: 'NO_SECRET_DIAGNOSTICS', owner: 'pricing-bff', message: 'Diagnostics show refs and statuses only; credentials, tokens, tenant secrets, and secret transport values are not exposed.' },
              ],
              events: ['TenantPlatformCoverageOpened'],
              fallbackReason: 'Configured tenant-context-service diagnostics are unavailable; this response carries non-secret platform coverage refs and blocked states only.',
            }),
          };
        }

        if (url === '/api/v1/tenants/workspaces') {
          return {
            ok: true,
            status: 201,
            json: async () => ({
              tenantId: 'tenant-test',
              status: 'RECORDED',
              message: 'Tenant workspace setup was recorded in local preview mode.',
              nextStep: 'Connect configured tenant and identity services before production onboarding.',
              placeholders: ['Tenant service contract is not configured in this local response.'],
            }),
          };
        }

        if (url === '/api/v1/products/catalog') {
          return {
            ok: true,
            status: 201,
            json: async () => ({
              productId: 'product-test',
              status: 'RECORDED',
              message: 'Product catalog draft was recorded in local preview mode.',
              nextStep: 'Connect configured product catalog services before production publishing.',
              placeholders: ['Product terms, eligibility, rates, thresholds, and regulatory values are not inferred.'],
            }),
          };
        }

        if (url === '/api/v1/products/catalog/manager') {
          return {
            ok: true,
            json: async () => ({
              tenantContext: 'ui-preview-tenant',
              dependencyStatus: 'CATALOG_CONTRACTS_UNAVAILABLE',
              areas: [
                {
                  areaId: 'draft-products',
                  label: 'Product drafts',
                  sourceRef: 'catalog-service draft metadata',
                  status: 'BLOCKED',
                  guidance: 'Drafts are visible, but configured product draft contracts are required before publish.',
                  fields: ['Product name', 'Product owner', 'Borrower need', 'Version reference'],
                  validationMessages: ['Configured draft schema is required before field validation can be marked verified.'],
                },
                {
                  areaId: 'investors-channels',
                  label: 'Investors, taxonomy, and channels',
                  sourceRef: 'catalog-service domain lists',
                  status: 'BLOCKED',
                  guidance: 'Domain list labels must come from catalog-service; this fallback does not infer investor behavior.',
                  fields: ['Investor label', 'Taxonomy path', 'Channel label'],
                  validationMessages: ['Investor/channel catalog contracts are unavailable in local fallback mode.'],
                },
              ],
              lifecycle: {
                state: 'REVIEW_BLOCKED',
                actionsDisabled: true,
                actions: ['approve', 'publish', 'rollback'],
                snapshotRefs: ['snapshot-catalog-contract-required', 'event-catalog-contract-required'],
                auditRefs: ['audit-ref-required', 'replay-hash-required'],
                blocker: 'Approval, publish, rollback, snapshot, event, and audit actions stay disabled until catalog-service contracts are configured.',
              },
              events: ['CatalogManagerOpened'],
              fallbackReason: 'Configured catalog-service draft, lifecycle, snapshot, event, and audit contracts are unavailable; fallback records non-secret blocked states only.',
              uiTraceId: 'catalog-manager-local-trace',
            }),
          };
        }

        if (url === '/api/v1/quality/dashboard') {
          return {
            ok: true,
            json: async () => ({
              tenantContext: 'ui-preview-tenant',
              dependencyStatus: 'FALLBACK_STATIC_DEPENDENCIES_UNAVAILABLE',
              validationRun: {
                runId: 'validation-run-config-required',
                status: 'BLOCKED',
                loopStatus: 'RED',
                nextAction: 'Block package closure and route unresolved blockers to rework until configured evidence is linked.',
                stages: [
                  { stageId: 'V1', label: 'Preflight', status: 'PASS', timestampLabel: 'timestamp supplied by configured validator' },
                  { stageId: 'V2', label: 'Contract Validation', status: 'FAIL', timestampLabel: 'contract service conformance required' },
                  { stageId: 'V3', label: 'Execution Validation', status: 'PENDING', timestampLabel: 'execution validator unavailable' },
                  { stageId: 'V4', label: 'End-to-End Consistency', status: 'PENDING', timestampLabel: 'pipeline evidence required' },
                  { stageId: 'V5', label: 'Closure Validation', status: 'BLOCKED', timestampLabel: 'blocker evidence required' },
                ],
                openBlockers: [{ blockerId: 'blocker-contract-conformance', severity: 'P1', reasonClass: 'workflow', owner: 'Release engineering', status: 'OPEN', summary: 'Configured contract conformance evidence is missing.' }],
                evidencePaths: ['validation_result.json', 'validation_trace.jsonl', 'module_evidence_index.json', 'blocker_register.json'],
              },
              readiness: {
                readinessStatus: 'fail',
                deploymentDisabled: true,
                blockList: ['P1 contract conformance blocker is open'],
                signoffRefs: ['Quality owner signature required'],
                dependencyChecks: ['smoke check: configured result required'],
                evidenceSetCompleteness: 'missing required evidence references',
              },
              drift: {
                metricFamily: 'pricing-quality',
                window: 'configured analysis window required',
                windowBaseline: 'configured baseline required',
                affectedProducts: ['product set supplied by quality API'],
                cacheStaleness: 'stale',
                lockoutReason: 'Comparison controls are locked until baseline and sample-window evidence are supplied.',
                metrics: [{ metricName: 'contract_failure_rate', severity: 'P2', deviationLabel: 'deviation value supplied by configured metrics' }],
              },
              fairness: {
                protectedClassDimensions: ['masked-class-label'],
                redacted: true,
                sampleCountsLabel: 'sample counts supplied by fairness API',
                breachSeverity: 'P1',
                escalationTarget: 'Risk and compliance owner',
                evidenceRefs: ['fairness-evidence-package-required'],
              },
              incidents: [{ incidentId: 'quality-incident-contract', severity: 'P1', escalationTarget: 'Release engineering', incidentClass: 'contract', playbookRef: 'playbook-required', lifecycleStage: 'mitigating', evidencePackageId: 'evidence-package-required', impactedServices: ['pricing-bff'] }],
              replay: { policySnapshotId: 'policySnapshotId-required', inputBundleRef: 'inputBundleRef-required', deterministicSeedRef: 'deterministicSeed-required', replayAvailable: false, blockedReason: 'Replay is blocked until configured snapshot, seed, and event payload evidence are supplied.', replayModes: ['regression replay', 'deterministic quote replay'] },
              contracts: [{ contractId: 'pricing-bff-ui-quality', status: 'FAIL', summary: 'schema compatibility evidence required', failures: ['quality-dashboard contract pending upstream conformance'] }],
              evidenceExport: { packageId: 'quality-evidence-package-required', completenessStatus: 'INCOMPLETE', redacted: true, evidenceRefs: ['validation_result.json'], blockers: ['Completeness status is incomplete until configured evidence store is available'] },
              uiTraceId: 'ql-s08-local-trace',
              events: ['QualityDashboardOpened'],
              fallbackReason: 'Configured quality services are unavailable; fallback records only.',
            }),
          };
        }

        if (url === '/api/v1/quality/evidence/export') {
          return {
            ok: true,
            json: async () => ({
              packageId: 'quality-evidence-package-required',
              completenessStatus: 'INCOMPLETE',
              redacted: true,
              evidenceRefs: ['validation_result.json', 'validation_trace.jsonl'],
              blockers: ['Export is redacted and incomplete until configured quality evidence storage is available.'],
            }),
          };
        }

        if (url === '/api/v1/ops/cases') {
          return {
            ok: true,
            json: async () => ({
              tenantContext: 'ui-preview-tenant',
              cases: [
                { caseId: 'ops-lock-blocked', priority: 'CRITICAL', ageLabel: 'Age supplied by configured ops-case API', slaState: 'SLA contract required', owner: 'Unassigned', status: 'OPEN', contextSummary: 'Blocked lock workflow requires operations triage.' },
              ],
              uiTraceId: 'ops-s06-local-trace',
              events: ['OpsCaseQueueOpened'],
            }),
          };
        }

        if (url === '/api/v1/ops/cases/ops-lock-blocked') {
          return {
            ok: true,
            json: async () => ({
              caseId: 'ops-lock-blocked',
              priority: 'CRITICAL',
              ageLabel: 'Age supplied by configured ops-case API',
              slaState: 'SLA contract required',
              owner: 'Unassigned',
              status: 'OPEN',
              contextSummary: 'Blocked lock workflow requires operations triage.',
              tenantContext: 'ui-preview-tenant',
              timeline: [{ eventId: 'timeline-opened', eventType: 'OpsCaseOpened', summary: 'Operations case context opened.' }],
              evidencePacketIds: ['evidence-packet-required-after-escalation'],
              uiTraceId: 'ops-s06-local-trace',
              events: ['OpsCaseOpened'],
            }),
          };
        }

        if (url === '/api/v1/ops/cases/ops-lock-blocked/assign') {
          return {
            ok: true,
            json: async () => ({ caseId: 'ops-lock-blocked', owner: 'ops-user', status: 'ASSIGNED', message: 'Ops case assignment recorded by pricing-bff fallback.', uiTraceId: 'ops-s06-local-trace', events: ['OpsCaseAssigned'] }),
          };
        }

        if (url === '/api/v1/ops/cases/ops-lock-blocked/notes') {
          return {
            ok: true,
            json: async () => ({ caseId: 'ops-lock-blocked', status: 'NOTE_RECORDED', message: 'Ops case note recorded by pricing-bff fallback without changing pricing state.', uiTraceId: 'ops-s06-local-trace', events: ['OpsCaseNoteAdded'] }),
          };
        }

        if (url === '/api/v1/ops/cases/ops-lock-blocked/status') {
          return {
            ok: true,
            json: async () => ({ caseId: 'ops-lock-blocked', status: 'ESCALATED', immutableSummary: 'Case ops-lock-blocked transitioned to ESCALATED with original context preserved.', escalationContextPreserved: true, downstreamExecuted: false, uiTraceId: 'ops-s06-local-trace', events: ['OpsCaseEscalated'] }),
          };
        }

        if (url === '/api/v1/ops/rate-feeds') {
          return {
            ok: true,
            json: async () => ({
              tenantContext: 'ui-preview-tenant',
              dependencyStatus: 'RATE_FEED_SERVICE_CONTRACT_NOT_CONFIGURED',
              workflowSteps: [
                { stepId: 'upload', label: 'Upload received', status: 'UPLOAD_READY', sourceBoundary: 'rate-feed-service upload sessions and import endpoints', auditRef: 'batchId-required', resultHashRef: 'raw-file-ref-required' },
                { stepId: 'parse', label: 'Parse and normalize', status: 'PARSE_AND_NORMALIZE_VISIBLE', sourceBoundary: 'rate-feed-service parse-results and normalized-entries endpoints', auditRef: 'parse-job-ref-required', resultHashRef: 'parse-result-hash-required' },
                { stepId: 'validate', label: 'Validation review', status: 'VALIDATION_BLOCKERS_VISIBLE', sourceBoundary: 'rate-feed-service validation-report endpoint', auditRef: 'validation-job-ref-required', resultHashRef: 'validation-result-hash-required' },
                { stepId: 'activate', label: 'Activate or reject', status: 'ACTION_BLOCKED_UNTIL_CONFIGURED_SERVICE', sourceBoundary: 'rate-feed-service publish, rollback, activate, and reject endpoints', auditRef: 'approval-ref-required', resultHashRef: 'activation-audit-ref-required' },
                { stepId: 'replay', label: 'Replay and cache evidence', status: 'EVIDENCE_BLOCKED_UNTIL_CONFIGURED_SERVICE', sourceBoundary: 'rate-feed-service replay and cache-invalidation endpoints', auditRef: 'replay-hash-required', resultHashRef: 'cache-invalidation-command-required' },
              ],
              rowBlockers: [
                { rowRef: 'source-row-12', fieldName: 'noteRate', severity: 'BLOCKER', blockerCode: 'SOURCE_ROW_VALIDATION_REQUIRED', sourceReference: 'source:rate-feed-batch/row/12', resolutionState: 'configured validation report required' },
              ],
              sourceReferences: ['sheet-version-ref-required', 'activation-audit-ref-required', 'partner-submission-ref-required'],
              replayEvidence: ['cache-invalidation-command-required', 'replay-hash-required', 'outbox-event-ref-required'],
              actionsDisabled: true,
              fallbackReason: 'Configured rate-feed-service operations contract is unavailable in this local BFF fallback; UI actions show workflow state and blockers only and do not recalculate rates.',
              uiTraceId: 'rf-s03-local-trace',
              events: ['RateFeedOperationsOpened'],
            }),
          };
        }

        if (url === '/api/v1/ops/performance') {
          return {
            ok: true,
            json: async () => ({
              tenantContext: 'ui-preview-tenant',
              dependencyStatus: 'OBSERVABILITY_SERVICE_CONTRACT_NOT_CONFIGURED',
              signalGroups: [
                {
                  serviceName: 'observability-service',
                  tenantContext: 'ui-preview-tenant',
                  correlationId: 'corr-performance-observability',
                  freshness: 'STALE',
                  signals: [
                    { signalId: 'reference-cache-freshness', label: 'Cache freshness is stale until the configured cache observation read model is linked.', freshness: 'STALE', source: 'reference data cache observation', sourceRef: 'observability-service.cache_observation', evidenceRefs: ['.local-harness/evidence/PII-22-S09/observability-service-test.log'] },
                    { signalId: 'load-test-evidence', label: 'Load-test evidence is unavailable until a project-relative report is supplied.', freshness: 'BLOCKED', source: 'load-test profile', sourceRef: 'observability-service.loadtest', evidenceRefs: ['.local-harness/evidence/PII-22-S09/load-test-report-required.json'] },
                  ],
                  blockers: [{ code: 'LOAD_TEST_EVIDENCE_UNAVAILABLE', owner: 'observability-service', message: 'Project-relative load-test report is required before this dashboard can mark load history fresh.' }],
                },
                {
                  serviceName: 'pricing-bff',
                  tenantContext: 'ui-preview-tenant',
                  correlationId: 'corr-performance-bff',
                  freshness: 'PARTIAL',
                  signals: [{ signalId: 'request-latency', label: 'Latency signal requires configured observability-service metric snapshot.', freshness: 'NO_DATA', source: 'pricing-bff request metrics', sourceRef: 'observability-service.performance_metric_snapshot', evidenceRefs: ['.local-harness/evidence/PII-22-S09/pricing-bff-test.log'] }],
                  blockers: [],
                },
              ],
              impacts: [{ impactCode: 'STALE_CACHE', summary: 'Pricing/reference cache freshness may be stale for operators reviewing quote workflow readiness.', source: 'observability-service', recoveryOwner: 'SRE / Operations Lead', runbookRef: 'runbooks/cache-backpressure' }],
              evidenceLinks: ['.local-harness/evidence/PII-22-S09/observability-service-test.log', '.local-harness/evidence/PII-22-S09/ui-test.log'],
              blockers: [{ code: 'OBSERVABILITY_SERVICE_CONTRACT_NOT_CONFIGURED', owner: 'observability-service', message: 'Live performance metric, cache, and alert read models are not configured at the BFF boundary.' }],
              actionsDisabled: true,
              fallbackReason: 'Configured observability-service performance, cache, alert, and load-test contracts are unavailable; this fallback carries backend-owned refs, freshness, blockers, and recovery ownership only.',
              uiTraceId: 'perf-s09-local-trace',
              events: ['PerformanceDashboardOpened'],
            }),
          };
        }

        if (url === '/api/v1/partners/partner-preview/quotes') {
          return {
            ok: true,
            json: async () => ({
              partnerId: 'partner-preview',
              tenantContext: 'ui-preview-tenant',
              statusFilter: '',
              quotes: [{ quoteId: 'quote-active', borrowerLabel: 'Borrower context available', status: 'ACTIVE', slaState: 'Awaiting configured SLA contract', lockState: 'LOCK_NOT_REQUESTED', errorFlags: [] }],
              uiTraceId: 'ch-s02-local-trace',
              events: ['PartnerQuoteLoaded'],
            }),
          };
        }

        if (url === '/api/v1/partners/partner-preview/quotes/quote-active') {
          return {
            ok: true,
            json: async () => ({
              quoteId: 'quote-active',
              borrowerLabel: 'Borrower context available',
              status: 'ACTIVE',
              slaState: 'Awaiting configured SLA contract',
              lockState: 'LOCK_NOT_REQUESTED',
              errorFlags: [],
              tenantContext: 'ui-preview-tenant',
              partnerId: 'partner-preview',
              lifecycleEvents: ['PartnerQuoteLoaded'],
              actions: { reprice: { visible: true, permitted: true, guidance: 'API permit is true and partner role context is present.', supportHandoffRoute: '/partners/support/reprice' } },
              uiTraceId: 'ch-s02-local-trace',
            }),
          };
        }

        if (url === '/api/v1/partners/partner-preview/integrations/webhooks') {
          return {
            ok: true,
            json: async () => ({
              partnerId: 'partner-preview',
              tenantContext: 'ui-preview-tenant',
              retryHealthSummary: 'RETRY_HEALTH_VISIBLE',
              eventWindow: 'latest 30 events',
              dlqSizeStatus: 'DLQ size requires configured integration-service metrics',
              retryWindowStatus: 'Configured retry window required',
              deliveryAttempts: [
                { webhookId: 'webhook-pricing-updates', eventId: 'event-quote-blocked', route: '/partners/quotes', status: 'FAILED', rootCauseCode: 'UPSTREAM_PARTNER_CONTRACT_NOT_CONFIGURED', lastSuccessfulAt: '2026-06-08T07:15:00Z', failureReason: 'Configured partner webhook transport is unavailable at the BFF boundary.', idempotencyKeyState: 'CONFIRMED_REQUIRED_FOR_REPLAY', maskingIndicator: 'MASKING_INDICATOR_PRESENT', consentIndicator: 'CONSENT_INDICATOR_PRESENT' },
              ],
              safetyToggles: [{ webhookId: 'webhook-lock-alerts', route: '/partners/alerts', paused: true, visibleState: 'Auto-emit is paused for this route in the visible BFF fallback state.' }],
              replayAction: { available: true, disabledReason: 'Replay requires request correlation and explicit idempotency confirmation before it can be recorded.', confirmationRequirement: 'Confirm correlation id and idempotency before replay.', supportHandoffRoute: '/partners/support/webhooks' },
              endpointTestAction: { available: false, disabledReason: 'Endpoint test requires the configured partner webhook transport contract.', confirmationRequirement: 'Confirm endpoint ownership before testing.', supportHandoffRoute: '/partners/support/webhooks' },
              uiTraceId: 'ch-s05-local-trace',
              events: ['WebhookHealthChecked'],
            }),
          };
        }

        if (url.endsWith('/offers')) {
          return {
            ok: true,
            json: async () => ({
              runId: 'run-test',
              status: 'QUOTE_SERVICE_EVIDENCE_VISIBLE',
              offers: [
                {
                  offerId: 'quote-option-contract-required',
                  rank: 1,
                  productLabel: 'Backend-ranked offer',
                  payment: 'payment-ref-required',
                  apr: 'apr-ref-required',
                  confidence: 'score:backend-owned',
                  rankScore: 'rank-score-ref-required',
                  rationaleChips: ['Rank 1 from quote-service ranking response'],
                  scenarioFlags: ['LOCK_PERIOD_REQUIRED', 'FILTER_FACTS_PENDING'],
                  explanationStatus: 'AVAILABLE',
                  sourceScenarioId: 'scenario-ref-required',
                  scenarioVersion: 7,
                  upstreamRefs: ['quote-service.option:quote-option-contract-required', 'eligibility-service:decision-ref-required'],
                  lockEligibilityRefs: ['lock-eligibility:pending:quote-option-contract-required'],
                  snapshotRefs: ['snapshot:quote-service:run:run-test'],
                  auditIds: ['audit:quote-ready-required', 'replay-hash-required'],
                  explanationSections: ['ranking', 'comparison', 'detail'],
                },
              ],
              sortOptions: ['rank', 'score', 'confidence'],
              selectedOfferId: null,
              commitBlocked: false,
              fallbackReason: 'Quote-service offer evidence is represented with backend-owned refs; UI actions stay blocked only when required facts are missing.',
              requiredFacts: ['requestedLockPeriods', 'scenarioVersion', 'filterFacts'],
              backendRefs: ['quote-service.ranking', 'quote-service.explanation', 'quote-service.selection'],
              uiTraceId: 'brw-s02-local-trace',
              events: ['OfferListRendered', 'QuoteServiceEvidenceBound'],
            }),
          };
        }

        if (url.endsWith('/quote-option-contract-required/explain')) {
          return {
            ok: true,
            json: async () => ({
              runId: 'run-test',
              offerId: 'quote-option-contract-required',
              status: 'AVAILABLE',
              rationaleLines: ['quote-service supplied rank, score, warnings, and source refs for this option.'],
              scenarioFlags: ['LOCK_PERIOD_REQUIRED', 'FILTER_FACTS_PENDING'],
              upstreamRefs: ['quote-service.option:quote-option-contract-required'],
              snapshotRefs: ['snapshot:quote-service:run:run-test'],
              auditIds: ['audit:quote-explanation-required', 'replay-hash-required'],
              explanationSections: ['ranking', 'comparison', 'detail', 'selection-handoff'],
              commitBlocked: false,
              message: 'Explanation data is available from backend-owned refs; no UI-side pricing rules are inferred.',
              uiTraceId: 'brw-s02-local-trace',
            }),
          };
        }

        if (url.endsWith('/quote-option-contract-required/select')) {
          return {
            ok: true,
            status: 201,
            json: async () => ({
              runId: 'run-test',
              selectedOfferId: 'quote-option-contract-required',
              status: 'SELECTED',
              nextRoute: '/quote/run-test/lock',
              sourceScenarioId: 'scenario-ref-required',
              scenarioVersion: 7,
              lockEligibilityRef: 'lock-eligibility:pending:quote-option-contract-required',
              snapshotRef: 'snapshot:quote-service:run:run-test',
              auditIds: ['audit:quote-selection-required', 'replay-hash-required'],
              auditRef: 'audit:quote-selection-required',
              message: 'Offer selection recorded with backend-owned refs for lock handoff.',
              uiTraceId: 'brw-s02-local-trace',
              events: ['OfferSelectionRecorded', 'LockEligibilityRefsBound'],
            }),
          };
        }

        if (url.endsWith('/lock')) {
          return {
            ok: true,
            json: async () => ({
              runId: 'run-test',
              selectedOfferId: null,
              status: 'BLOCKED',
              lockDisabled: true,
              blockers: ['Select an offer before requesting a lock.'],
              disclosureText: 'Review lock disclosures after an offer is selected.',
              nextAction: 'Return to offer comparison.',
              uiTraceId: 'brw-s04-local-trace',
              events: ['LockBlocked'],
              dependencyStatus: 'UPSTREAM_LOCK_CONTRACT_NOT_CONFIGURED',
            }),
          };
        }

        if (url.endsWith('/pricing-waterfall')) {
          return {
            ok: true,
            json: async () => ({
              tenantContext: 'ui-preview-tenant',
              runId: 'run-test',
              status: 'BLOCKED',
              restrictedValuesVisible: false,
              dependencyStatus: 'PRICING_SERVICE_WATERFALL_CONTRACT_NOT_CONFIGURED',
              baseSelection: {
                selectionId: 'base-selection-ref-required',
                gridVersionRef: 'grid-version-ref-required',
                selectedNoteRate: { value: null, redacted: true, reason: 'pricing.waterfall.restricted.read permission is required for selected note rate' },
                basePrice: { value: null, redacted: true, reason: 'pricing.waterfall.restricted.read permission is required for base price' },
                ledgerSteps: ['grid-resolution', 'candidate-generation', 'rate-selection'],
              },
              finalPrice: {
                finalPriceId: 'final-price-ref-required',
                roundedFinalPrice: { value: null, redacted: true, reason: 'pricing.waterfall.restricted.read permission is required for rounded final price' },
                ledger: [
                  { ordinal: 1, step: 'BASE_PRICE', inputValue: { value: null, redacted: true, reason: 'pricing.waterfall.restricted.read permission is required for ledger values' }, operation: 'START', outputValue: { value: null, redacted: true, reason: 'pricing.waterfall.restricted.read permission is required for ledger values' }, configRef: 'grid-version-ref-required', reasonCode: 'BASE_RATE_SELECTED', roundingMode: null },
                  { ordinal: 2, step: 'ROUND_FINAL_PRICE', inputValue: { value: null, redacted: true, reason: 'pricing.waterfall.restricted.read permission is required for ledger values' }, operation: 'ROUND', outputValue: { value: null, redacted: true, reason: 'pricing.waterfall.restricted.read permission is required for ledger values' }, configRef: 'rounding-policy-ref-required', reasonCode: 'ROUNDING_TRACE_REQUIRED', roundingMode: 'configured-rounding-mode-required' },
                ],
                adjustmentRefs: ['adjustment-version-refs-required'],
                roundingTraceRefs: ['rounding-policy-ref-required', 'configured-rounding-trace-required'],
              },
              blockers: [{ code: 'PRICING_SERVICE_CONTRACT_REQUIRED', message: 'Pricing-service waterfall evidence must provide base selection and final price ledger.', sourceRef: 'pricing-service.waterfall' }],
              versionRefs: ['grid-version-ref-required', 'adjustment-version-refs-required', 'rounding-policy-ref-required'],
              auditRefs: ['audit:base-selection-required', 'audit:final-price-required'],
              replayHash: 'replay-hash-required',
              versionGraphHash: 'version-graph-hash-required',
              resultHash: 'result-hash-required',
              evidenceHash: 'waterfall-evidence-hash-required',
              uiTraceId: 'pw-s05-local-trace',
              events: ['PricingWaterfallOpened'],
              fallbackReason: 'Configured pricing-service waterfall contract is unavailable; this response exposes non-secret references, redactions, and blockers only.',
            }),
          };
        }

        if (url === '/api/v1/tenants/ui-preview-tenant/quote-runs/intake-metadata') {
          return {
            ok: true,
            json: async () => ({
              tenantContext: 'ui-preview-tenant',
              dependencyStatus: 'SCENARIO_SERVICE_CONTRACT_NOT_CONFIGURED',
              fieldGroups: [
                {
                  groupId: 'scenario-identity',
                  label: 'Scenario identity',
                  helpText: 'Capture identifiers and channel context.',
                  fields: [
                    { fieldId: 'scenarioName', label: 'Scenario name', groupId: 'scenario-identity', dataType: 'text', required: false, helpText: 'Optional local label.', sourceRef: 'scenario-service metadata contract', decisionQuality: 'UNKNOWN', validationMessages: ['Configured scenario-service metadata is required before this field can be marked verified.'] },
                    { fieldId: 'channel', label: 'Channel', groupId: 'scenario-identity', dataType: 'text', required: false, helpText: 'Originating channel.', sourceRef: 'submission-profile contract', decisionQuality: 'UNKNOWN', validationMessages: [] },
                    { fieldId: 'externalLoanId', label: 'External loan id', groupId: 'scenario-identity', dataType: 'text', required: false, helpText: 'Caller-provided loan reference.', sourceRef: 'scenario-service create request', decisionQuality: 'UNKNOWN', validationMessages: [] },
                  ],
                },
                {
                  groupId: 'borrower-loan-property',
                  label: 'Borrower, loan, and property facts',
                  helpText: 'Capture fact fields for downstream validation only.',
                  fields: [
                    { fieldId: 'loanAmount', label: 'Loan amount', groupId: 'borrower-loan-property', dataType: 'number', required: false, helpText: 'Optional requested amount captured as a fact.', sourceRef: 'loan-structure metadata', decisionQuality: 'UNKNOWN', validationMessages: [] },
                    { fieldId: 'propertyState', label: 'Property state', groupId: 'borrower-loan-property', dataType: 'text', required: false, helpText: 'State reference for configured downstream validation.', sourceRef: 'property metadata', decisionQuality: 'UNKNOWN', validationMessages: [] },
                  ],
                },
              ],
              decisionControls: ['Disable quote progression when required backend facts are missing.', 'Keep pricing calculations outside the workbench intake surface.'],
              validationIssues: [{ code: 'SCENARIO_SERVICE_CONTRACT_REQUIRED', fieldPath: 'scenarioService', severity: 'BLOCKING', message: 'Scenario-service metadata must be configured before downstream quote decisions can mutate.' }],
              auditPackageId: 'audit-package-required-after-scenario-service-create',
              replayHashRef: 'replay-hash-required-after-scenario-service-create',
              fallbackReason: 'Configured scenario-service metadata is unavailable; fallback records only.',
              uiTraceId: 'brw-s01-local-trace',
            }),
          };
        }

        return {
          ok: true,
          status: 201,
          json: async () => ({
            runId: 'run-test',
            status: 'CREATED',
            nextRoute: '/quote/run-test/offers',
            validationSummary: { passed: true, status: 'PASSED', message: 'Required borrower intake fields are present.', blockers: {} },
            uiTraceId: 'brw-s01-local-trace',
            events: ['UIFlowOpened', 'ScenarioMetadataReviewed', 'BorrowerIntakeSubmitted'],
            fallbackMode: false,
            dependencyStatus: 'SCENARIO_SERVICE_CONTRACT_NOT_CONFIGURED',
            auditPackageId: 'audit-package-required-after-scenario-service-create',
            replayHashRef: 'replay-hash-required-after-scenario-service-create',
            validationIssues: [],
          }),
        };
      }),
    );
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  function primaryRenderedText() {
    const copy = document.body.cloneNode(true) as HTMLElement;
    copy.querySelectorAll('details, script, style').forEach((node) => node.remove());
    return copy.textContent ?? '';
  }

  const forbiddenPrimaryJargon = /Support reference|SLA contract required|Connected services|dependencyStatus|Downstream executed|Partner Transport|policy versions|market-rule completeness|release gates|\bdrift\b|override ledger|RBAC|DSAR|DLQ|\bBFF\b|\bupstream\b|route labels/i;

  it('renders accessible full workflow and calls the workbench service health boundary on load', async () => {
    render(<App />);

    expect(screen.getByText('Skip to main content')).toHaveAttribute('href', '#main-content');
    expect(screen.getByRole('banner')).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Main navigation' })).toBeInTheDocument();
    expect(screen.getByRole('main')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Tenant onboarding' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Product management' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Borrower quote details' })).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: /Borrower name/i })).toHaveAttribute('aria-invalid', 'false');

    await waitFor(() => expect(screen.getByText('Workbench service reachable')).toBeInTheDocument());
    expect(fetch).toHaveBeenCalledTimes(1);
    expect(fetch).toHaveBeenCalledWith('/api/ui/health', { headers: { Accept: 'application/json' } });
  });

  it('renders modular screen package boundaries, states, and evidence targets from the route registry', async () => {
    render(<App />);

    expect(screen.getByRole('heading', { name: 'Modular route shell' })).toBeInTheDocument();
    const modules = screen.getByRole('list', { name: 'Workbench screen modules' });
    expect(within(modules).getByText('screens/workbenchShell')).toBeInTheDocument();
    expect(within(modules).getByText('lib/api/offers')).toBeInTheDocument();
    expect(within(modules).getByText('.local-harness/evidence/PII-22-S21/custom-rules.json')).toBeInTheDocument();
    expect(within(modules).getAllByText('blocked').length).toBeGreaterThan(0);

    await waitFor(() => expect(screen.getByText('Workbench service reachable')).toBeInTheDocument());
  });

  it('marks the active modular route in navigation without breaking existing routed screens', async () => {
    window.history.pushState({}, '', '/custom-rules/evidence');

    render(<App />);

    expect(screen.getByRole('link', { name: 'Custom rule evidence' })).toHaveAttribute('aria-current', 'page');
    expect(await screen.findByRole('heading', { name: 'Custom field and calculation evidence' })).toBeInTheDocument();
  });

  it('renders product catalog manager sections, lifecycle blockers, snapshots, and audit evidence from the BFF boundary', async () => {
    window.history.pushState({}, '', '/admin/products/catalog');

    render(<App />);

    expect(screen.getByRole('link', { name: 'Product catalog manager' })).toHaveAttribute('aria-current', 'page');
    expect(await screen.findByRole('heading', { name: 'Catalog sections and validation' })).toBeInTheDocument();
    expect(screen.getByRole('list', { name: 'Product catalog manager sections' })).toBeInTheDocument();
    expect(screen.getByText('Product drafts')).toBeInTheDocument();
    expect(screen.getByText('Investors, taxonomy, and channels')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Approval, publish, rollback, snapshots, and audit' })).toBeInTheDocument();
    expect(screen.getByText('snapshot-catalog-setup-required')).toBeInTheDocument();
    expect(screen.getByText('replay-hash-required')).toBeInTheDocument();
    expect(screen.getByText(/actions stay disabled/)).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith('/api/v1/products/catalog/manager', expect.objectContaining({ headers: expect.objectContaining({ 'X-Ui-Trace-Id': 'catalog-manager-local-trace' }) }));
    expect(document.body.textContent).not.toMatch(/rate table|eligibility threshold|fee amount/i);
  });

  it('records tenant setup and product management drafts through relative workbench endpoints', async () => {
    render(<App />);

    fireEvent.change(screen.getByRole('textbox', { name: /Workspace name/i }), { target: { value: 'Retail lending workspace' } });
    fireEvent.change(screen.getByRole('textbox', { name: /Operations contact/i }), { target: { value: 'ops@example.test' } });
    fireEvent.change(screen.getByRole('textbox', { name: /Launch goal/i }), { target: { value: 'Prepare assisted pricing workbench launch' } });
    fireEvent.click(screen.getByRole('button', { name: 'Create tenant workspace' }));

    expect(await screen.findByText('Tenant workspace recorded')).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith('/api/v1/tenants/workspaces', expect.objectContaining({ method: 'POST' }));

    fireEvent.change(screen.getByRole('textbox', { name: /Product name/i }), { target: { value: 'Standard purchase draft' } });
    fireEvent.change(screen.getByRole('textbox', { name: /Product owner/i }), { target: { value: 'Product manager' } });
    fireEvent.change(screen.getByRole('textbox', { name: /Borrower need served/i }), { target: { value: 'Compare purchase loan options without entering pricing rules' } });
    fireEvent.click(screen.getByRole('button', { name: 'Add product draft' }));

    expect(await screen.findByText('Product draft recorded')).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith('/api/v1/products/catalog', expect.objectContaining({ method: 'POST' }));
  });

  it('keeps primary start-screen forms, banners, and navigation free of implementation jargon', () => {
    render(<App />);

    expect(primaryRenderedText()).not.toMatch(forbiddenPrimaryJargon);
  });

  it.each([
    ['/quote/run-test/offers', 'Offer comparison'],
    ['/quote/run-test/pricing-waterfall', 'Waterfall evidence'],
    ['/quote/run-test/lock', 'Lock workflow'],
      ['/ops/dashboard', 'Operations case triage'],
      ['/partners/quotes', 'Partner quote lifecycle'],
      ['/partners/webhooks', 'Partner connection reliability'],
      ['/ops/performance', 'Service performance cockpit'],
      ['/ops/rate-feeds', 'Rate feed operations'],
    ['/compliance/evidence', 'Compliance evidence registry'],
      ['/quality/validation', 'Quality guardrails dashboard'],
      ['/admin/products/catalog', 'Product catalog manager'],
      ['/admin/governance', 'Admin governance and readiness controls'],
      ['/platform/tenant-context', 'Tenant platform coverage'],
      ['/audit/replay', 'Audit replay evidence workbench'],
    ])('keeps primary rendered copy free of implementation jargon on %s', async (path, heading) => {
    window.history.pushState({}, '', path);
    render(<App />);

    expect(await screen.findByRole('heading', { name: heading })).toBeInTheDocument();
    await waitFor(() => expect(primaryRenderedText()).not.toMatch(/Loading/i));
    expect(primaryRenderedText()).not.toMatch(forbiddenPrimaryJargon);
  });

  it('renders custom field metadata, decision blockers, and backend rule evidence without local pricing math', async () => {
    window.history.pushState({}, '', '/custom-rules/evidence');

    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Custom field and calculation evidence' })).toBeInTheDocument();
    expect(await screen.findByRole('table', { name: 'Custom field metadata' })).toBeInTheDocument();
    expect(screen.getByText('Evidence source')).toBeInTheDocument();
    expect(screen.getAllByText('UNKNOWN').length).toBeGreaterThan(0);
    expect(screen.getAllByText('CONFLICTING').length).toBeGreaterThan(0);
    expect(screen.getByRole('button', { name: 'Commit rule-backed calculation' })).toBeDisabled();
    expect(screen.getByRole('table', { name: 'Matched rules' })).toBeInTheDocument();
    expect(screen.getByRole('table', { name: 'Skipped rules' })).toBeInTheDocument();
    expect(screen.getByText('replay-hash-ref-required')).toBeInTheDocument();
    expect(screen.getByText(/External screenshot\/PDF evidence is unavailable/)).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith('/api/v1/custom-rules/evidence', expect.objectContaining({ headers: expect.objectContaining({ 'X-Ui-Trace-Id': 'cr-s01-local-trace' }) }));
  });

  it('renders governance descriptors, pending review impact, and dynamic rule evidence on /admin/governance', async () => {
    window.history.pushState({}, '', '/admin/governance');

    render(<App />);

    expect(screen.getByRole('link', { name: 'Governance lifecycle' })).toHaveAttribute('aria-current', 'page');
    expect(await screen.findByRole('table', { name: 'Governance descriptors' })).toBeInTheDocument();
    expect(screen.getByText('config-lifecycle')).toBeInTheDocument();
    expect(screen.getByText('simulate')).toBeInTheDocument();
    expect(screen.getByText('governance-service.config-lifecycle')).toBeInTheDocument();
    expect(screen.getByText('config-lifecycle-version-ref-required')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Pending config review impact' })).toBeInTheDocument();
    expect(screen.getByText('PCR-config-lifecycle-required · PENDING REVIEW')).toBeInTheDocument();
    expect(screen.getByText('pricing-workbench-ui')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Dynamic rule evidence' })).toBeInTheDocument();
    expect(screen.getByRole('table', { name: 'Governance matched rules' })).toBeInTheDocument();
    expect(screen.getByRole('table', { name: 'Governance skipped rules' })).toBeInTheDocument();
    expect(screen.getByText('precision-metadata-ref-required')).toBeInTheDocument();
    expect(screen.getByText('replay-hash-ref-required')).toBeInTheDocument();
    expect(document.body.textContent).not.toMatch(/rate table|eligibility threshold|fee amount/i);
  });

  it('renders audit replay records, replay diffs, retention, redaction, and export blockers', async () => {
    window.history.pushState({}, '', '/audit/replay');

    render(<App />);

    expect(screen.getByRole('link', { name: 'Audit replay evidence' })).toHaveAttribute('aria-current', 'page');
    expect(await screen.findByRole('heading', { name: 'Audit record search results' })).toBeInTheDocument();
    const recordsTable = screen.getByRole('table', { name: 'Audit replay records' });
    expect(recordsTable).toBeInTheDocument();
    expect(screen.getByText('event-id-required')).toBeInTheDocument();
    expect(screen.getAllByText('INTEGRITY PENDING').length).toBeGreaterThan(0);
    expect(screen.getByText('redaction-profile-required')).toBeInTheDocument();
    expect(recordsTable).toHaveTextContent(/Legal hold:\s*active/i);
    expect(recordsTable).toHaveTextContent('EXPORT LOCKED BY LEGAL HOLD');
    expect(screen.getByText('replay-hash-required')).toBeInTheDocument();
    expect(screen.getByText('quote-service replay dependency is unavailable')).toBeInTheDocument();
    expect(screen.getByText('manifest-hash-required')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Download evidence export' })).toBeDisabled();
    expect(fetch).toHaveBeenCalledWith('/api/v1/audit-replay/workbench', expect.objectContaining({ headers: expect.objectContaining({ 'X-Ui-Trace-Id': 'ar-s10-local-trace' }) }));
  });

  it('renders tenant platform coverage with trace refs, blockers, and no secrets', async () => {
    window.history.pushState({}, '', '/platform/tenant-context');

    render(<App />);

    expect(screen.getByRole('link', { name: 'Tenant platform coverage' })).toHaveAttribute('aria-current', 'page');
    expect(await screen.findByRole('heading', { name: 'Propagation and readiness evidence' })).toBeInTheDocument();
    expect(screen.getByText('correlation-id-required')).toBeInTheDocument();
    expect(screen.getByText('idempotency-key-required')).toBeInTheDocument();
    expect(screen.getByRole('list', { name: 'Tenant platform controls' })).toBeInTheDocument();
    expect(screen.getByText('Tenant resolution')).toBeInTheDocument();
    expect(screen.getByText('Rate limiting guard')).toBeInTheDocument();
    expect(screen.getByText('outbox-event-ref')).toBeInTheDocument();
    expect(screen.getByRole('list', { name: 'Tenant platform blockers' })).toBeInTheDocument();
    expect(screen.getByText(/credentials, tokens, tenant secrets/)).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith('/api/v1/platform/tenant-context', expect.objectContaining({ headers: expect.objectContaining({ 'X-Ui-Trace-Id': 'tc-s08-local-trace' }) }));
    expect(document.body.textContent).not.toMatch(/password|access token|connection string/i);
  });

  it('renders service performance dashboard grouped by service, workspace, correlation id, and freshness', async () => {
    window.history.pushState({}, '', '/ops/performance');

    render(<App />);

    expect(screen.getByRole('link', { name: 'Performance dashboard' })).toHaveAttribute('aria-current', 'page');
    expect(await screen.findByRole('heading', { name: 'Performance, cache, and freshness signals' })).toBeInTheDocument();
    expect(screen.getByRole('list', { name: 'Performance signal groups' })).toBeInTheDocument();
    expect(screen.getAllByText('observability-service').length).toBeGreaterThan(0);
    expect(screen.getByText('corr-performance-observability')).toBeInTheDocument();
    expect(screen.getByText('STALE')).toBeInTheDocument();
    expect(screen.getByText(/Load-test evidence is unavailable/)).toBeInTheDocument();
    expect(screen.getByText('SRE / Operations Lead')).toBeInTheDocument();
    expect(screen.getByText('.local-harness/evidence/PII-22-S09/ui-test.log')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Change runtime configuration' })).toBeDisabled();
    expect(fetch).toHaveBeenCalledWith('/api/v1/ops/performance', expect.objectContaining({ headers: expect.objectContaining({ 'X-Ui-Trace-Id': 'perf-s09-local-trace' }) }));
  });

  it('keeps invalid borrower intake on /quote/start and focuses the first invalid field', async () => {
    render(<App />);

    fireEvent.click(screen.getByRole('button', { name: 'Start quick quote' }));

    expect(await screen.findByText('Complete the highlighted required fields.')).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: /Borrower name/i })).toHaveFocus();
    expect(screen.getByText('Borrower name is required.')).toHaveAttribute('role', 'alert');
    expect(fetch).toHaveBeenCalledTimes(1);
  });

  it('launches a valid borrower quote run through the relative pricing-bff boundary', async () => {
    render(<App />);

    fireEvent.change(screen.getByRole('textbox', { name: /Borrower name/i }), { target: { value: 'Alex Borrower' } });
    fireEvent.change(screen.getByRole('textbox', { name: /Contact email/i }), { target: { value: 'alex@example.test' } });
    fireEvent.change(screen.getByRole('textbox', { name: /Quote goal/i }), { target: { value: 'Compare available offers' } });
    fireEvent.click(screen.getByRole('button', { name: 'Start quick quote' }));

    expect(await screen.findByRole('heading', { name: 'Offer comparison' })).toBeInTheDocument();
    expect(window.location.pathname).toBe('/quote/run-test/offers');
    expect(fetch).toHaveBeenCalledWith('/api/v1/tenants/ui-preview-tenant/quote-runs', expect.objectContaining({ method: 'POST' }));
  });

  it('renders scenario intake metadata, advanced facts, blockers, audit id, and replay hash without local pricing math', async () => {
    render(<App />);

    expect(await screen.findByText('Advanced scenario facts from backend metadata')).toBeInTheDocument();
    expect(screen.getByLabelText('Scenario intake metadata evidence')).toBeInTheDocument();
    expect(screen.getByText('Scenario identity')).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: /Scenario name/i })).toBeInTheDocument();
    expect(screen.getByRole('spinbutton', { name: /Loan amount/i })).toBeInTheDocument();
    expect(screen.getByText('audit-package-required-after-scenario-service-create')).toBeInTheDocument();
    expect(screen.getByText('replay-hash-required-after-scenario-service-create')).toBeInTheDocument();
    expect(screen.getByText(/Scenario-service metadata must be configured/)).toBeInTheDocument();

    fireEvent.change(screen.getByRole('textbox', { name: /Scenario name/i }), { target: { value: 'Purchase comparison intake' } });
    fireEvent.change(screen.getByRole('spinbutton', { name: /Loan amount/i }), { target: { value: '425000' } });
    expect(screen.getByDisplayValue('Purchase comparison intake')).toBeInTheDocument();
    expect(screen.getByDisplayValue('425000')).toBeInTheDocument();
    expect(document.body.textContent).not.toMatch(/APR|rate table|eligibility threshold/i);
  });

  it('shows retry guidance and support reference when the BFF launch boundary is unavailable', async () => {
    vi.mocked(fetch).mockImplementation(async (input: RequestInfo | URL) => {
      if (input.toString() === '/api/ui/health') {
        return { ok: true, json: async () => ({ service: 'pricing-bff', status: 'UP', ready: true, dependencyStatus: 'NO_UPSTREAMS_CONFIGURED', dependencies: [] }) } as Response;
      }
      throw new Error('BFF borrower intake boundary is temporarily unavailable.');
    });

    render(<App />);

    fireEvent.change(screen.getByRole('textbox', { name: /Borrower name/i }), { target: { value: 'Alex Borrower' } });
    fireEvent.change(screen.getByRole('textbox', { name: /Contact email/i }), { target: { value: 'alex@example.test' } });
    fireEvent.change(screen.getByRole('textbox', { name: /Quote goal/i }), { target: { value: 'Compare available offers' } });
    fireEvent.click(screen.getByRole('button', { name: 'Start quick quote' }));

    expect(await screen.findByText('Service outage fallback')).toBeInTheDocument();
    expect(screen.getByText(/Support reference: brw-s01-local-trace/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Retry intake' })).toBeInTheDocument();
  });

  it('renders operations cases with SLA and owner fields from the BFF boundary', async () => {
    window.history.pushState({}, '', '/ops/dashboard');

    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Operations case triage' })).toBeInTheDocument();
    const queue = screen.getByRole('table', { name: 'Operations cases' });
    expect(within(queue).getByText(/ops-lock-blocked/)).toBeInTheDocument();
    expect(within(queue).getByText('Response target needs setup')).toBeInTheDocument();
    expect(within(queue).getByText('Unassigned')).toBeInTheDocument();
    expect(await screen.findByText('Operations case context opened.')).toBeInTheDocument();
    expect(screen.getByText('evidence-packet-required-after-escalation')).toBeInTheDocument();
  });

  it('keeps /ops/escalations inside the operations triage route family', async () => {
    window.history.pushState({}, '', '/ops/escalations');

    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Operations case triage' })).toBeInTheDocument();
    expect(screen.getByRole('table', { name: 'Operations cases' })).toBeInTheDocument();
  });

  it('renders rate feed workflow steps, row blockers, source refs, and replay evidence without local rate math', async () => {
    window.history.pushState({}, '', '/ops/rate-feeds');

    render(<App />);

    expect(screen.getByRole('link', { name: 'Rate feed operations' })).toHaveAttribute('aria-current', 'page');
    expect(await screen.findByRole('heading', { name: 'Upload-to-replay workflow' })).toBeInTheDocument();
    expect(screen.getByRole('list', { name: 'Rate feed workflow steps' })).toBeInTheDocument();
    expect(screen.getByText('Upload received')).toBeInTheDocument();
    expect(screen.getByText('Parse and normalize')).toBeInTheDocument();
    expect(screen.getByText('Validation review')).toBeInTheDocument();
    expect(screen.getByText('Activate or reject')).toBeInTheDocument();
    expect(screen.getByText('Replay and cache evidence')).toBeInTheDocument();
    expect(screen.getByRole('table', { name: 'Rate grid blockers' })).toBeInTheDocument();
    expect(screen.getByText('SOURCE ROW VALIDATION REQUIRED')).toBeInTheDocument();
    expect(screen.getByText('source:rate-feed-batch/row/12')).toBeInTheDocument();
    expect(screen.getAllByText('activation-audit-ref-required').length).toBeGreaterThan(0);
    expect(screen.getAllByText('cache-invalidation-command-required').length).toBeGreaterThan(0);
    expect(screen.getByRole('button', { name: 'Publish rate sheet' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Request replay' })).toBeDisabled();
    expect(document.body.textContent).not.toMatch(/APR|eligibility threshold|fee amount/i);
    expect(fetch).toHaveBeenCalledWith('/api/v1/ops/rate-feeds', expect.objectContaining({ headers: expect.objectContaining({ 'X-Ui-Trace-Id': 'rf-s03-local-trace' }) }));
  });

  it('renders pricing waterfall ledger, redactions, blockers, audit refs, and replay hashes without local pricing math', async () => {
    window.history.pushState({}, '', '/quote/run-test/pricing-waterfall');

    render(<App />);

    expect(screen.getByRole('link', { name: 'Pricing waterfall' })).toHaveAttribute('aria-current', 'page');
    expect(await screen.findByRole('heading', { name: 'Waterfall evidence' })).toBeInTheDocument();
    expect(screen.getByRole('table', { name: 'Pricing waterfall ledger' })).toBeInTheDocument();
    expect(screen.getByText('grid-version-ref-required')).toBeInTheDocument();
    expect(screen.getAllByText(/Redacted:/).length).toBeGreaterThan(0);
    expect(screen.getByText('PRICING SERVICE CONTRACT REQUIRED')).toBeInTheDocument();
    expect(screen.getByText('audit:final-price-required')).toBeInTheDocument();
    expect(screen.getByText('replay-hash-required')).toBeInTheDocument();
    expect(screen.getByText('waterfall-evidence-hash-required')).toBeInTheDocument();
    expect(document.body.textContent).not.toMatch(/rate table|eligibility threshold|fee amount/i);
    expect(fetch).toHaveBeenCalledWith('/api/v1/tenants/ui-preview-tenant/quote-runs/run-test/pricing-waterfall', expect.objectContaining({ headers: expect.objectContaining({ 'X-Ui-Trace-Id': 'pw-s05-local-trace' }) }));
  });

  it('gates operations escalation and resolution controls on explicit text inputs', async () => {
    window.history.pushState({}, '', '/ops/cases/ops-lock-blocked');

    render(<App />);

    expect(await screen.findByRole('button', { name: 'Assign case' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Escalate with reason' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Resolve case' })).toBeDisabled();

    fireEvent.change(screen.getByRole('textbox', { name: 'Assign owner' }), { target: { value: 'ops-user' } });
    fireEvent.click(screen.getByRole('button', { name: 'Assign case' }));
    expect(await screen.findByText('Ops case assignment recorded by pricing-bff fallback.')).toBeInTheDocument();

    fireEvent.change(screen.getByRole('textbox', { name: 'Add case note' }), { target: { value: 'Borrower callback captured in operations queue' } });
    fireEvent.click(screen.getByRole('button', { name: 'Add note' }));
    expect(await screen.findByText('Ops case note recorded by pricing-bff fallback without changing pricing state.')).toBeInTheDocument();

    fireEvent.change(screen.getByRole('textbox', { name: 'Escalation reason' }), { target: { value: 'Borrower lock blocker still unresolved' } });
    fireEvent.click(screen.getByRole('button', { name: 'Escalate with reason' }));
    expect(await screen.findByText('Connected workflow run: no')).toBeInTheDocument();
  });

  it('submits a resolution code and renders immutable closure summary', async () => {
    window.history.pushState({}, '', '/ops/cases/ops-lock-blocked');
    vi.mocked(fetch).mockImplementation(async (input: RequestInfo | URL) => {
      const url = input.toString();
      if (url === '/api/ui/health') {
        return { ok: true, json: async () => ({ service: 'pricing-bff', status: 'UP', ready: true, dependencyStatus: 'NO_UPSTREAMS_CONFIGURED', dependencies: [] }) } as Response;
      }
      if (url === '/api/v1/ops/cases') {
        return { ok: true, json: async () => ({ tenantContext: 'ui-preview-tenant', cases: [{ caseId: 'ops-lock-blocked', priority: 'CRITICAL', ageLabel: 'Age supplied by configured ops-case API', slaState: 'SLA contract required', owner: 'Unassigned', status: 'OPEN', contextSummary: 'Blocked lock workflow requires operations triage.' }], uiTraceId: 'ops-s06-local-trace', events: ['OpsCaseQueueOpened'] }) } as Response;
      }
      if (url === '/api/v1/ops/cases/ops-lock-blocked') {
        return { ok: true, json: async () => ({ caseId: 'ops-lock-blocked', priority: 'CRITICAL', ageLabel: 'Age supplied by configured ops-case API', slaState: 'SLA contract required', owner: 'Unassigned', status: 'OPEN', contextSummary: 'Blocked lock workflow requires operations triage.', tenantContext: 'ui-preview-tenant', timeline: [{ eventId: 'timeline-opened', eventType: 'OpsCaseOpened', summary: 'Operations case context opened.' }], evidencePacketIds: ['evidence-packet-required-after-escalation'], uiTraceId: 'ops-s06-local-trace', events: ['OpsCaseOpened'] }) } as Response;
      }
      if (url === '/api/v1/ops/cases/ops-lock-blocked/status') {
        return { ok: true, json: async () => ({ caseId: 'ops-lock-blocked', status: 'RESOLVED', immutableSummary: 'Case ops-lock-blocked closed with resolution code OPS_CONFIRMED.', escalationContextPreserved: false, downstreamExecuted: false, uiTraceId: 'ops-s06-local-trace', events: ['OpsCaseResolved'] }) } as Response;
      }
      throw new Error(`Unexpected fetch ${url}`);
    });

    render(<App />);

    expect(await screen.findByRole('button', { name: 'Resolve case' })).toBeDisabled();
    fireEvent.change(screen.getByRole('textbox', { name: 'Resolution code' }), { target: { value: 'OPS_CONFIRMED' } });
    fireEvent.click(screen.getByRole('button', { name: 'Resolve case' }));
    expect(await screen.findByText('Case ops-lock-blocked closed with resolution code OPS_CONFIRMED.')).toBeInTheDocument();
  });

  it('renders backend-owned ranking refs and selection evidence for offers', async () => {
    window.history.pushState({}, '', '/quote/run-test/offers');

    render(<App />);

    expect(await screen.findByText('Backend-ranked offer')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Offer comparison' })).toBeInTheDocument();
    expect(screen.getByText('rank-score-ref-required')).toBeInTheDocument();
    expect(screen.getByText('lock-eligibility:pending:quote-option-contract-required')).toBeInTheDocument();
    expect(screen.getByText('snapshot:quote-service:run:run-test')).toBeInTheDocument();
    expect(screen.getByText('audit:quote-ready-required')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Inspect explanation' }));
    expect(await screen.findByText(/quote-service supplied rank, score, warnings/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Continue to lock workflow' }));
    await waitFor(() => expect(window.location.pathname).toBe('/quote/run-test/lock'));
    expect(sessionStorage.getItem('wcpe:selectedOfferId:run-test')).toBe('quote-option-contract-required');
    expect(fetch).toHaveBeenCalledWith('/api/v1/tenants/ui-preview-tenant/quote-runs/run-test/offers', expect.any(Object));
  });

  it('keeps offer order stable, exposes explanation in place, and persists selectedOfferId for lock workflow', async () => {
    window.history.pushState({}, '', '/quote/run-test/offers');
    vi.mocked(fetch).mockImplementation(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = input.toString();
      if (url === '/api/ui/health') {
        return { ok: true, json: async () => ({ service: 'pricing-bff', status: 'UP', ready: true, dependencyStatus: 'NO_UPSTREAMS_CONFIGURED', dependencies: [] }) } as Response;
      }
      if (url.endsWith('/offers')) {
        return {
          ok: true,
          json: async () => ({
            runId: 'run-test',
            status: 'READY',
            offers: [
              { offerId: 'offer-b', rank: 2, productLabel: 'Offer B', payment: '2000', apr: '6.5', confidence: 'medium', rankScore: '0.80', rationaleChips: ['BFF supplied'], scenarioFlags: ['scenario-linked'], explanationStatus: 'AVAILABLE', sourceScenarioId: 'scenario-test', scenarioVersion: 2, upstreamRefs: ['quote-service.option:offer-b'], lockEligibilityRefs: ['lock-eligibility:pending:offer-b'], snapshotRefs: ['snapshot-b'], auditIds: ['audit-b'], explanationSections: ['ranking'] },
              { offerId: 'offer-a', rank: 1, productLabel: 'Offer A', payment: '1000', apr: '6.0', confidence: 'high', rankScore: '0.95', rationaleChips: ['BFF supplied'], scenarioFlags: [], explanationStatus: 'AVAILABLE', sourceScenarioId: 'scenario-test', scenarioVersion: 2, upstreamRefs: ['quote-service.option:offer-a'], lockEligibilityRefs: ['lock-eligibility:pending:offer-a'], snapshotRefs: ['snapshot-a'], auditIds: ['audit-a'], explanationSections: ['ranking', 'selection'] },
            ],
            sortOptions: ['payment', 'apr', 'confidence'],
            selectedOfferId: null,
            commitBlocked: false,
            fallbackReason: null,
            uiTraceId: 'brw-s02-local-trace',
            events: ['OfferListRendered'],
          }),
        } as Response;
      }
      if (url.endsWith('/offer-a/explain')) {
        return { ok: true, json: async () => ({ runId: 'run-test', offerId: 'offer-a', status: 'AVAILABLE', rationaleLines: ['Rationale supplied by BFF'], scenarioFlags: [], upstreamRefs: ['quote-service.option:offer-a'], snapshotRefs: ['snapshot-a'], auditIds: ['audit-a'], explanationSections: ['ranking'], commitBlocked: false, message: '', uiTraceId: 'brw-s02-local-trace' }) } as Response;
      }
      if (url.endsWith('/offer-a/select') && init?.method === 'POST') {
        return { ok: true, json: async () => ({ runId: 'run-test', selectedOfferId: 'offer-a', status: 'SELECTED', nextRoute: '/quote/run-test/lock', sourceScenarioId: 'scenario-test', scenarioVersion: 2, lockEligibilityRef: 'lock-eligibility:pending:offer-a', snapshotRef: 'snapshot-a', auditIds: ['audit-a'], auditRef: 'audit-test', message: 'selected', uiTraceId: 'brw-s02-local-trace', events: ['OfferSelectionMade'] }) } as Response;
      }
      throw new Error(`Unexpected fetch ${url}`);
    });

    render(<App />);

    const offerList = await screen.findByRole('list', { name: 'Comparable offers' });
    const cards = within(offerList).getAllByRole('listitem');
    expect(cards[0]).toHaveAccessibleName(/offer-a/i);
    fireEvent.click(screen.getAllByRole('button', { name: 'Inspect explanation' })[0]);
    expect(await screen.findByText('Rationale supplied by BFF')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Continue to lock workflow' }));

    await waitFor(() => expect(window.location.pathname).toBe('/quote/run-test/lock'));
    expect(sessionStorage.getItem('wcpe:selectedOfferId:run-test')).toBe('offer-a');
  });

  it('disables lock confirmation and shows blockers when preconditions are incomplete', async () => {
    window.history.pushState({}, '', '/quote/run-test/lock');

    render(<App />);

    expect(await screen.findByText('Lock is blocked')).toBeInTheDocument();
    expect(screen.getByText('Select an offer before requesting a lock.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Confirm lock' })).toBeDisabled();
    expect(fetch).toHaveBeenCalledWith('/api/v1/tenants/ui-preview-tenant/quote-runs/run-test/lock', expect.any(Object));
  });

  it('confirms a valid lock and returns lock details in one step', async () => {
    window.history.pushState({}, '', '/quote/run-test/lock');
    sessionStorage.setItem('wcpe:selectedOfferId:run-test', 'offer-a');
    vi.mocked(fetch).mockImplementation(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = input.toString();
      if (url === '/api/ui/health') {
        return { ok: true, json: async () => ({ service: 'pricing-bff', status: 'UP', ready: true, dependencyStatus: 'NO_UPSTREAMS_CONFIGURED', dependencies: [] }) } as Response;
      }
      if (url.endsWith('/lock?selectedOfferId=offer-a')) {
        return { ok: true, json: async () => ({
          runId: 'run-test',
          selectedOfferId: 'offer-a',
          status: 'READY',
          lockDisabled: false,
          blockers: [],
          blockerDetails: [],
          disclosureText: 'Confirm lock disclosure.',
          nextAction: 'Confirm lock request',
          uiTraceId: 'brw-s04-local-trace',
          events: ['LockAttempted'],
          dependencyStatus: 'UPSTREAM_LOCK_CONTRACT_NOT_CONFIGURED',
          selectedQuoteRefs: ['quote-run:run-test', 'selected-offer:offer-a', 'lock-eligibility:pending:offer-a'],
          freshnessChecks: [{ label: 'Quote freshness', status: 'PENDING_CONFIGURED_SERVICE', sourceRef: 'lock-service:freshness-check', remediation: 'Lock-service must return the authoritative freshness decision before live submission.' }],
          requiredEvidence: ['selected-offer-ref', 'freshness-check-id', 'pricing-result-hash'],
          stateTransitions: [{ fromState: 'OFFER_SELECTED', toState: 'READY_FOR_LOCK_REQUEST', eventId: 'lock.lifecycle.ready.offer-a', status: 'VISIBLE' }],
          auditGroups: [{ eventId: 'lock.confirmation.offer-a', label: 'Confirmation', evidenceRefs: ['audit:lock-confirmation:run-test'], replayHash: 'replay:lock-confirmation:offer-a', exportRef: 'export:lock-confirmation:run-test' }],
        }) } as Response;
      }
      if (url.endsWith('/lock/confirm') && init?.method === 'POST') {
        return { ok: true, status: 201, json: async () => ({ runId: 'run-test', selectedOfferId: 'offer-a', status: 'CONFIRMED', lockId: 'lock-test', lockStatus: 'LOCK_REQUEST_RECORDED', expiresAt: 'Pending configured lock-service response', statusRoute: '/quote/run-test/status', message: 'Lock request recorded for selected offer offer-a.', uiTraceId: 'brw-s04-local-trace', events: ['LockSuccess'], blockers: [], auditGroups: [{ eventId: 'lock.confirmation.offer-a', label: 'Confirmation', evidenceRefs: ['audit:lock-confirmation:run-test'], replayHash: 'replay:lock-confirmation:offer-a', exportRef: 'export:lock-confirmation:run-test' }] }) } as Response;
      }
      throw new Error(`Unexpected fetch ${url}`);
    });

    render(<App />);

    expect(await screen.findByText('Ready to confirm')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Lifecycle cockpit' })).toBeInTheDocument();
    expect(screen.getByText('lock.lifecycle.ready.offer-a')).toBeInTheDocument();
    expect(screen.getByLabelText('Lock audit evidence grouped by backend event ids')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('checkbox'));
    fireEvent.click(screen.getByRole('button', { name: 'Confirm lock' }));

    expect(await screen.findByText('Lock details returned')).toBeInTheDocument();
    expect(screen.getByText('Lock id: lock-test')).toBeInTheDocument();
    expect(window.location.pathname).toBe('/quote/run-test/status');
  });

  it('retains selected offer context and shows a clear error for lock conflicts', async () => {
    window.history.pushState({}, '', '/quote/run-test/lock');
    sessionStorage.setItem('wcpe:selectedOfferId:run-test', 'conflict-offer');
    vi.mocked(fetch).mockImplementation(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = input.toString();
      if (url === '/api/ui/health') {
        return { ok: true, json: async () => ({ service: 'pricing-bff', status: 'UP', ready: true, dependencyStatus: 'NO_UPSTREAMS_CONFIGURED', dependencies: [] }) } as Response;
      }
      if (url.endsWith('/lock?selectedOfferId=conflict-offer')) {
        return { ok: true, json: async () => ({ runId: 'run-test', selectedOfferId: 'conflict-offer', status: 'READY', lockDisabled: false, blockers: [], disclosureText: 'Confirm lock disclosure.', nextAction: 'Confirm lock request', uiTraceId: 'brw-s04-local-trace', events: ['LockAttempted'], dependencyStatus: 'UPSTREAM_LOCK_CONTRACT_NOT_CONFIGURED' }) } as Response;
      }
      if (url.endsWith('/lock/confirm') && init?.method === 'POST') {
        return { ok: true, status: 409, json: async () => ({ runId: 'run-test', selectedOfferId: 'conflict-offer', status: 'CONFLICT', lockId: null, lockStatus: null, expiresAt: null, statusRoute: null, message: 'Lock conflict returned by BFF fallback: refresh status or choose another offer without losing context.', uiTraceId: 'brw-s04-local-trace', events: ['LockBlocked'], blockers: ['A competing lock context exists for the selected offer.'] }) } as Response;
      }
      throw new Error(`Unexpected fetch ${url}`);
    });

    render(<App />);

    expect(await screen.findByText('Ready to confirm')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('checkbox'));
    fireEvent.click(screen.getByRole('button', { name: 'Confirm lock' }));

    expect(await screen.findByText('Lock conflict')).toBeInTheDocument();
    expect(screen.getByText('Selected offer context retained: conflict-offer')).toBeInTheDocument();
  });

  it('filters partner quotes by status and renders detail inside tenant context', async () => {
    window.history.pushState({}, '', '/partners/quotes');
    vi.mocked(fetch).mockImplementation(async (input: RequestInfo | URL) => {
      const url = input.toString();
      if (url === '/api/ui/health') {
        return { ok: true, json: async () => ({ service: 'pricing-bff', status: 'UP', ready: true, dependencyStatus: 'NO_UPSTREAMS_CONFIGURED', dependencies: [] }) } as Response;
      }
      if (url === '/api/v1/partners/partner-preview/quotes') {
        return { ok: true, json: async () => ({ partnerId: 'partner-preview', tenantContext: 'ui-preview-tenant', statusFilter: '', quotes: [{ quoteId: 'quote-active', borrowerLabel: 'Borrower context available', status: 'ACTIVE', slaState: 'Awaiting configured SLA contract', lockState: 'LOCK_NOT_REQUESTED', errorFlags: [] }], uiTraceId: 'ch-s02-local-trace', events: ['PartnerQuoteLoaded'] }) } as Response;
      }
      if (url === '/api/v1/partners/partner-preview/quotes?status=BLOCKED') {
        return { ok: true, json: async () => ({ partnerId: 'partner-preview', tenantContext: 'ui-preview-tenant', statusFilter: 'BLOCKED', quotes: [{ quoteId: 'quote-blocked', borrowerLabel: 'Borrower context redacted', status: 'BLOCKED', slaState: 'Awaiting configured SLA contract', lockState: 'LOCK_BLOCKED', errorFlags: ['UPSTREAM_PARTNER_CONTRACT_NOT_CONFIGURED'] }], uiTraceId: 'ch-s02-local-trace', events: ['PartnerQuoteLoaded'] }) } as Response;
      }
      if (url.endsWith('/quote-active')) {
        return { ok: true, json: async () => ({ quoteId: 'quote-active', borrowerLabel: 'Borrower context available', status: 'ACTIVE', slaState: 'Awaiting configured SLA contract', lockState: 'LOCK_NOT_REQUESTED', errorFlags: [], tenantContext: 'ui-preview-tenant', partnerId: 'partner-preview', lifecycleEvents: ['PartnerQuoteLoaded'], actions: { reprice: { visible: true, permitted: true, guidance: 'API permit is true and partner role context is present.', supportHandoffRoute: '/partners/support/reprice' } }, uiTraceId: 'ch-s02-local-trace' }) } as Response;
      }
      if (url.endsWith('/quote-blocked')) {
        return { ok: true, json: async () => ({ quoteId: 'quote-blocked', borrowerLabel: 'Borrower context redacted', status: 'BLOCKED', slaState: 'Awaiting configured SLA contract', lockState: 'LOCK_BLOCKED', errorFlags: ['UPSTREAM_PARTNER_CONTRACT_NOT_CONFIGURED'], tenantContext: 'ui-preview-tenant', partnerId: 'partner-preview', lifecycleEvents: ['PartnerQuoteLoaded'], actions: { reprice: { visible: false, permitted: false, guidance: 'Reprice requires partner role context and an explicit API permit from the configured partner quote contract.', supportHandoffRoute: '/partners/support/reprice' } }, uiTraceId: 'ch-s02-local-trace' }) } as Response;
      }
      throw new Error(`Unexpected fetch ${url}`);
    });

    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Partner quote lifecycle' })).toBeInTheDocument();
    expect(await screen.findByText('tenant: ui-preview-tenant')).toBeInTheDocument();
    expect(await screen.findByText('Quote status')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Filter status'), { target: { value: 'BLOCKED' } });

    expect(await screen.findByText(/quote-blocked/)).toBeInTheDocument();
    expect(await screen.findByText('Reprice blocked')).toBeInTheDocument();
    expect(screen.getByText('Support path: /partners/support/reprice')).toBeInTheDocument();
  });

  it('shows partner reprice action when BFF detail marks it visible and permitted', async () => {
    window.history.pushState({}, '', '/partners/quotes');
    vi.mocked(fetch).mockImplementation(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = input.toString();
      if (url === '/api/ui/health') {
        return { ok: true, json: async () => ({ service: 'pricing-bff', status: 'UP', ready: true, dependencyStatus: 'NO_UPSTREAMS_CONFIGURED', dependencies: [] }) } as Response;
      }
      if (url === '/api/v1/partners/partner-preview/quotes') {
        return { ok: true, json: async () => ({ partnerId: 'partner-preview', tenantContext: 'ui-preview-tenant', statusFilter: '', quotes: [{ quoteId: 'quote-active', borrowerLabel: 'Borrower context available', status: 'ACTIVE', slaState: 'Awaiting configured SLA contract', lockState: 'LOCK_NOT_REQUESTED', errorFlags: [] }], uiTraceId: 'ch-s02-local-trace', events: ['PartnerQuoteLoaded'] }) } as Response;
      }
      if (url.endsWith('/quote-active') && init?.method !== 'POST') {
        return { ok: true, json: async () => ({ quoteId: 'quote-active', borrowerLabel: 'Borrower context available', status: 'ACTIVE', slaState: 'Awaiting configured SLA contract', lockState: 'LOCK_NOT_REQUESTED', errorFlags: [], tenantContext: 'ui-preview-tenant', partnerId: 'partner-preview', lifecycleEvents: ['PartnerQuoteLoaded'], actions: { reprice: { visible: true, permitted: true, guidance: 'API permit is true and partner role context is present.', supportHandoffRoute: '/partners/support/reprice' } }, uiTraceId: 'ch-s02-local-trace' }) } as Response;
      }
      if (url.endsWith('/reprice') && init?.method === 'POST') {
        return { ok: true, status: 202, json: async () => ({ quoteId: 'quote-active', status: 'ACCEPTED', message: 'Partner reprice request recorded by pricing-bff fallback.', guidance: 'Configured upstream repricing remains outside this UI fallback slice.', supportHandoffRoute: '/partners/support/reprice', uiTraceId: 'ch-s02-local-trace', events: ['PartnerQuoteRepriced'] }) } as Response;
      }
      throw new Error(`Unexpected fetch ${url}`);
    });

    render(<App />);

    const reprice = await screen.findByRole('button', { name: 'Request reprice' });
    expect(reprice).toBeInTheDocument();
    fireEvent.click(reprice);

    expect(await screen.findByText('Reprice request recorded')).toBeInTheDocument();
    expect(screen.getByText('Configured service repricing remains outside this UI fallback slice.')).toBeInTheDocument();
  });

  it('shows partner webhook retry health and gates replay and safety toggles with confirmation', async () => {
    window.history.pushState({}, '', '/partners/webhooks');
    vi.mocked(fetch).mockImplementation(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = input.toString();
      if (url === '/api/ui/health') {
        return { ok: true, json: async () => ({ service: 'pricing-bff', status: 'UP', ready: true, dependencyStatus: 'NO_UPSTREAMS_CONFIGURED', dependencies: [] }) } as Response;
      }
      if (url === '/api/v1/partners/partner-preview/integrations/webhooks') {
        return {
          ok: true,
          json: async () => ({
            partnerId: 'partner-preview',
            tenantContext: 'ui-preview-tenant',
            retryHealthSummary: 'RETRY_HEALTH_VISIBLE',
            eventWindow: 'latest 30 events',
            dlqSizeStatus: 'DLQ size requires configured integration-service metrics',
            retryWindowStatus: 'Configured retry window required',
            deliveryAttempts: [
              { webhookId: 'webhook-pricing-updates', eventId: 'event-quote-blocked', route: '/partners/quotes', status: 'FAILED', rootCauseCode: 'UPSTREAM_PARTNER_CONTRACT_NOT_CONFIGURED', lastSuccessfulAt: '2026-06-08T07:15:00Z', failureReason: 'Configured partner webhook transport is unavailable at the BFF boundary.', idempotencyKeyState: 'CONFIRMED_REQUIRED_FOR_REPLAY', maskingIndicator: 'MASKING_INDICATOR_PRESENT', consentIndicator: 'CONSENT_INDICATOR_PRESENT' },
            ],
            safetyToggles: [
              { webhookId: 'webhook-lock-alerts', route: '/partners/alerts', paused: true, visibleState: 'Auto-emit is paused for this route in the visible BFF fallback state.' },
            ],
            replayAction: { available: true, disabledReason: 'Replay requires request correlation and explicit idempotency confirmation before it can be recorded.', confirmationRequirement: 'Confirm correlation id and idempotency before replay.', supportHandoffRoute: '/partners/support/webhooks' },
            endpointTestAction: { available: false, disabledReason: 'Endpoint test requires the configured partner webhook transport contract.', confirmationRequirement: 'Confirm endpoint ownership before testing.', supportHandoffRoute: '/partners/support/webhooks' },
            uiTraceId: 'ch-s05-local-trace',
            events: ['WebhookHealthChecked'],
          }),
        } as Response;
      }
      if (url.endsWith('/replay') && init?.method === 'POST') {
        return { ok: true, status: 202, json: async () => ({ webhookId: 'webhook-pricing-updates', eventId: 'event-quote-blocked', status: 'ACCEPTED', message: 'Webhook replay request recorded by pricing-bff fallback.', guidance: 'Configured upstream replay execution remains outside this UI fallback slice.', downstreamExecuted: false, uiTraceId: 'ch-s05-local-trace', events: ['WebhookReplayRequested'] }) } as Response;
      }
      if (url.endsWith('/safety') && init?.method === 'POST') {
        return { ok: true, status: 202, json: async () => ({ webhookId: 'webhook-lock-alerts', route: '/partners/alerts', paused: false, status: 'VISIBLE', message: 'Safety toggle change is visible in the BFF fallback response.', uiTraceId: 'ch-s05-local-trace', events: ['WebhookSafetyToggled'] }) } as Response;
      }
      throw new Error(`Unexpected fetch ${url}`);
    });

    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Partner connection reliability' })).toBeInTheDocument();
    expect(await screen.findByText('latest 30 events')).toBeInTheDocument();
    expect(screen.getByText('exception queue size requires configured integration-service metrics')).toBeInTheDocument();
    expect(screen.getByText('configured service PARTNER setup NOT CONFIGURED')).toBeInTheDocument();
    expect(screen.getByText('Replay requires request correlation and explicit idempotency confirmation before it can be recorded.')).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Replay correlation id'), { target: { value: 'corr-123' } });
    fireEvent.click(screen.getByLabelText('I confirm idempotency for this replay request.'));
    fireEvent.click(screen.getByRole('button', { name: 'Request replay' }));

    expect(await screen.findByText('Webhook replay requested')).toBeInTheDocument();
    expect(screen.getByText('Connected workflow run: no')).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith('/api/v1/partners/partner-preview/integrations/webhooks/webhook-pricing-updates/replay', expect.objectContaining({ method: 'POST' }));

    fireEvent.click(screen.getByLabelText('I confirm this safety toggle change.'));
    fireEvent.click(screen.getByRole('button', { name: 'Resume auto-emit' }));

    expect(await screen.findByText('Safety toggle visible')).toBeInTheDocument();
    expect(screen.getByText('Current pause state: Active')).toBeInTheDocument();
  });

  it('renders compliance evidence, privacy, security, alert, and retention fallback blockers', async () => {
    window.history.pushState({}, '', '/compliance/evidence');
    vi.mocked(fetch).mockImplementation(async (input: RequestInfo | URL) => {
      const url = input.toString();
      if (url === '/api/ui/health') {
        return { ok: true, json: async () => ({ service: 'pricing-bff', status: 'UP', ready: true, dependencyStatus: 'NO_UPSTREAMS_CONFIGURED', dependencies: [] }) } as Response;
      }
      if (url === '/api/v1/compliance/evidence') {
        return {
          ok: true,
          json: async () => ({
            tenantContext: 'ui-preview-tenant',
            dependencyStatus: 'FALLBACK_STATIC_DEPENDENCIES_UNAVAILABLE',
            artifacts: [
              {
                artifactId: 'evidence-ops-lock-blocked',
                path: '/ops/cases/ops-lock-blocked',
                artifactType: 'OPS_ESCALATION',
                owner: 'Operations queue',
                retentionClass: 'Configured retention class required',
                relatedModule: 'operations',
                version: 'v1',
                hash: 'hash-placeholder-required',
                traceId: 'trace-ops-s06',
                policyVersion: 'policy-version-required',
                policyDigest: 'policy-digest-required',
                jurisdictionCode: 'jurisdiction-config-required',
                continuityStatus: 'CHAIN_CONTINUITY_UNVERIFIED',
                moduleLinks: ['m10-lock', 'ops-case-triage'],
                progressionBlocked: true,
                blockers: ['Missing configured compliance evidence store'],
              },
            ],
            decisions: [{ decisionId: 'decision-explainability-required', reasonCode: 'RULE_SOURCE_REQUIRED', humanText: 'Human-readable explanations require configured policy contracts.', jurisdictionCode: 'jurisdiction-config-required', reasonTiers: ['policy'], exportBlocked: true, disclosureArtifactRef: 'disclosure-artifact-required' }],
            privacyRequests: [{ requestId: 'dsar-config-required', borrowerRef: 'Borrower reference redacted', requestedScope: 'restricted', identityStatus: 'unverified', slaState: 'SLA deadline supplied by configured privacy service', consentAuditRef: 'consentAuditRef-required', blockers: ['Identity verification contract unavailable'] }],
            securityEvents: [{ eventId: 'security-event-config-required', category: 'vulnerability finding', severity: 'P2', owner: 'Security owner required', logRecordId: 'logRecordId-required', correlationId: 'trace-ch-s05', acknowledged: false, blockers: ['Explicit owner acknowledgment required before release handoff'] }],
            alerts: [{ alertId: 'alert-missing-evidence', severity: 'P2', alertClass: 'workflow', triggerType: 'missing_evidence', routeTarget: 'Owner queue required', acknowledged: false, blockers: ['Evidence attachment pending'] }],
            retentionControls: [{ ruleId: 'retention-rule-config-required', retentionClass: 'Configured retention class required', retentionWindow: 'Retention window supplied by configured policy', legalHoldActive: true, deletionGateReason: 'OD-005 unresolved blocks destructive retention actions', backupEvidence: 'backup inventory supplied by configured evidence store' }],
            uiTraceId: 'sec-s07-local-trace',
            events: ['ComplianceEvidenceRegistryOpened'],
            fallbackReason: 'Configured compliance, audit-replay, security, privacy, and retention service contracts are unavailable; this response carries non-secret UI fallback records only.',
          }),
        } as Response;
      }
      throw new Error(`Unexpected fetch ${url}`);
    });

    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Compliance evidence registry' })).toBeInTheDocument();
    expect(await screen.findByText('tenant: ui-preview-tenant')).toBeInTheDocument();
    expect(screen.getByRole('table', { name: 'Compliance evidence artifacts' })).toBeInTheDocument();
    expect(screen.getByText('guidance version-required / jurisdiction-config-required')).toBeInTheDocument();
    expect(screen.getByText('CHAIN CONTINUITY UNVERIFIED')).toBeInTheDocument();
    expect(screen.getByText('RULE SOURCE REQUIRED')).toBeInTheDocument();
    expect(screen.getByText('Identity: unverified')).toBeInTheDocument();
    expect(screen.getByText('Security record: logRecordId-required')).toBeInTheDocument();
    expect(screen.getByText('P2 · workflow · missing evidence')).toBeInTheDocument();
    expect(screen.getByText('OD-005 unresolved blocks destructive retention actions')).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith('/api/v1/compliance/evidence', expect.any(Object));
  });

  it('renders quality validation, readiness, drift, fairness, incidents, replay, contracts, and export blockers', async () => {
    window.history.pushState({}, '', '/quality/validation');

    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Quality guardrails dashboard' })).toBeInTheDocument();
    expect(await screen.findByText('Loop status RED')).toBeInTheDocument();
    expect(screen.getByRole('table', { name: 'Validation stages' })).toBeInTheDocument();
    expect(screen.getByText('V2 · Contract Validation')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Approve for rollout' })).toBeDisabled();
    expect(screen.getByText('Deployment action disabled until readiness passes and blockers clear.')).toBeInTheDocument();
    expect(screen.getByText('Comparison controls are locked until baseline and sample-window evidence are supplied.')).toBeInTheDocument();
    expect(screen.getByText('Protected class labels masked')).toBeInTheDocument();
    expect(screen.getByRole('table', { name: 'Quality incidents' })).toBeInTheDocument();
    expect(screen.getByText('Replay is blocked until configured snapshot, seed, and event payload evidence are supplied.')).toBeInTheDocument();
    expect(screen.getByRole('list', { name: 'Contract conformance checks' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Prepare redacted evidence export' }));
    expect(await screen.findByText('Export is redacted and incomplete until configured quality evidence storage is available.')).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith('/api/v1/quality/dashboard', expect.any(Object));
    expect(fetch).toHaveBeenCalledWith('/api/v1/quality/evidence/export', expect.any(Object));
  });

  it('renders eligibility decisions with fail-closed blockers and backend references', async () => {
    window.history.pushState({}, '', '/quote/run-test/eligibility');
    vi.mocked(fetch).mockImplementation(async (input: RequestInfo | URL) => {
      const url = input.toString();
      if (url === '/api/ui/health') {
        return { ok: true, json: async () => ({ service: 'pricing-bff', status: 'UP', ready: true, dependencyStatus: 'NO_UPSTREAMS_CONFIGURED', dependencies: [] }) } as Response;
      }
      if (url === '/api/v1/tenants/ui-preview-tenant/quote-runs/intake-metadata') {
        return { ok: true, json: async () => ({ tenantContext: 'ui-preview-tenant', dependencyStatus: 'SCENARIO_SERVICE_CONTRACT_NOT_CONFIGURED', fieldGroups: [], decisionControls: [], validationIssues: [], auditPackageId: 'audit-required', replayHashRef: 'replay-required', fallbackReason: 'metadata unavailable', uiTraceId: 'brw-s01-local-trace' }) } as Response;
      }
      if (url === '/api/v1/tenants/ui-preview-tenant/quote-runs/run-test/eligibility') {
        return {
          ok: true,
          json: async () => ({
            runId: 'run-test',
            quoteOptionId: 'option-1',
            status: 'FAIL_CLOSED_REVIEW',
            decisions: [
              { decisionId: 'eligible-contract-path', decision: 'ELIGIBLE', reasonCodes: ['ELIGIBILITY_CONTRACT_VISIBLE'], inputFactRefs: ['fact:scenario-version'], overlayRefs: ['overlay:configured-product'], cacheFreshness: { status: 'FRESHNESS_REQUIRED', cacheRef: 'cache:eligibility:decision', indicatorText: 'Cache timestamp supplied by eligibility-service.' }, explanationText: 'Configured eligibility-service explanation text is displayed here when available.', references: ['audit-package-required'] },
              { decisionId: 'ineligible-contract-path', decision: 'INELIGIBLE', reasonCodes: ['FILTER_OUT_EXPLANATION_REQUIRED'], inputFactRefs: ['fact:representative-credit'], overlayRefs: ['overlay:investor-contract'], cacheFreshness: { status: 'FRESHNESS_REQUIRED', cacheRef: 'cache:eligibility:filter-out', indicatorText: 'Filter-out cache evidence is backend-owned.' }, explanationText: 'Filter-out explanation must come from eligibility-service; the BFF does not infer policy logic.', references: ['evidence-id-required'] },
              { decisionId: 'conditional-contract-path', decision: 'CONDITIONAL', reasonCodes: ['REQUIRED_FACTS_PENDING'], inputFactRefs: ['fact:income-assets'], overlayRefs: ['overlay:conditional-review'], cacheFreshness: { status: 'STALE_OR_UNKNOWN', cacheRef: 'cache:eligibility:conditional', indicatorText: 'Refresh requirement is supplied by eligibility-service.' }, explanationText: 'Conditional explanation is visible only as backend-owned text and references.', references: ['condition-audit-ref-required'] },
            ],
            blockers: [{ reasonCode: 'UNKNOWN_REQUIRED_FACT', factRef: 'fact:income-assets', message: 'Required fact is unknown; eligibility stays fail-closed until a configured source supplies it.' }],
            requiredNextFacts: ['fact:income-assets'],
            fallbackReason: 'Configured eligibility-service decision, overlay, cache, and explanation contracts are unavailable; this fallback carries references and blockers only.',
            uiTraceId: 'brw-s04-local-trace',
            events: ['EligibilityModuleOpened'],
          }),
        } as Response;
      }
      throw new Error(`Unexpected fetch ${url}`);
    });

    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Eligibility explanation for run run-test' })).toBeInTheDocument();
    expect(screen.getByText('Fail-closed blockers')).toBeInTheDocument();
    expect(screen.getByText(/UNKNOWN_REQUIRED_FACT/)).toBeInTheDocument();
    expect(screen.getByRole('list', { name: 'Eligibility decisions' })).toBeInTheDocument();
    expect(screen.getByRole('listitem', { name: 'ELIGIBLE eligibility decision' })).toBeInTheDocument();
    expect(screen.getByRole('listitem', { name: 'INELIGIBLE eligibility decision' })).toBeInTheDocument();
    expect(screen.getByRole('listitem', { name: 'CONDITIONAL eligibility decision' })).toBeInTheDocument();
    expect(screen.getByText('fact:scenario-version')).toBeInTheDocument();
    expect(screen.getByText('overlay:investor-contract')).toBeInTheDocument();
    expect(screen.getByText('cache:eligibility:filter-out')).toBeInTheDocument();
    expect(screen.getByText('Filter-out explanation must come from eligibility-service; the BFF does not infer policy logic.')).toBeInTheDocument();
    expect(screen.queryByText(/LTV > 80/)).not.toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith('/api/v1/tenants/ui-preview-tenant/quote-runs/run-test/eligibility', expect.any(Object));
  });

  it('renders admin governance release gates with open decisions as blockers', async () => {
    window.history.pushState({}, '', '/admin/governance');

    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Admin governance and readiness controls' })).toBeInTheDocument();
    expect(await screen.findByText('Release status RED')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Deploy release candidate' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Execute rollback' })).toBeDisabled();
    expect(screen.getByRole('table', { name: 'Readiness checks' })).toBeInTheDocument();
    expect(screen.getByText('OD-001 unresolved blocks role access source and role-to-privilege ingestion.')).toBeInTheDocument();
    expect(screen.getByText('OD-001 · BLOCKING')).toBeInTheDocument();
    expect(screen.getByText('OD-002 · BLOCKING')).toBeInTheDocument();
    expect(screen.getByText('OD-004 · BLOCKING')).toBeInTheDocument();
    expect(screen.getByText('OD-005 · BLOCKING')).toBeInTheDocument();
    expect(screen.getByText('Feature flag activation blocked')).toBeInTheDocument();
    expect(screen.getByText('Market guidance promotion blocked')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Approve change request' })).toBeDisabled();
    expect(screen.getByText('Configured baseline and alert threshold are required; no numeric threshold is inferred.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Close incident' })).toBeDisabled();
    expect(screen.getByRole('table', { name: 'Change audit history' })).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith('/api/v1/admin/governance', expect.any(Object));
  });
});
