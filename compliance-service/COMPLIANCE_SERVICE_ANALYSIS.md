# Compliance Service Analysis

## 1. Service Purpose and Capabilities

The compliance-service is a Spring Boot 3.3.5 microservice built on Java 17 that implements regulatory compliance evaluation and governance for mortgage pricing workflows. It provides deterministic, auditable compliance decisions with full replay capability and evidence preservation.

### Core Capabilities

1. **Federal & State Rule Pack Resolution** - Evaluates federal and state-specific compliance rule packs with versioned, effective-dated configurations and tenant isolation.
2. **High-Cost Loan Evaluation** - Implements HOEPA/Section 32 high-cost mortgage threshold evaluation with configurable APR spread, points & fees, and prepayment penalty tests, including proximity band warnings.
3. **APR Advisory Policy** - Calculates APR spread with finance charge inclusion/exclusion rules, rounding modes, and warning band logic per regulatory formulas.
4. **Fair Lending Monitoring** - Runs disparate impact analysis snapshots with configured metrics, minimum population thresholds, protected class data redaction, and alert lifecycle management (raised ? under review ? resolved).
5. **Regulatory Config Approval** - Enforces separation of duties for rule pack approval workflows (draft ? ready_for_review ? submitted ? approved ? published) with evidence requirements, SOD enforcement, and rollback capability.
6. **Compliance Reason Code Catalog** - Manages versioned reason codes with borrower-safe text requiring legal approval, deprecation chains, and replay-safe resolution.
7. **Compliance Evidence Registry** - Provides artifact export with redaction profiles (FULL, PARTIAL, FULL_REDACTED, METADATA_ONLY), privacy request processing, security event acknowledgment, legal hold application, and retention deletion gating.
8. **Compliance Export Service** - Generates chain-of-custody export manifests with approved templates, redaction profiles, delivery policies, artifact integrity hashing, and expiry enforcement.
9. **Compliance Audit Snapshot** - Creates immutable, hash-verified evidence snapshots with legal hold support, sensitive data redaction by role, and tamper detection.

### Design Principles
- **Fail-closed by default**: Missing configuration, ambiguous versions, cross-tenant leakage, or missing inputs all return POLICY_NOT_SATISFIED/locked_missing_config statuses.
- **Deterministic replay**: Every evaluation produces a SHA-256 esultHash and uditRef enabling exact replay verification.
- **No hardcoded thresholds**: All regulatory thresholds, formulas, and policies are supplied via versioned configuration; source code enforces structure only (validated by NoHardCodedRegulatoryThresholdsTest).
- **Tenant isolation**: All evaluations and data are tenant-scoped; cross-tenant access is blocked and does not leak existence.
- **Audit-first**: Every state transition emits an outbox event type for downstream consumption.
- **Redaction by design**: Borrower PII and protected class data are redacted unless explicitly authorized.

## 2. Domain Entities and Data Models

### Federal Compliance Rule Pack
- **FederalRulePack** (V1 migration): id, 	enant_id, code, 
ame, category, created_at, created_by - unique per tenant/code
- **FederalRulePackVersion** (V1): id, 	enant_id, ule_pack_id (FK), ersion, status (PUBLISHED/DRAFT), effective_from, effective_to, pplicability (JSONB: productType, channel, state, lienPosition), ules (JSONB), 	hreshold_config_refs (JSONB), citations (JSONB), source_document_refs (JSONB), hash, created_by, pproved_by, created_at, updated_at - unique per tenant/rule_pack_id/version
- **FederalRulePackApproval** (V1): Audit trail for rule pack lifecycle actions

### State Compliance Rule Pack
- **StateRulePack** (V2): id, 	enant_id, state_code (CHAR(2)), code, 
ame, category, created_at, created_by - unique per tenant/state_code/code
- **StateRulePackVersion** (V2): id, 	enant_id, ule_pack_id (FK), ersion, status, effective_from, effective_to, pplicability (JSONB: productType, channel, lienPosition, occupancy), ules, 	hreshold_config_refs, ederal_rule_pack_refs (JSONB), citations, source_document_refs, hash, pproved_by, pproved_at, pproval_comments, created_at, created_by - unique per tenant/rule_pack_id/version
- **StateRulePackApproval** (V2): Audit trail for state rule pack lifecycle

### High-Cost Evaluation
- **HighCostEvaluation** (V3): id, 	enant_id, scenario_id, quote_id, s_of_date, product_type, state_code, status (high_cost/near_threshold/not_high_cost/blocked_missing_config/POLICY_NOT_SATISFIED), esult_hash, ederal_rule_pack_version_id, state_rule_pack_version_ids (JSONB), 	hreshold_config_version_ids (JSONB), equest_json (full request), esult_json (full result), correlation_id, idempotency_key, created_at, created_by_service - unique per tenant/idempotency_key
- **HighCostEvaluationLedger** (V3): Line-item ledger per test: sequence, 	est_code, input_ref, ormula_ref, config_version_id, aw_value, ounded_value, comparison_operator, 	hreshold_ref, outcome, eason_code`n
### APR Advisory Evaluation
- **AprAdvisoryEvaluation** (V4): id, 	enant_id, scenario_id, quote_id, s_of_date, status (clear/warning/blocking/blocked_missing_config/POLICY_NOT_SATISFIED), 
ote_rate, pr, spread, ormula_ref, config_version_ids (JSONB), esult_hash, created_at, updated_at - unique indexes on tenant/scenario, tenant/quote, tenant/as_of_date, result_hash
- **AprFinanceChargeComponent** (V4): id, 	enant_id, evaluation_id (FK), component_code, mount, included, inclusion_rule_ref, source_ref, sensitivity_classification`n- **AprAdvisoryLedger** (V4): Sequential ledger entries for APR advisory computation

### Fair Lending Monitoring
- **FairLendingMonitorConfig** (V5): id, 	enant_id, ersion, status, effective_from, effective_to, metric_definitions (TEXT), peer_group_refs (TEXT), protected_class_policy_ref, lert_policy_refs (TEXT), config_hash, pproved_by, pproved_at, created_at, updated_at - unique per tenant/version
- **FairLendingSnapshot** (V5): id, 	enant_id, config_version_id (FK), period_start, period_end, status (completed_clear/completed_alert/blocked_missing_config/POLICY_NOT_SATISFIED), population_count, data_completeness_score, esult_hash, correlation_id, created_at, updated_at`n- **FairLendingMetricResult** (V5): id, 	enant_id, snapshot_id (FK), metric_code, peer_group_key, comparison_group_key, outcome_measure, alue, 	hreshold_ref, severity, eason_code, supporting_refs`n- **FairLendingAlert** (V5): id, 	enant_id, snapshot_id (FK), metric_result_id (FK), severity, status (raised/under_review/resolved), ssigned_to, disposition, eview_comments, created_at, updated_at`n
### Compliance Reason Code Catalog
- **ComplianceReasonCode** (V6): id, 	enant_id, code, category, created_at, created_by - unique per tenant/code/category
- **ComplianceReasonCodeVersion** (V6): id, 	enant_id, eason_code_id (FK), ersion, status (draft/pending_approval/published/deprecated/suspended), severity (INFO/LOW/MEDIUM/HIGH/CRITICAL), effective_from, effective_to, internal_label, orrower_safe_label (boolean), orrower_safe_approved (boolean), description, citations (TEXT), ule_mappings (TEXT), locale_text (TEXT), successor_code, hash, pproved_by, pproved_at, pproval_comment, correlation_id, created_at, updated_at - unique per tenant/reason_code_id/version
- **ComplianceReasonCodeApproval** (V6): Audit trail for reason code lifecycle actions

### Regulatory Config Approval
- **RegulatoryConfigApproval** (V7): id, 	enant_id, rtifact_type, rtifact_id, rtifact_version, status (draft/ready_for_review/submitted/approved/rejected/published/rolled_back), effective_from, effective_to, uthor_id, submitted_at, pproved_by, pproved_at, published_by, published_at, rtifact_hash, pproval_package_hash, ollback_target_ref, pproval_package_json, alidation_report_json, simulation_evidence_json, udit_ref, correlation_id, created_at, updated_at - unique per tenant/artifact_type/artifact_id/artifact_version
- **RegulatoryConfigApprovalEvidence** (V7): id, 	enant_id, pproval_id (FK), evidence_type, source_ref, payload (JSONB), payload_hash, created_at`n- **RegulatoryConfigDecision** (V7): id, 	enant_id, pproval_id (FK), ctor_id, ctor_role, comments, previous_status, 
ew_status, correlation_id, decided_at`n
### Compliance Export
- **ComplianceExportJob** (V8): id, 	enant_id, status (pending_approval/ready_to_run/approved/completed/failed_blocked/expired), equested_by, pproved_by, 	emplate_version_ref, edaction_profile_ref, subject_filter_json, delivery_policy_ref, manifest_hash, rtifact_count, idempotency_key, correlation_id, equested_at, updated_at, expires_at - unique per tenant/idempotency_key
- **ComplianceExportArtifact** (V8): id, 	enant_id, export_job_id, rtifact_type, source_ref, ile_ref, content_type, payload_hash, edaction_applied, sequence - unique per tenant/export_job_id/sequence
- **ComplianceExportAccessLog** (V8): id, 	enant_id, export_job_id, ctor_id, purpose, correlation_id, ccessed_at`n
### Compliance Audit Snapshot
- **ComplianceAuditSnapshot** (in-memory model, persisted via audit-snapshot service): Immutable snapshot with snapshotId, 	enantId, subjectRef (subjectType, subjectId), snapshotType, sOfTimestamp, configVersionGraph (versionRefs), eventSequenceRefs, calculationLedgerRefs, easonCodeRefs, items (SnapshotEvidenceItem: sequence, itemType, sourceRef, schemaVersion, payload, payloadHash, sensitivityClassification, redactionPolicyRef), payloadHash, esultHash, etentionPolicyRef, legalHoldState (no_legal_hold/legal_hold_active/legal_hold_released), createdByService, ctorId, idempotencyKey, correlationId, uditRef, outboxEventTypes, legalHoldActions`n
### Compliance Evidence Registry (In-Memory/Service)
- **ArtifactEvidence**, **DecisionEvidence**, **AdvisoryReviewView**, **FairLendingEvidence**, **PrivacyRequestView**, **SecurityEventView**, **AlertView**, **RetentionRuleView**, **ConfigGapView** - all with auditRefs, replayHash, versionRefs, dependencyStatus
- **RedactionProfile** enum: FULL, PARTIAL, FULL_REDACTED, METADATA_ONLY

### Value Objects / Request/Response Records
All services use Java ecord types for request/response/result objects ensuring immutability and value semantics. Key records include:
- ComplianceShellRequest/Response - Basic compliance shell
- ComplianceEvaluationRequest/ComplianceAdvisoryResult - Federal/State rule pack resolution
- HighCostEvaluationRequest/Result - High-cost threshold evaluation
- AprAdvisoryRequest/Result - APR advisory calculation
- FairLendingSnapshotRequest/Result - Fair lending monitoring
- RegulatoryConfigApproval and related commands - Approval workflow
- ComplianceReasonCodeVersion, ReasonCodeResolution - Reason code lifecycle
- ComplianceExportJob, ExportManifest - Export orchestration
- ComplianceAuditSnapshot, SnapshotVerificationResult - Audit snapshots


### Service Health
- GET /api/compliance/health -> {service: 'compliance-service', status: 'UP', capability: 'local/dev deployment health adapter only'} (ServiceHealthController)

### Compliance Evidence Registry (ComplianceEvidenceController)
- GET /api/v1/compliance/evidence -> ComplianceEvidenceRegistryView (supports ?redactionProfile= query param)
- GET /api/v1/compliance/evidence/artifacts/{artifactId}/export -> ArtifactExportView (supports ?redactionProfile=PARTIAL default)
- POST /api/v1/compliance/privacy/requests/{requestId}/process -> PrivacyRequestView (accepts optional PrivacyProcessCommand body)
- POST /api/v1/compliance/security/events/{eventId}/acknowledge -> SecurityEventView (accepts optional SecurityAcknowledgeCommand body)
- POST /api/v1/compliance/retention/rules/{ruleId}/legal-hold -> RetentionRuleView (accepts optional LegalHoldCommand body)
- POST /api/v1/compliance/retention/rules/{ruleId}/delete -> RetentionDeletionGateView (accepts optional RetentionDeleteCommand body)

### Internal/Service-to-Service Evaluation APIs (No Controllers - Direct Service Calls)
The core evaluation services are pure Java functions called by upstream services (pricing, scenario, etc.). They do not expose REST endpoints directly but are invoked via service-to-service calls:

1. FederalComplianceRuleShell.evaluate(ComplianceEvaluationRequest) -> ComplianceAdvisoryResult
2. StateComplianceRuleShell.resolve(StateComplianceEvaluationRequest) -> StateComplianceAdvisoryResult
3. HighCostThresholdEvaluator.evaluate(HighCostEvaluationRequest) -> HighCostEvaluationResult
   - replay(request, expectedResultHash) -> HighCostEvaluationResult (replay verification)
4. AprAdvisoryPolicyEvaluator.evaluate(AprAdvisoryRequest) -> AprAdvisoryResult
   - replay(request, expectedResultHash) -> AprAdvisoryResult
5. FairLendingMonitoringService.runSnapshot(FairLendingSnapshotRequest) -> FairLendingSnapshotResult
   - replay(request, expectedResultHash) -> FairLendingSnapshotResult
   - reviewAlert(alert, reviewerId, assignedTo, comments) -> FairLendingAlert (status: raised -> under_review)
   - resolveAlert(alert, reviewerId, disposition, comments) -> FairLendingAlert (status: under_review -> resolved)
6. RegulatoryConfigApprovalService - Workflow methods returning RegulatoryConfigApproval:
   - createApprovalPackage(CreateApprovalPackage)
   - attachValidationReport(approval, EvidenceRef)
   - attachSimulationEvidence(approval, EvidenceRef)
   - submitApprovalPackage(approval, ApprovalDecision)
   - approveRegulatoryConfig(approval, ApprovalDecision) (enforces SOD: author cannot approve)
   - rejectRegulatoryConfig(approval, ApprovalDecision)
   - publishApprovedConfig(approval, PublishPlan) (requires cacheDomains)
   - rollbackPublishedConfig(approval, RollbackPlan) (requires rollbackTargetApproved=true)
7. ComplianceReasonCodeCatalog - Lifecycle methods returning ComplianceReasonCodeVersion or ReasonCodeLifecycleResult:
   - createDraft(DraftReasonCodeCommand, existingVersions)
   - approve(draft, ApprovalCommand) (enforces SOD + borrower-safe approval)
   - publish(approved) -> ReasonCodeLifecycleResult
   - deprecate(published, DeprecationCommand) -> ReasonCodeLifecycleResult (requires successorCode)
   - resolve(ResolveReasonCodeRequest, versions) -> ReasonCodeResolution (auto-resolves deprecated->successor for replay)
8. ComplianceExportService - Returns ComplianceExportJob:
   - createExportRequest(CreateComplianceExportRequest)
   - approveExport(job, ApprovalCommand) (enforces SOD: requester cannot approve)
   - runExport(job, RunComplianceExport) (validates artifacts, redaction, generates manifest)
   - manifestForDownload(job, DownloadCommand, now) -> ExportManifest (enforces expiry)
   - expireExport(job, ExpireCommand)
   - hashPayload(payload) -> sha256:
9. ComplianceAuditSnapshotService - Returns ComplianceAuditSnapshot or SnapshotVerificationResult:
   - createSnapshot(CreateComplianceAuditSnapshot)
   - verify(snapshot) -> SnapshotVerificationResult (detects HASH_MISMATCH)
   - placeLegalHold(snapshot, LegalHoldCommand)
   - releaseLegalHold(snapshot, LegalHoldCommand)
   - viewForRole(snapshot, actorRole) -> ComplianceAuditSnapshotView (redacts sensitive items for non-compliance-manager roles)
10. ComplianceEvidenceRegistryService - Returns registry views and operation results (see controller endpoints above)

### Outbox Event Types (for downstream consumption)
Every state transition emits a typed outbox event. Key event types:
- federal_compliance_rule_shell.completed.v1 / state_compliance_shell.completed.v1
- HighCostEvaluationCompleted.v1 / HighCostEvaluationFailedClosed.v1 / HighCostThresholdConfigReferenced.v1
- AprAdvisoryCompleted.v1 / AprAdvisoryFailedClosed.v1 / AprAdvisoryConfigReferenced.v1
- FairLendingSnapshotCompleted.v1 / FairLendingAlertRaised.v1 / FairLendingAlertReviewed.v1 / FairLendingAlertResolved.v1 / FairLendingSnapshotFailedClosed.v1
- ComplianceReasonCodePublished.v1 / ComplianceReasonCodeDeprecated.v1
- ComplianceAuditSnapshotCreated.v1 / ComplianceAuditSnapshotVerified.v1 / ComplianceAuditSnapshotLegalHoldPlaced.v1 / ComplianceAuditSnapshotLegalHoldReleased.v1
- RegulatoryConfigSubmitted.v1 / RegulatoryConfigApproved.v1 / RegulatoryConfigRejected.v1 / RegulatoryConfigPublished.v1 / RegulatoryConfigRolledBack.v1
- ComplianceExportRequested.v1 / ComplianceExportApproved.v1 / ComplianceExportCompleted.v1 / ComplianceExportFailed.v1 / ComplianceExportExpired.v1
- ComplianceArtifactExported.v1 / CompliancePrivacyRequestProcessed.v1 / ComplianceSecurityEventAcknowledged.v1 / ComplianceRetentionLegalHoldApplied.v1 / ComplianceRetentionDeletionExecuted.v1

## 4. Database Schema (Migrations)
The service uses Flyway migrations (V1-V8) for schema management. All tables use UUID primary keys and include tenant_id for multi-tenancy.

### V1__federal_rule_pack_shell.sql
- federal_rule_pack: Core rule pack metadata (id, tenant_id, code, name, category, created_at, created_by) - unique(tenant_id, code)
- federal_rule_pack_version: Versioned rule packs with JSONB applicability, rules, threshold_config_refs, citations, source_document_refs - unique(tenant_id, rule_pack_id, version)
- federal_rule_pack_approval: Audit trail for approval actions (action, actor_id, before_hash, after_hash, correlation_id, created_at)
- compliance_outbox: Event outbox table (event_id, tenant_id, event_type, event_version, partition_key, headers JSONB, payload JSONB, occurred_at, published_at, retry_count)
- Index: idx_federal_rule_pack_version_resolution on (tenant_id, rule_pack_id, status, effective_from, effective_to)

### V2__state_rule_pack_shell.sql
- state_rule_pack: State-specific rule packs (id, tenant_id, state_code CHAR(2), code, name, category, created_at, created_by) - unique(tenant_id, state_code, code)
- state_rule_pack_version: Versioned state rule packs with JSONB applicability, rules, threshold_config_refs, federal_rule_pack_refs, citations, source_document_refs - unique(tenant_id, rule_pack_id, version)
- state_rule_pack_approval: Audit trail for state rule pack approval
- Indexes: idx_state_rule_pack_tenant_state_status_effective, idx_state_rule_pack_version_lookup, idx_state_rule_pack_approval_version

### V3__high_cost_evaluation.sql
- high_cost_evaluation: Evaluation results with request/result JSONB, rule pack version refs, threshold config refs - unique(tenant_id, idempotency_key)
- high_cost_evaluation_ledger: Line-item computation ledger (sequence, test_code, input_ref, formula_ref, config_version_id, raw_value, rounded_value, comparison_operator, threshold_ref, outcome, reason_code) - unique(tenant_id, evaluation_id, sequence)
- Indexes: idx_high_cost_evaluation_scenario, idx_high_cost_evaluation_quote, idx_high_cost_evaluation_created

### V4__apr_advisory_evaluation.sql
- apr_advisory_evaluation: APR advisory results (tenant_id VARCHAR(64), scenario_id, quote_id, as_of_date, status, note_rate, apr, spread, formula_ref, config_version_ids JSONB, result_hash, created_at, updated_at)
- apr_finance_charge_component: Finance charge line items (component_code, amount, included, inclusion_rule_ref, source_ref, sensitivity_classification)
- apr_advisory_ledger: Sequential ledger entries (sequence_number, entry_type, entry_json TEXT)
- Indexes: idx_apr_advisory_tenant_scenario, idx_apr_advisory_tenant_quote, idx_apr_advisory_tenant_as_of, idx_apr_advisory_result_hash, idx_apr_finance_charge_tenant_eval, unique idx_apr_advisory_ledger_sequence

### V5__fair_lending_monitoring.sql
- fair_lending_monitor_config: Monitoring configurations (tenant_id VARCHAR(64), version, status, effective_from, effective_to, metric_definitions TEXT, peer_group_refs TEXT, protected_class_policy_ref, alert_policy_refs TEXT, config_hash, approved_by, approved_at, created_at, updated_at) - unique(tenant_id, version)
- fair_lending_snapshot: Snapshot runs (config_version_id FK, period_start, period_end, status, population_count, data_completeness_score, result_hash, correlation_id, created_at, updated_at)
- fair_lending_metric_result: Metric results per snapshot (metric_code, peer_group_key, comparison_group_key, outcome_measure, value, threshold_ref, severity, reason_code, supporting_refs)
- fair_lending_alert: Alert lifecycle (metric_result_id FK, severity, status (raised/under_review/resolved), assigned_to, disposition, review_comments, created_at, updated_at)
- Indexes: idx_fair_lending_monitor_config_tenant_version, idx_fair_lending_monitor_config_tenant_effective, idx_fair_lending_snapshot_tenant_period, idx_fair_lending_snapshot_tenant_status, idx_fair_lending_metric_tenant_snapshot, idx_fair_lending_alert_tenant_status_severity

### V6__compliance_reason_codes.sql
- compliance_reason_code: Base reason codes (tenant_id VARCHAR(64), code, category, created_at, created_by) - unique(tenant_id, code, category)
- compliance_reason_code_version: Versioned reason codes with status, severity, effective dates, internal/borrower-safe labels, citations, rule_mappings, locale_text, successor_code, hash, approval metadata - unique(tenant_id, reason_code_id, version)
- compliance_reason_code_approval: Audit trail for reason code lifecycle
- Indexes: idx_compliance_reason_code_version_tenant_code_version, idx_compliance_reason_code_version_tenant_status_effective, idx_compliance_reason_code_version_mapping_lookup, idx_compliance_reason_code_approval_tenant_version

### V7__regulatory_config_approval.sql
- regulatory_config_approval: Regulatory artifact approval packages (id VARCHAR(80), tenant_id, artifact_type, artifact_id, artifact_version, status (draft/ready_for_review/submitted/approved/rejected/published/rolled_back), effective dates, author/submission/approval/publication metadata, artifact_hash, approval_package_hash, rollback_target_ref, approval_package_json JSONB, validation_report_json JSONB, simulation_evidence_json JSONB, audit_ref, correlation_id) - unique(tenant_id, artifact_type, artifact_id, artifact_version)
- regulatory_config_approval_evidence: Evidence attachments (evidence_type, source_ref, payload JSONB, payload_hash)
- regulatory_config_decision: Decision log (actor_id, actor_role, comments, previous_status, new_status, correlation_id, decided_at)
- Indexes: regulatory_config_approval_tenant_status_idx, regulatory_config_approval_effective_idx, regulatory_config_approval_evidence_lookup_idx, regulatory_config_decision_approval_idx

### V8__compliance_export.sql
- compliance_export_job: Export job orchestration (id VARCHAR(80), tenant_id, status (pending_approval/ready_to_run/approved/completed/failed_blocked/expired), requested_by, approved_by, template/redaction/delivery refs, subject_filter_json, manifest_hash, artifact_count, idempotency_key, correlation_id, requested_at, updated_at, expires_at) - unique(tenant_id, idempotency_key)
- compliance_export_artifact: Export artifacts (artifact_type, source_ref, file_ref, content_type, payload_hash, redaction_applied, sequence) - unique(tenant_id, export_job_id, sequence)
- compliance_export_access_log: Access audit trail (actor_id, purpose, correlation_id, accessed_at)
- Indexes: idx_compliance_export_job_tenant_status, idx_compliance_export_job_tenant_expires

## 5. Business Logic / Services

### ComplianceShell (Entry Point)
- evaluateComplianceShell(ComplianceShellRequest) -> ComplianceShellResponse: Returns pending_evidence status with deferred message until quote/lock/concession evidence available. Validates requestId, subjectRef, evidenceRefs, ruleSetRef.

### FederalComplianceRuleShell
- evaluate(ComplianceEvaluationRequest) -> ComplianceAdvisoryResult: Core rule pack resolution logic:
  1. Filters versions by tenant, rulePackCode, PUBLISHED status, effective date (asOfDate), applicability criteria match
  2. Sorts by version DESC, requires exactly ONE match (fail-closed on 0 or >1)
  3. Validates all required thresholdConfigRefs are present in availableThresholdConfigRefs
  4. Returns RESOLVED with ruleExpressionRefs, or POLICY_NOT_SATISFIED with failClosedReasons (RULE_PACK_NOT_FOUND, AMBIGUOUS_RULE_PACK_VERSION, MISSING_THRESHOLD_CONFIG:ref)
  5. Emits auditRef (SHA-256 of material) and outboxEventType
- alidatePublishedPeriods(versions) -> List<String>: Detects overlapping effective periods for same tenant/rulePackCode/applicability

### StateComplianceRuleShell
- esolve(StateComplianceEvaluationRequest) -> StateComplianceAdvisoryResult: Similar to federal but adds:
  1. Normalizes propertyState to uppercase 2-char code
  2. Filters by stateCode in addition to rulePackCode
  3. Validates federalRulePackRefs against availableFederalRulePackRefs (fail-closed on missing precedence)
  4. Returns StateRulePackResolution including thresholdConfigRefs and federalRulePackRefs
  5. Same overlap validation in alidatePublishedPeriods$n
### HighCostThresholdEvaluator
- evaluate(HighCostEvaluationRequest) -> HighCostEvaluationResult: Multi-test threshold evaluation:
  1. Filters configs by tenant, effective date, applicability match (productType, channel, stateCode, lienPosition, occupancy)
  2. Sorts by testCode, detects duplicate testCodes (fail-closed: AMBIGUOUS_RULE_PACK_VERSION)
  3. For each config: retrieves input value (APR_SPREAD/POINTS_AND_FEES/PREPAYMENT_PENALTY), applies rounding (scale + mode), compares using operator (>, >=, <, <=, =) against threshold
  4. Determines outcome: high_cost (crossed), near_threshold (inside proximity band), not_high_cost
  5. Aggregates: crossed=true if any high_cost, blocking=true if any crossed test is blocking, near=true if any near_threshold
  6. Status: high_cost / near_threshold / not_high_cost; advisorySeverity: BLOCKING / WARNING / INFO
  7. Builds CalculationLedgerEntry per test with raw/rounded values, operator, thresholdRef, outcome, reasonCode
  8. Computes deterministic resultHash (SHA-256 of tenantId|scenarioId|quoteId|asOfDate|productType|stateCode|status|config hashes|ledger hashes|reasonCodes)
  9. eplay(request, expectedResultHash) verifies deterministic execution

### AprAdvisoryPolicyEvaluator
- evaluate(AprAdvisoryRequest) -> AprAdvisoryResult: APR spread calculation with finance charge treatment:
  1. Filters config by tenant, effective date, applicability match (productType, channel, stateCode)
  2. Requires exactly ONE config (fail-closed on 0 or >1: AMBIGUOUS_APR_FORMULA_CONFIG)
  3. Validates paymentStreamRef present, roundingMode configured, feeTreatments match financeChargeComponents
  4. Sorts components by componentCode, applies rounding (currencyScale + roundingMode)
  5. For each component: looks up FeeTreatmentConfig, sums included amounts to includedFinanceChargeTotal
  6. Rounds APR and noteRate (aprScale + roundingMode), computes spread = apr - noteRate
  7. Checks warning band: spread.abs() >= warningBandValue
  8. Status: clear / warning / blocking (if blockingWhenWarning=true)
  9. Builds FinanceChargeLedgerEntry and AprLedgerEntry with formulaRef, configVersionId
  10. Deterministic resultHash includes all material fields
  11. eplay(request, expectedResultHash) for verification

### FairLendingMonitoringService
- unSnapshot(FairLendingSnapshotRequest) -> FairLendingSnapshotResult: Disparate impact analysis:
  1. Filters config by tenant, effective for periodEnd, applicability match (productType, channel, geography)
  2. Requires exactly ONE APPROVED config (fail-closed: MONITOR_CONFIG_NOT_APPROVED, PROTECTED_CLASS_POLICY_MISSING, MISSING_METRIC_DEFINITIONS, etc.)
  3. Validates sourceCompleteness (sourceEventCount > 0, completenessRuleRef present)
  4. For each MetricDefinition (sorted by metricCode):
     - Requires OutcomeMeasure with matching metricCode
     - Fails if populationCount < minimumPopulation (INSUFFICIENT_POPULATION)
     - Rounds value (scale + roundingMode), compares against threshold (operator)
     - Severity from metric definition (HIGH/MEDIUM/LOW), reasonCode from definition or metricCode:ALERT/CLEAR
     - Redacts protected class drilldowns if not authorized
  5. Creates FairLendingAlert for each thresholdCrossed metric (raised status)
  6. Status: completed_clear (no alerts) / completed_alert (alerts raised)
  7. Deterministic resultHash
  8. Alert workflow: eviewAlert (raised -> under_review) -> esolveAlert (under_review -> resolved) with SOD enforcement

### RegulatoryConfigApprovalService
State machine enforcing separation of duties and evidence requirements:
- DRAFT -> (attachValidationReport + attachSimulationEvidence) -> READY_FOR_REVIEW
- READY_FOR_REVIEW -> SUBMITTED (requires both evidences, observedArtifactHash matches)
- SUBMITTED -> APPROVED (different actor than author, SOD enforced, observedArtifactHash matches)
- SUBMITTED -> REJECTED
- APPROVED -> PUBLISHED (publisherId, cacheDomains, observedArtifactHash matches)
- PUBLISHED -> ROLLED_BACK (requires rollbackTargetApproved=true, observedArtifactHash matches)
- Every transition records RegulatoryConfigDecision in decisionLog and emits outbox event

### ComplianceReasonCodeCatalog
- createDraft: Creates version 1 DRAFT with hash, validates required fields (citations, ruleMappings, localeText with borrower_safe entries)
- pprove: Requires different approver than author, borrowerSafeApproved=true if borrowerSafeLabel=true
- publish: Only from PENDING_APPROVAL, requires borrowerSafeApproved if borrowerSafeLabel=true
- deprecate: Requires successorCode, sets status DEPRECATED
- esolve: Finds latest effective version for tenant/code/asOfDate/audience:
  - If DEPRECATED and not replay -> auto-resolves to successorCode (recursive)
  - If SUSPENDED -> fail-closed
  - If borrower_safe audience -> requires borrowerSafeApproved=true, returns borrowerSafeTextFor(locale)
  - Returns ReasonCodeResolution with displayText, citations, ruleMappings, successorCode, replayHash

### ComplianceExportService
- createExportRequest: Validates templateVersionRef.approved, sensitiveExport requires redactionProfileRef.approved and deliveryPolicyRef.approved. Status: pending_approval (if approvalRequired or sensitiveExport) else ready_to_run.
- pproveExport: SOD enforcement (requester != approver), moves to APPROVED
- unExport: Validates artifacts (non-empty, required fields, redactionApplied if sensitiveExport). Builds ExportManifest with deterministic manifestHash (SHA-256 of schema|tenant|exportId|template|redaction|delivery|subject|completedAt|artifactMaterials). Chain-of-custody hash = manifestHash.
- manifestForDownload: Enforces COMPLETED status and expiry check
- expireExport: Moves to EXPIRED

### ComplianceAuditSnapshotService
- createSnapshot: Builds immutable snapshot with payloadHash (SHA-256 of sorted items) and resultHash (includes config graph, event/ledger/reasonCode refs, payloadHash). Validates each item's payloadHash matches hashPayload(payload).
- erify: Recalculates payloadHash and resultHash, returns HASH_MISMATCH if tampered
- placeLegalHold/eleaseLegalHold: Records LegalHoldAction in legalHoldActions, enforces no duplicate place, status transitions
- iewForRole: Redacts sensitive items (sensitivityClassification not public/internal) for non-compliance-manager roles, adds REDACTION_REQUIRED warning

### ComplianceEvidenceRegistryService
- In-memory registry with redaction profiles (FULL, PARTIAL, FULL_REDACTED, METADATA_ONLY)
- egistry(redactionProfile): Returns full registry with applied redaction
- exportArtifact: Returns ArtifactExportView, blocks export if artifact.blocked=true
- processPrivacyRequest: Validates identityVerified, scopeConfirmed, exportRef; returns processed/blocked
- cknowledgeSecurityEvent: Requires owner, returns acknowledged status
- pplyLegalHold: Requires reason, sets legalHold=true, deletionGate=blocked_by_legal_hold
- equestDeletion: Blocks if legalHoldActive, requires approvalRef and backupEvidence
 
 ## 6. Key Algorithms
### SHA-256 Deterministic Hashing (Universal)
### Rule Pack Resolution (Federal & State)
1. Tenant scoping: Filter by tenantId (case-insensitive equality)
2. Code matching: Filter by rulePackCode (case-insensitive)
3. Status filter: Only PUBLISHED versions
4. Temporal validity: asOfDate within [effectiveFrom, effectiveToOrMax()]
5. Applicability matching: Criteria fields match using wildcard (*) or exact case-insensitive equality
6. Version selection: Highest version wins (DESC sort)
7. Uniqueness enforcement: Exactly one match required; 0 -> RULE_PACK_NOT_FOUND, >1 -> AMBIGUOUS_RULE_PACK_VERSION
8. Dependency validation: All thresholdConfigRefs and federalRulePackRefs must be present in available sets
### High-Cost Threshold Evaluation
For each applicable ThresholdConfigVersion (sorted by testCode):
1. Input retrieval: Extract input value by inputType (APR_SPREAD, POINTS_AND_FEES, PREPAYMENT_PENALTY)
2. Rounding: input.setScale(roundingScale, roundingMode) (default HALF_UP)
3. Comparison: roundedValue.compareTo(thresholdValue) with operator (>, >=, <, <=, =)
4. Proximity band: If not crossed and proximityBandValue > 0: For >/>=: thresholdValue - roundedValue <= proximityBandValue (approaching from below); For </<=: roundedValue - thresholdValue <= proximityBandValue (approaching from above)
5. Outcome classification: high_cost / near_threshold / not_high_cost
6. Aggregation: Any high_cost -> crossed=true; any blocking test crossed -> blocking=true; any near_threshold -> near=true
7. Result hash: Combines tenantId, scenarioId, quoteId, asOfDate, productType, stateCode, status, all config hashMaterials, all ledger hashMaterials, reasonCodes
### APR Advisory Spread Calculation
1. Finance charge aggregation: Sort components by componentCode, round each amount, sum included amounts per FeeTreatmentConfig
2. APR and Note Rate rounding: Both rounded to aprScale with roundingMode
3. Spread: roundedApr - roundedNoteRate (rounded to aprScale)
4. Warning band: spread.abs() >= warningBandValue (if warningBandValue > 0)
5. Status: clear / warning / blocking (if blockingWhenWarning)
6. Result hash: Includes all material fields + finance charge ledger + APR ledger
### Fair Lending Metric Evaluation
1. Population threshold: measure.populationCount >= definition.minimumPopulation (fail-closed if insufficient)
2. Rounding: measure.value.setScale(definition.scale, definition.roundingMode)
3. Threshold comparison: Same operator logic as high-cost evaluation
4. Redaction: If protectedClassDetailsRequested AND NOT protectedClassDetailAuthorized, drilldowns replaced with protected-class-drilldowns-redacted and protectedClassRedacted=true
5. Alert creation: One alert per crossed metric with metricCode, severity, raised status
### Deterministic Replay Verification
Every evaluator implements replay(request, expectedResultHash): Re-evaluates the request, compares computed resultHash with expectedResultHash. On mismatch: returns POLICY_NOT_SATISFIED with REPLAY_HASH_MISMATCH/APR_REPLAY_HASH_MISMATCH/FAIR_LENDING_REPLAY_HASH_MISMATCH reason code, emits FAILED_CLOSED event type. On match: returns original result.
### Export Manifest Hash (Chain of Custody)
manifestHash = sha256(schema:v1|tenantId|exportId|templateHash|redactionHash|deliveryHash|subjectHash|completedAt|artifactMaterials); chainOfCustodyHash = chain-of-custody: + manifestHash; Artifact hash: sha256(sequence|artifactType|sourceRef|fileRef|contentType|payloadHash|redactionApplied); Manifest immutable once COMPLETED
### Audit Snapshot Integrity
payloadHash = sha256(schema:v1|items|sorted(item.hashMaterial)) where item.hashMaterial = sequence|itemType|sourceRef|schemaVersion|payloadHash|sensitivity|redactionPolicy; resultHash = sha256(schema:v1|tenantId|snapshotId|subjectRef|snapshotType|asOfTimestamp|configGraph|eventRefs|ledgerRefs|reasonCodeRefs|payloadHash|retentionPolicy|items); verify() recomputes both hashes and compares against stored values
### Reason Code Hash
hash = sha256(tenantId|code|category|severity|effectiveFrom|effectiveTo|internalLabel|borrowerSafeLabel|borrowerSafeApproved|description|citations|ruleMappings|localeText); Immutable after creation; status transitions create new audit trail but hash remains constant
### Tenant Isolation & Cross-Tenant Leakage Prevention

## 7. Test Coverage

### Unit Tests (JUnit 5)
Each core service has dedicated test classes with comprehensive coverage:
- ComplianceShellTest: 3 tests (valid request, optional evidence refs, validation error shape)
- FederalComplianceRuleShellTest: 7 tests (resolution, missing threshold, cross-tenant isolation, overlapping periods, deterministic audit ref, validation error)
- StateComplianceRuleShellTest: 8 tests (resolution, missing property state, cross-tenant/state isolation, missing threshold, missing federal precedence, state overlap, deterministic audit ref, validation error)
- HighCostThresholdEvaluatorTest: 9 tests (threshold crossing, missing config, formula/rounding, deterministic replay hash, replay mismatch, ambiguous version, missing input, validation error)
- AprAdvisoryPolicyEvaluatorTest: 10 tests (warning band, finance charge/rounding, missing config, ambiguous fee treatment, invalid component, missing payment stream, deterministic replay, replay mismatch, validation error)
- FairLendingMonitoringServiceTest: 8 tests (configured metrics, insufficient population, severity, missing config, redaction, deterministic replay, replay mismatch, alert workflow transitions, validation error)
- ReasonCodeResolverTest: 3 tests (deprecated replay, borrower-safe rejection, borrower-safe after approval)
- ReasonCodeBorrowerTextPolicyTest: 2 tests (requires legal approval, separates author/approver)
- ComplianceReasonCodeTest: 3 tests (duplicate code, draft creation, publish with audit/outbox)
- RegulatoryApprovalPolicyTest: 7 tests (submit/approve/publish, SOD enforcement, validation before approval, draft vs submission timestamps, stale artifact hash rejection, rollback target approval, rollback recording)
- ComplianceAuditSnapshotServiceTest: 7 tests (immutable snapshot, evidence validation, tamper detection, legal hold, legal hold release, legal hold doesn't invalidate verify, role-based redaction)
- ComplianceExportServiceTest: 6 tests (approved template, pending approval, SOD enforcement, deterministic manifest, sensitive redaction blocking, expiry enforcement, manifest hash sensitivity)
- ComplianceEvidenceApiTest: 6 tests (full registry, artifact redaction, fair lending redaction, privacy SLA, security events, retention legal hold/deletion)
- ComplianceGoldenReplayTest: 2 tests (fixture replay hash determinism, hash mismatch detection)
- NoHardCodedRegulatoryThresholdsTest: 1 test (scans source for forbidden numeric literals 0.125, 0.0125, 80, 43, 1 in BigDecimal literals outside evaluator/catalog files)

### Test Architecture Patterns
- All tests use JUnit 5 with ComplianceShellValidationError for validation failures
- Golden fixture files in golden/ directory provide expected output contracts per story (PII-15-S01 through PII-15-S09)
- Fixture index (fixtures/compliance/fixture-index.json) maps 9 fixtures to stories with schemaRef, configVersionRefs, expectedHash, sensitivity
- ComplianceContractTestCatalog provides REST/event contract definitions and fixture validation
- Deterministic replay verification is a core test pattern across all evaluators
- No hardcoded regulatory thresholds enforced via static analysis test
- Cross-tenant isolation and PII minimization verified in multiple tests

## 8. Configuration

### application.yml
- spring.application.name: compliance-service
- server.port: 
- management.endpoint.health.probes.enabled: true
- management.endpoints.web.exposure.include: health,info,prometheus

### Kubernetes Dev Config (k8s/compliance-dev.yaml)
- Namespace: wcpe-dev
- ConfigMap: SPRING_PROFILES_ACTIVE=dev, SERVER_PORT=8080, MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info,prometheus, WCPE_SYNTHETIC_ONLY=true
- Deployment: 1 replica, runAsNonRoot:10001, readOnlyRootFilesystem, dropped capabilities, resource requests (100m CPU/512Mi), limits (750m CPU/1Gi)
- Readiness probe: /actuator/health/readiness, Liveness probe: /actuator/health/liveness
- Service: ClusterIP on port 8080

### Docker (Dockerfile)
- Base: eclipse-temurin:17-jre
- JAR: build/libs/compliance-service-0.1.0.jar -> app.jar
- Expose 8080, EntryPoint: java -jar /app/app.jar

## 9. Dependencies

### Build Dependencies (build.gradle)
- org.springframework.boot:spring-boot-starter-web (3.3.5 via BOM)
- org.springframework.boot:spring-boot-starter-actuator (3.3.5 via BOM)
- org.junit.jupiter:junit-jupiter:5.10.2 (test)
- io.spring.dependency-management:1.1.6 (plugin)

### Runtime Dependencies (Transitive from Spring Boot)
- Spring Boot 3.3.5 (Spring Framework 6.1.x)
- Tomcat embedded server
- Jackson (JSON serialization)
- Micrometer + Prometheus (metrics)
- Java 17 (toolchain enforced)

### External Service Dependencies (Implied)
- PostgreSQL (Flyway migrations V1-V8, UUID generation, JSONB, timestamptz)
- Message broker for outbox event publishing (compliance_outbox table)
- Governance service (for policy references, retention windows, redaction policies)
- Audit replay service (for snapshot/artifact verification)
- Pricing/Scenario services (upstream callers of evaluation APIs)
