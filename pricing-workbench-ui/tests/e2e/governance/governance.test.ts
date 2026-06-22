// Governance, Compliance, and Partner E2E Tests
import { test, expect } from "@playwright/test";
import { apiHelper, type ApiResponse, type TestContext } from "../core/helpers/api-helper";
import { uiHelper, type NavigationResult } from "../core/helpers/ui-helper";
import { driftDetector, type DriftFinding, type DriftReport, type BaselineExpectation } from "../core/drift/drift-detector";
import { personas, type PersonaRole, getPersona, getTestScenarios, getAccessibleRoutes, getRestrictedRoutes } from "../core/personas/personas";
const { describe, beforeAll, afterAll, beforeEach, afterEach } = test;
const GOVERNANCE_PERSONAS = ["GOVERNANCE_REVIEWER", "ADMIN"] as PersonaRole[];
const COMPLIANCE_PERSONAS = ["COMPLIANCE_OFFICER", "ADMIN"] as PersonaRole[];
const PARTNER_PERSONAS = ["CAPITAL_MARKETS", "ADMIN", "WHOLESALE_LO", "CORRESPONDENT_LO"] as PersonaRole[];
const ALL_PERSONAS = ["GOVERNANCE_REVIEWER", "COMPLIANCE_OFFICER", "ADMIN", "CAPITAL_MARKETS"] as PersonaRole[];
const HEADLESS_MODE = process.env.HEADED !== "true";

interface TestRunState {
 runId: string;
 optionId: string;
 scenario: string;
 persona: PersonaRole;
 traceId: string;
}

const testRunState = new Map<string, TestRunState>();

async function verifyDriftForConfigVersion(
 scenario: string,
 persona: PersonaRole,
 actualConfig: {
 version: string;
 descriptors: Array<{ id: string; version: string; hash: string; status: string }>;
 policies: Array<{ id: string; version: string; hash: string; status: string }>;
 featureFlags: Array<{ id: string; version: string; hash: string; enabled: boolean }>;
 marketRules: Array<{ id: string; version: string; hash: string; status: string }>;
 }
): Promise<DriftFinding[]> {
 const key = ${scenario}-;
 const baseline = driftDetector[\baselines\].get(key);
 const findings: DriftFinding[] = [];

 if (!baseline) return findings;

 // Check config version drift
 const baselineVersion = baseline.expectations.apiResponses[\/api/v1/admin/governance/config/version\]?.fieldConstraints?.version;
 if (baselineVersion && actualConfig.version !== baselineVersion.enum?.[0]) {
 findings.push({
 category: \api\,
 severity: \WARNING\,
 description: Config version drift detected for ,
 expected: baselineVersion.enum?.[0] || \baseline version\,
 actual: actualConfig.version,
 baselineVersion: baseline.version,
 currentVersion: \current\,
 recommendation: \Review config version changes and approve if intentional\,
 });
 }

 // Check descriptor hashes
 for (const descriptor of actualConfig.descriptors) {
 if (descriptor.hash && descriptor.status === \MODIFIED\) {
 findings.push({
 category: \api\,
 severity: \WARNING\,
 description: Descriptor hash mismatch,
 expected: \hash matches baseline\,
 actual: hash: , status: ,
 baselineVersion: baseline.version,
 currentVersion: \current\,
 recommendation: \Verify descriptor changes are approved\,
 });
 }
 }

 // Check policy hashes
 for (const policy of actualConfig.policies) {
 if (policy.hash && policy.status === \MODIFIED\) {
 findings.push({
 category: \api\,
 severity: \WARNING\,
 description: Policy hash mismatch,
 expected: \hash matches baseline\,
 actual: hash: , status: ,
 baselineVersion: baseline.version,
 currentVersion: \current\,
 recommendation: \Verify policy changes are approved\,
 });
 }
 }

 // Check feature flag changes
 for (const flag of actualConfig.featureFlags) {
 if (flag.hash && flag.enabled !== undefined) {
 findings.push({
 category: \api\,
 severity: \INFO\,
 description: Feature flag state: ,
 expected: \baseline state\,
 actual: enabled: , hash: ,
 baselineVersion: baseline.version,
 currentVersion: \current\,
 recommendation: \Track feature flag changes for audit\,
 });
 }
 }

 // Check market rule hashes
 for (const rule of actualConfig.marketRules) {
 if (rule.hash && rule.status === \MODIFIED\) {
 findings.push({
 category: \api\,
 severity: \WARNING\,
 description: Market rule hash mismatch,
 expected: \hash matches baseline\,
 actual: hash: , status: ,
 baselineVersion: baseline.version,
 currentVersion: \current\,
 recommendation: \Verify market rule changes are approved\,
 });
 }
 }

 return findings;
}

async function verifyDriftForComplianceStatus(
 scenario: string,
 persona: PersonaRole,
 actualCompliance: {
 overallStatus: string;
 artifacts: Array<{ id: string; type: string; status: string; lastReviewed: string }>;
 decisions: Array<{ id: string; status: string; reviewedBy: string }>;
 fairLendingMetrics: Array<{ metricId: string; value: number; threshold: number; status: string }>;
 privacyRequests: Array<{ id: string; type: string; status: string; slaDays: number }>;
 securityEvents: Array<{ id: string; severity: string; status: string }>;
 alerts: Array<{ id: string; severity: string; status: string }>;
 retentionControls: Array<{ policyId: string; status: string; complianceRate: number }>;
 }
): Promise<DriftFinding[]> {
 const key = ${scenario}-;
 const baseline = driftDetector[\baselines\].get(key);
 const findings: DriftFinding[] = [];

 if (!baseline) return findings;

 // Check overall compliance status
 const baselineStatus = baseline.expectations.apiResponses[\/api/v1/compliance/evidence/status\]?.fieldConstraints?.overallStatus;
 if (baselineStatus && actualCompliance.overallStatus !== baselineStatus.enum?.[0]) {
 findings.push({
 category: \api\,
 severity: actualCompliance.overallStatus === \NON_COMPLIANT\ ? \CRITICAL\ : \WARNING\,
 description: Compliance status drift: ,
 expected: baselineStatus.enum?.[0] || \COMPLIANT\,
 actual: actualCompliance.overallStatus,
 baselineVersion: baseline.version,
 currentVersion: \current\,
 recommendation: \Investigate compliance status change immediately\,
 });
 }

 // Check fair lending metrics
 for (const metric of actualCompliance.fairLendingMetrics) {
 if (metric.status === \EXCEEDS_THRESHOLD\) {
 findings.push({
 category: \api\,
 severity: \CRITICAL\,
 description: Fair lending metric exceeds threshold,
 expected: <= ,
 actual: ${metric.value},
 baselineVersion: baseline.version,
 currentVersion: \current\,
 recommendation: \Immediate fair lending review required\,
 });
 }
 }

 // Check privacy request SLA
 for (const request of actualCompliance.privacyRequests) {
 if (request.slaDays > 30 && request.status !== \COMPLETED\) {
 findings.push({
 category: \api\,
 severity: \WARNING\,
 description: Privacy request SLA at risk,
 expected: \<= 30 days\,
 actual: ${request.slaDays} days,
 baselineVersion: baseline.version,
 currentVersion: \current\,
 recommendation: \Prioritize privacy request completion\,
 });
 }
 }

 // Check security events
 const criticalSecurityEvents = actualCompliance.securityEvents.filter(e => e.severity === \CRITICAL\ && e.status === \OPEN\);
 if (criticalSecurityEvents.length > 0) {
 findings.push({
 category: \api\,
 severity: \CRITICAL\,
 description: ${criticalSecurityEvents.length} critical security events open,
 expected: \0 critical open events\,
 actual: ${criticalSecurityEvents.length} open,
 baselineVersion: baseline.version,
 currentVersion: \current\,
 recommendation: \Immediate security incident response required\,
 });
 }

 // Check retention compliance
 for (const control of actualCompliance.retentionControls) {
 if (control.complianceRate < 0.95) {
 findings.push({
 category: \api\,
 severity: \WARNING\,
 description: Retention policy compliance rate below 95%,
 expected: \>= 95%\,
 actual: ${(control.complianceRate * 100).toFixed(1)}%,
 baselineVersion: baseline.version,
 currentVersion: \current\,
 recommendation: \Review retention policy enforcement\,
 });
 }
 }

 return findings;
}

async function runDriftReport(findings: DriftFinding[], baselineVersion: string, scenario: string, persona: string): Promise<void> {
 if (findings.length > 0) {
 const report = driftDetector.generateReport(findings, baselineVersion, \current\);
 console.log(\\n=== DRIFT REPORT: () ===);
 console.log(JSON.stringify(report, null, 2));

 if (report.overallSeverity === \CRITICAL\) {
 throw new Error(CRITICAL drift detected for (): );
 }
 }
}
describe(" Governance Compliance and Partner E2E Tests\, () => {
 let globalTraceId: string;

 beforeAll(async () => {
 await apiHelper.init();
 globalTraceId = e2e-governance--;

 driftDetector.loadBaselinesFromDir(\./tests/baselines/governance\);
 driftDetector.loadBaselinesFromDir(\./tests/baselines/compliance\);
 driftDetector.loadBaselinesFromDir(\./tests/baselines/partners\);
 });

 afterAll(async () => {
 await apiHelper.dispose();
 });
// ========================================================================
// ADMIN GOVERNANCE TESTS
// ========================================================================
describe(" Admin Governance\, () => {
 for (const personaRole of GOVERNANCE_PERSONAS) {
 const persona = getPersona(personaRole);

 test(${personaRole} - Admin Governance: descriptors, policies, feature flags, market rules, async ({ page }) => {
 const helper = uiHelper(page);
 const ctx = apiHelper.createContext(personaRole);
 ctx.traceId = globalTraceId;

 // Test API endpoint
 const governanceResponse = await apiHelper.getAdminGovernance(ctx);
 expect(governanceResponse.status).toBe(200);
 expect(governanceResponse.data).toBeDefined();
 expect(governanceResponse.data.configVersion).toBeDefined();
 expect(governanceResponse.data.descriptors).toBeDefined();
 expect(governanceResponse.data.policies).toBeDefined();
 expect(governanceResponse.data.featureFlags).toBeDefined();
 expect(governanceResponse.data.marketRules).toBeDefined();
 expect(governanceResponse.data.changeRequests).toBeDefined();
 expect(governanceResponse.data.releaseCandidates).toBeDefined();
 expect(governanceResponse.data.driftAlerts).toBeDefined();
 expect(governanceResponse.data.incidents).toBeDefined();
 expect(governanceResponse.data.overrideLedger).toBeDefined();
 expect(governanceResponse.data.pendingReview).toBeDefined();
 expect(governanceResponse.data.dynamicRuleEvidence).toBeDefined();

 // Verify descriptors structure
 for (const descriptor of governanceResponse.data.descriptors) {
 expect(descriptor).toHaveProperty(\id\);
 expect(descriptor).toHaveProperty(\version\);
 expect(descriptor).toHaveProperty(\hash\);
 expect(descriptor).toHaveProperty(\status\);
 expect([\ACTIVE\, \PENDING\, \DEPRECATED\, \MODIFIED\]).toContain(descriptor.status);
 expect(descriptor).toHaveProperty(\lastModified\);
 expect(descriptor).toHaveProperty(\modifiedBy\);
 }

 // Verify policies structure
 for (const policy of governanceResponse.data.policies) {
 expect(policy).toHaveProperty(\id\);
 expect(policy).toHaveProperty(\version\);
 expect(policy).toHaveProperty(\hash\);
 expect(policy).toHaveProperty(\status\);
 expect([\ACTIVE\, \PENDING\, \DEPRECATED\, \MODIFIED\]).toContain(policy.status);
 expect(policy).toHaveProperty(\scope\);
 expect(policy).toHaveProperty(\rules\);
 expect(Array.isArray(policy.rules)).toBe(true);
 }

 // Verify feature flags structure
 for (const flag of governanceResponse.data.featureFlags) {
 expect(flag).toHaveProperty(\id\);
 expect(flag).toHaveProperty(\version\);
 expect(flag).toHaveProperty(\hash\);
 expect(flag).toHaveProperty(\enabled\);
 expect(typeof flag.enabled).toBe(\boolean\);
 expect(flag).toHaveProperty(\rolloutPercentage\);
 expect(flag).toHaveProperty(\targetSegments\);
 expect(Array.isArray(flag.targetSegments)).toBe(true);
 }

 // Verify market rules structure
 for (const rule of governanceResponse.data.marketRules) {
 expect(rule).toHaveProperty(\id\);
 expect(rule).toHaveProperty(\version\);
 expect(rule).toHaveProperty(\hash\);
 expect(rule).toHaveProperty(\status\);
 expect([\ACTIVE\, \PENDING\, \DEPRECATED\, \MODIFIED\]).toContain(rule.status);
 expect(rule).toHaveProperty(\market\);
 expect(rule).toHaveProperty(\conditions\);
 expect(rule).toHaveProperty(\actions\);
 }

 // Verify change requests structure
 for (const cr of governanceResponse.data.changeRequests) {
 expect(cr).toHaveProperty(\id\);
 expect(cr).toHaveProperty(\title\);
 expect(cr).toHaveProperty(\status\);
 expect([\DRAFT\, \PENDING_REVIEW\, \APPROVED\, \REJECTED\, \IMPLEMENTED\, \ROLLED_BACK\]).toContain(cr.status);
 expect(cr).toHaveProperty(\requestedBy\);
 expect(cr).toHaveProperty(\requestedAt\);
 expect(cr).toHaveProperty(\impactAnalysis\);
 expect(cr).toHaveProperty(\approvals\);
 expect(Array.isArray(cr.approvals)).toBe(true);
 }

 // Verify release candidates structure
 for (const rc of governanceResponse.data.releaseCandidates) {
 expect(rc).toHaveProperty(\id\);
 expect(rc).toHaveProperty(\version\);
 expect(rc).toHaveProperty(\status\);
 expect([\BUILDING\, \TESTING\, \STAGING\, \READY\, \DEPLOYED\, \ROLLED_BACK\]).toContain(rc.status);
 expect(rc).toHaveProperty(\artifacts\);
 expect(Array.isArray(rc.artifacts)).toBe(true);
 expect(rc).toHaveProperty(\testResults\);
 expect(rc).toHaveProperty(\approvals\);
 }

 // Verify drift alerts structure
 for (const alert of governanceResponse.data.driftAlerts) {
 expect(alert).toHaveProperty(\id\);
 expect(alert).toHaveProperty(\type\);
 expect([\CONFIG\, \POLICY\, \DESCRIPTOR\, \MARKET_RULE\, \FEATURE_FLAG\]).toContain(alert.type);
 expect(alert).toHaveProperty(\severity\);
 expect([\INFO\, \WARNING\, \CRITICAL\]).toContain(alert.severity);
 expect(alert).toHaveProperty(\entityId\);
 expect(alert).toHaveProperty(\detectedAt\);
 expect(alert).toHaveProperty(\status\);
 expect([\ACTIVE\, \ACKNOWLEDGED\, \RESOLVED\, \ESCALATED\]).toContain(alert.status);
 }

 // Verify incidents structure
 for (const incident of governanceResponse.data.incidents) {
 expect(incident).toHaveProperty(\id\);
 expect(incident).toHaveProperty(\title\);
 expect(incident).toHaveProperty(\severity\);
 expect([\SEV1\, \SEV2\, \SEV3\, \SEV4\]).toContain(incident.severity);
 expect(incident).toHaveProperty(\status\);
 expect([\OPEN\, \INVESTIGATING\, \MITIGATED\, \RESOLVED\, \CLOSED\]).toContain(incident.status);
 expect(incident).toHaveProperty(\detectedAt\);
 expect(incident).toHaveProperty(\affectedEntities\);
 expect(Array.isArray(incident.affectedEntities)).toBe(true);
 }

 // Verify override ledger structure
 for (const override of governanceResponse.data.overrideLedger) {
 expect(override).toHaveProperty(\id\);
 expect(override).toHaveProperty(\entityType\);
 expect([\DESCRIPTOR\, \POLICY\, \FEATURE_FLAG\, \MARKET_RULE\]).toContain(override.entityType);
 expect(override).toHaveProperty(\entityId\);
 expect(override).toHaveProperty(\overriddenBy\);
 expect(override).toHaveProperty(\overriddenAt\);
 expect(override).toHaveProperty(\reason\);
 expect(override).toHaveProperty(\expiresAt\);
 expect(override).toHaveProperty(\status\);
 expect([\ACTIVE\, \EXPIRED\, \REVOKED\]).toContain(override.status);
 }

 // Verify pending review structure
 for (const review of governanceResponse.data.pendingReview) {
 expect(review).toHaveProperty(\id\);
 expect(review).toHaveProperty(\entityType\);
 expect(review).toHaveProperty(\entityId\);
 expect(review).toHaveProperty(\submittedBy\);
 expect(review).toHaveProperty(\submittedAt\);
 expect(review).toHaveProperty(\reviewers\);
 expect(Array.isArray(review.reviewers)).toBe(true);
 expect(review).toHaveProperty(\status\);
 expect([\PENDING\, \IN_REVIEW\, \APPROVED\, \REJECTED\]).toContain(review.status);
 }

 // Verify dynamic rule evidence structure
 for (const evidence of governanceResponse.data.dynamicRuleEvidence) {
 expect(evidence).toHaveProperty(\id\);
 expect(evidence).toHaveProperty(\ruleId\);
 expect(evidence).toHaveProperty(\triggeredAt\);
 expect(evidence).toHaveProperty(\context\);
 expect(evidence).toHaveProperty(\decision\);
 expect(evidence).toHaveProperty(\auditRefs\);
 expect(Array.isArray(evidence.auditRefs)).toBe(true);
 }

 // Drift detection for config version
 const driftFindings = await verifyDriftForConfigVersion(\admin-governance\, personaRole, {
 version: governanceResponse.data.configVersion,
 descriptors: governanceResponse.data.descriptors,
 policies: governanceResponse.data.policies,
 featureFlags: governanceResponse.data.featureFlags,
 marketRules: governanceResponse.data.marketRules,
 });
 await runDriftReport(driftFindings, \1.0.0\, \admin-governance\, personaRole);

 // Navigate to Admin Governance UI
 const navResult = await helper.navigateTo(\/admin/governance\);
 expect(navResult.success).toBe(true);

 // Verify UI sections
 await expect(page.locator('[data-testid=\governance-config-version\]')).toBeVisible();
 await expect(page.locator('[data-testid=\governance-descriptors-tab\]')).toBeVisible();
 await expect(page.locator('[data-testid=\governance-policies-tab\]')).toBeVisible();
 await expect(page.locator('[data-testid=\governance-feature-flags-tab\]')).toBeVisible();
 await expect(page.locator('[data-testid=\governance-market-rules-tab\]')).toBeVisible();
 await expect(page.locator('[data-testid=\governance-change-requests-tab\]')).toBeVisible();
 await expect(page.locator('[data-testid=\governance-release-candidates-tab\]')).toBeVisible();
 await expect(page.locator('[data-testid=\governance-drift-alerts-tab\]')).toBeVisible();
 await expect(page.locator('[data-testid=\governance-incidents-tab\]')).toBeVisible();
 await expect(page.locator('[data-testid=\governance-override-ledger-tab\]')).toBeVisible();
 await expect(page.locator('[data-testid=\governance-pending-review-tab\]')).toBeVisible();
 await expect(page.locator('[data-testid=\governance-dynamic-rule-evidence-tab\]')).toBeVisible();

 // Test descriptor tab
 await page.click('[data-testid=\governance-descriptors-tab\]');
 await expect(page.locator('[data-testid=\descriptors-table\]')).toBeVisible();
 const descriptorRows = page.locator('[data-testid=\descriptor-row\]');
 expect(await descriptorRows.count()).toBeGreaterThanOrEqual(governanceResponse.data.descriptors.length);

 // Test policies tab
 await page.click('[data-testid=\governance-policies-tab\]');
 await expect(page.locator('[data-testid=\policies-table\]')).toBeVisible();

 // Test feature flags tab
 await page.click('[data-testid=\governance-feature-flags-tab\]');
 await expect(page.locator('[data-testid=\feature-flags-table\]')).toBeVisible();

 // Test market rules tab
 await page.click('[data-testid=\governance-market-rules-tab\]');
 await expect(page.locator('[data-testid=\market-rules-table\]')).toBeVisible();

 // Test change requests tab
 await page.click('[data-testid=\governance-change-requests-tab\]');
 await expect(page.locator('[data-testid=\change-requests-table\]')).toBeVisible();

 // Test release candidates tab
 await page.click('[data-testid=\governance-release-candidates-tab\]');
 await expect(page.locator('[data-testid=\release-candidates-table\]')).toBeVisible();

 // Test drift alerts tab
 await page.click('[data-testid=\governance-drift-alerts-tab\]');
 await expect(page.locator('[data-testid=\drift-alerts-table\]')).toBeVisible();

 // Test incidents tab
 await page.click('[data-testid=\governance-incidents-tab\]');
 await expect(page.locator('[data-testid=\incidents-table\]')).toBeVisible();

 // Test override ledger tab
 await page.click('[data-testid=\governance-override-ledger-tab\]');
 await expect(page.locator('[data-testid=\override-ledger-table\]')).toBeVisible();

 // Test pending review tab
 await page.click('[data-testid=\governance-pending-review-tab\]');
 await expect(page.locator('[data-testid=\pending-review-table\]')).toBeVisible();

 // Test dynamic rule evidence tab
 await page.click('[data-testid=\governance-dynamic-rule-evidence-tab\]');
 await expect(page.locator('[data-testid=\dynamic-rule-evidence-table\]')).toBeVisible();

 // Take headed mode screenshot for demo
 if (!HEADLESS_MODE) {
 await helper.takeScreenshot(dmin-governance--demo);
 }
 });

 test(${personaRole} - Admin Governance: config version drift detection, async () => {
 const ctx = apiHelper.createContext(personaRole);
 ctx.traceId = globalTraceId;

 const governanceResponse = await apiHelper.getAdminGovernance(ctx);
 expect(governanceResponse.status).toBe(200);

 const driftFindings = await verifyDriftForConfigVersion(\admin-governance-config-version\, personaRole, {
 version: governanceResponse.data.configVersion,
 descriptors: governanceResponse.data.descriptors,
 policies: governanceResponse.data.policies,
 featureFlags: governanceResponse.data.featureFlags,
 marketRules: governanceResponse.data.marketRules,
 });
 await runDriftReport(driftFindings, \1.0.0\, \admin-governance-config-version\, personaRole);
 });
 }

 // Test access control - restricted personas should be blocked
 for (const personaRole of ALL_PERSONAS) {
 const persona = getPersona(personaRole);
 const restrictedRoutes = getRestrictedRoutes(personaRole);

 if (restrictedRoutes.includes(\/admin/governance\)) {
 test(${personaRole} - Admin Governance: access denied for restricted persona, async ({ page }) => {
 const helper = uiHelper(page);
 const ctx = apiHelper.createContext(personaRole);
 ctx.traceId = globalTraceId;

 const navResult = await helper.navigateTo(\/admin/governance\);
 expect(navResult.success).toBe(false);
 expect(navResult.url).toMatch(/403|unauthorized|login/);
 });
 }
 }
});
// ========================================================================
// PRODUCT CATALOG MANAGER TESTS
// ========================================================================
describe(" Product Catalog Manager\, () => {
 for (const personaRole of GOVERNANCE_PERSONAS) {
 const persona = getPersona(personaRole);

 test(${personaRole} - Product Catalog Manager: areas, lifecycle, snapshots, audit evidence, async ({ page }) => {
 const helper = uiHelper(page);
 const ctx = apiHelper.createContext(personaRole);
 ctx.traceId = globalTraceId;

 const catalogResponse = await apiHelper.getProductCatalogManager(ctx);
 expect(catalogResponse.status).toBe(200);
 expect(catalogResponse.data).toBeDefined();
 expect(catalogResponse.data.areas).toBeDefined();
 expect(catalogResponse.data.lifecycle).toBeDefined();
 expect(catalogResponse.data.snapshots).toBeDefined();
 expect(catalogResponse.data.auditEvidence).toBeDefined();

 // Verify areas structure
 for (const area of catalogResponse.data.areas) {
 expect(area).toHaveProperty(\id\);
 expect(area).toHaveProperty(\name\);
 expect(area).toHaveProperty(\description\);
 expect(area).toHaveProperty(\products\);
 expect(Array.isArray(area.products)).toBe(true);
 expect(area).toHaveProperty(\status\);
 expect([\ACTIVE\, \INACTIVE\, \ARCHIVED\]).toContain(area.status);
 expect(area).toHaveProperty(\governanceDescriptorId\);
 expect(area).toHaveProperty(\lastModified\);
 expect(area).toHaveProperty(\modifiedBy\);
 }

 // Verify lifecycle structure
 for (const lifecycle of catalogResponse.data.lifecycle) {
 expect(lifecycle).toHaveProperty(\productId\);
 expect(lifecycle).toHaveProperty(\phase\);
 expect([\DRAFT\, \REVIEW\, \APPROVED\, \ACTIVE\, \DEPRECATED\, \RETIRED\]).toContain(lifecycle.phase);
 expect(lifecycle).toHaveProperty(\enteredAt\);
 expect(lifecycle).toHaveProperty(\enteredBy\);
 expect(lifecycle).toHaveProperty(\exitCriteria\);
 expect(Array.isArray(lifecycle.exitCriteria)).toBe(true);
 expect(lifecycle).toHaveProperty(\approvals\);
 expect(Array.isArray(lifecycle.approvals)).toBe(true);
 }

 // Verify snapshots structure
 for (const snapshot of catalogResponse.data.snapshots) {
 expect(snapshot).toHaveProperty(\id\);
 expect(snapshot).toHaveProperty(\version\);
 expect(snapshot).toHaveProperty(\capturedAt\);
 expect(snapshot).toHaveProperty(\capturedBy\);
 expect(snapshot).toHaveProperty(\products\);
 expect(Array.isArray(snapshot.products)).toBe(true);
 expect(snapshot).toHaveProperty(\hash\);
 expect(snapshot).toHaveProperty(\description\);
 }

 // Verify audit evidence structure
 for (const evidence of catalogResponse.data.auditEvidence) {
 expect(evidence).toHaveProperty(\id\);
 expect(evidence).toHaveProperty(\entityType\);
 expect([\AREA\, \PRODUCT\, \LIFECYCLE\, \SNAPSHOT\]).toContain(evidence.entityType);
 expect(evidence).toHaveProperty(\entityId\);
 expect(evidence).toHaveProperty(\action\);
 expect([\CREATE\, \UPDATE\, \DELETE\, \PHASE_TRANSITION\, \SNAPSHOT_CAPTURE\]).toContain(evidence.action);
 expect(evidence).toHaveProperty(\performedBy\);
 expect(evidence).toHaveProperty(\performedAt\);
 expect(evidence).toHaveProperty(\changes\);
 expect(Array.isArray(evidence.changes)).toBe(true);
 expect(evidence).toHaveProperty(\auditRefs\);
 expect(Array.isArray(evidence.auditRefs)).toBe(true);
 }

 // Navigate to Product Catalog Manager UI
 const navResult = await helper.navigateTo(\/admin/products/catalog\);
 expect(navResult.success).toBe(true);

 // Verify UI sections
 await expect(page.locator('[data-testid=\catalog-areas-tab\]')).toBeVisible();
 await expect(page.locator('[data-testid=\catalog-lifecycle-tab\]')).toBeVisible();
 await expect(page.locator('[data-testid=\catalog-snapshots-tab\]')).toBeVisible();
 await expect(page.locator('[data-testid=\catalog-audit-evidence-tab\]')).toBeVisible();

 // Test areas tab
 await page.click('[data-testid=\catalog-areas-tab\]');
 await expect(page.locator('[data-testid=\areas-tree\]')).toBeVisible();
 await expect(page.locator('[data-testid=\area-products-table\]')).toBeVisible();

 // Test lifecycle tab
 await page.click('[data-testid=\catalog-lifecycle-tab\]');
 await expect(page.locator('[data-testid=\lifecycle-table\]')).toBeVisible();

 // Test snapshots tab
 await page.click('[data-testid=\catalog-snapshots-tab\]');
 await expect(page.locator('[data-testid=\snapshots-table\]')).toBeVisible();

 // Test audit evidence tab
 await page.click('[data-testid=\catalog-audit-evidence-tab\]');
 await expect(page.locator('[data-testid=\audit-evidence-table\]')).toBeVisible();

 // Take headed mode screenshot for demo
 if (!HEADLESS_MODE) {
 await helper.takeScreenshot(product-catalog--demo);
 }
 });

 test(${personaRole} - Product Catalog Manager: create product catalog entry, async () => {
 const ctx = apiHelper.createContext(personaRole);
 ctx.traceId = globalTraceId;

 const newProduct = {
 id: TEST-PRODUCT-,
 name: \Test Product\,
 description: \E2E test product\,
 areaId: \CONV\,
 productType: \CONVENTIONAL\,
 eligibilityRules: [],
 pricingRules: [],
 governanceDescriptorId: \GOV-DESC-001\,
 status: \DRAFT\,
 };

 const createResponse = await apiHelper.createProductCatalogEntry(ctx, newProduct);
 expect(createResponse.status).toBe(201);
 expect(createResponse.data.id).toBe(newProduct.id);
 expect(createResponse.data.status).toBe(\DRAFT\);
 });
 }

 // Test access control
 for (const personaRole of ALL_PERSONAS) {
 const persona = getPersona(personaRole);
 const restrictedRoutes = getRestrictedRoutes(personaRole);

 if (restrictedRoutes.includes(\/admin/products/catalog\)) {
 test(${personaRole} - Product Catalog Manager: access denied for restricted persona, async ({ page }) => {
 const helper = uiHelper(page);
 const ctx = apiHelper.createContext(personaRole);
 ctx.traceId = globalTraceId;

 const navResult = await helper.navigateTo(\/admin/products/catalog\);
 expect(navResult.success).toBe(false);
 expect(navResult.url).toMatch(/403|unauthorized|login/);
 });
 }
 }
});
// ========================================================================
// COMPLIANCE EVIDENCE TESTS
// ========================================================================
describe(" Compliance Evidence\, () => {
 for (const personaRole of COMPLIANCE_PERSONAS) {
 const persona = getPersona(personaRole);

 test(${personaRole} - Compliance Evidence: artifacts, decisions, advisory reviews, fair lending, privacy requests, security events, alerts, retention controls, async ({ page }) => {
 const helper = uiHelper(page);
 const ctx = apiHelper.createContext(personaRole);
 ctx.traceId = globalTraceId;

 const complianceResponse = await apiHelper.getComplianceEvidence(ctx);
 expect(complianceResponse.status).toBe(200);
 expect(complianceResponse.data).toBeDefined();
 expect(complianceResponse.data.overallStatus).toBeDefined();
 expect([\COMPLIANT\, \NON_COMPLIANT\, \PARTIAL\, \UNDER_REVIEW\]).toContain(complianceResponse.data.overallStatus);
 expect(complianceResponse.data.artifacts).toBeDefined();
 expect(complianceResponse.data.decisions).toBeDefined();
 expect(complianceResponse.data.advisoryReviews).toBeDefined();
 expect(complianceResponse.data.fairLending).toBeDefined();
 expect(complianceResponse.data.privacyRequests).toBeDefined();
 expect(complianceResponse.data.securityEvents).toBeDefined();
 expect(complianceResponse.data.alerts).toBeDefined();
 expect(complianceResponse.data.retentionControls).toBeDefined();

 // Verify artifacts structure
 for (const artifact of complianceResponse.data.artifacts) {
 expect(artifact).toHaveProperty(\id\);
 expect(artifact).toHaveProperty(\type\);
 expect([\POLICY\, \PROCEDURE\, \REPORT\, \ASSESSMENT\, \ATTESTATION\, \EVIDENCE\]).toContain(artifact.type);
 expect(artifact).toHaveProperty(\status\);
 expect([\CURRENT\, \EXPIRED\, \DRAFT\, \ARCHIVED\]).toContain(artifact.status);
 expect(artifact).toHaveProperty(\lastReviewed\);
 expect(artifact).toHaveProperty(\reviewedBy\);
 expect(artifact).toHaveProperty(\nextReviewDue\);
 expect(artifact).toHaveProperty(\auditRefs\);
 expect(Array.isArray(artifact.auditRefs)).toBe(true);
 }

 // Verify decisions structure
 for (const decision of complianceResponse.data.decisions) {
 expect(decision).toHaveProperty(\id\);
 expect(decision).toHaveProperty(\title\);
 expect(decision).toHaveProperty(\status\);
 expect([\PENDING\, \APPROVED\, \REJECTED\, \DEFERRED\]).toContain(decision.status);
 expect(decision).toHaveProperty(\decidedBy\);
 expect(decision).toHaveProperty(\decidedAt\);
 expect(decision).toHaveProperty(\rationale\);
 expect(decision).toHaveProperty(\auditRefs\);
 expect(Array.isArray(decision.auditRefs)).toBe(true);
 }

 // Verify advisory reviews structure
 for (const review of complianceResponse.data.advisoryReviews) {
 expect(review).toHaveProperty(\id\);
 expect(review).toHaveProperty(\topic\);
 expect(review).toHaveProperty(\reviewer\);
 expect(review).toHaveProperty(\reviewedAt\);
 expect(review).toHaveProperty(\findings\);
 expect(Array.isArray(review.findings)).toBe(true);
 expect(review).toHaveProperty(\recommendations\);
 expect(Array.isArray(review.recommendations)).toBe(true);
 expect(review).toHaveProperty(\status\);
 expect([\OPEN\, \IN_PROGRESS\, \COMPLETED\, \CLOSED\]).toContain(review.status);
 expect(review).toHaveProperty(\auditRefs\);
 expect(Array.isArray(review.auditRefs)).toBe(true);
 }

 // Verify fair lending structure
 for (const metric of complianceResponse.data.fairLending) {
 expect(metric).toHaveProperty(\metricId\);
 expect(metric).toHaveProperty(\name\);
 expect(metric).toHaveProperty(\value\);
 expect(metric).toHaveProperty(\threshold\);
 expect(metric).toHaveProperty(\status\);
 expect([\WITHIN_THRESHOLD\, \EXCEEDS_THRESHOLD\, \DATA_INSUFFICIENT\]).toContain(metric.status);
 expect(metric).toHaveProperty(\calculatedAt\);
 expect(metric).toHaveProperty(\auditRefs\);
 expect(Array.isArray(metric.auditRefs)).toBe(true);
 }

 // Verify privacy requests structure
 for (const request of complianceResponse.data.privacyRequests) {
 expect(request).toHaveProperty(\id\);
 expect(request).toHaveProperty(\type\);
 expect([\ACCESS\, \DELETION\, \RECTIFICATION\, \PORTABILITY\, \RESTRICTION\, \OBJECTION\]).toContain(request.type);
 expect(request).toHaveProperty(\status\);
 expect([\PENDING\, \IN_PROGRESS\, \COMPLETED\, \REJECTED\, \EXPIRED\]).toContain(request.status);
 expect(request).toHaveProperty(\requestedAt\);
 expect(request).toHaveProperty(\slaDays\);
 expect(request).toHaveProperty(\assignedTo\);
 expect(request).toHaveProperty(\auditRefs\);
 expect(Array.isArray(request.auditRefs)).toBe(true);
 }

 // Verify security events structure
 for (const event of complianceResponse.data.securityEvents) {
 expect(event).toHaveProperty(\id\);
 expect(event).toHaveProperty(\type\);
 expect(event).toHaveProperty(\severity\);
 expect([\LOW\, \MEDIUM\, \HIGH\, \CRITICAL\]).toContain(event.severity);
 expect(event).toHaveProperty(\status\);
 expect([\OPEN\, \INVESTIGATING\, \MITIGATED\, \RESOLVED\, \CLOSED\]).toContain(event.status);
 expect(event).toHaveProperty(\detectedAt\);
 expect(event).toHaveProperty(\affectedSystems\);
 expect(Array.isArray(event.affectedSystems)).toBe(true);
 expect(event).toHaveProperty(\auditRefs\);
 expect(Array.isArray(event.auditRefs)).toBe(true);
 }

 // Verify alerts structure
 for (const alert of complianceResponse.data.alerts) {
 expect(alert).toHaveProperty(\id\);
 expect(alert).toHaveProperty(\type\);
 expect(alert).toHaveProperty(\severity\);
 expect([\INFO\, \WARNING\, \CRITICAL\]).toContain(alert.severity);
 expect(alert).toHaveProperty(\message\);
 expect(alert).toHaveProperty(\triggeredAt\);
 expect(alert).toHaveProperty(\status\);
 expect([\ACTIVE\, \ACKNOWLEDGED\, \RESOLVED\, \ESCALATED\]).toContain(alert.status);
 expect(alert).toHaveProperty(\auditRefs\);
 expect(Array.isArray(alert.auditRefs)).toBe(true);
 }

 // Verify retention controls structure
 for (const control of complianceResponse.data.retentionControls) {
 expect(control).toHaveProperty(\policyId\);
 expect(control).toHaveProperty(\name\);
 expect(control).toHaveProperty(\status\);
 expect([\COMPLIANT\, \NON_COMPLIANT\, \PARTIAL\]).toContain(control.status);
 expect(control).toHaveProperty(\complianceRate\);
 expect(control.complianceRate).toBeGreaterThanOrEqual(0);
 expect(control.complianceRate).toBeLessThanOrEqual(1);
 expect(control).toHaveProperty(\lastAssessed\);
 expect(control).toHaveProperty(\auditRefs\);
 expect(Array.isArray(control.auditRefs)).toBe(true);
 }

 // Drift detection for compliance status
 const driftFindings = await verifyDriftForComplianceStatus(\compliance-evidence\, personaRole, {
 overallStatus: complianceResponse.data.overallStatus,
 artifacts: complianceResponse.data.artifacts,
 decisions: complianceResponse.data.decisions,
 fairLendingMetrics: complianceResponse.data.fairLending,
 privacyRequests: complianceResponse.data.privacyRequests,
 securityEvents: complianceResponse.data.securityEvents,
 alerts: complianceResponse.data.alerts,
 retentionControls: complianceResponse.data.retentionControls,
 });
 await runDriftReport(driftFindings, \1.0.0\, \compliance-evidence\, personaRole);

 // Navigate to Compliance Evidence UI
 const navResult = await helper.navigateTo(\/compliance/evidence\);
 expect(navResult.success).toBe(true);

 // Verify UI sections
 await expect(page.locator('[data-testid=\compliance-overall-status\]')).toBeVisible();
 await expect(page.locator('[data-testid=\compliance-artifacts-tab\]')).toBeVisible();
 await expect(page.locator('[data-testid=\compliance-decisions-tab\]')).toBeVisible();
 await expect(page.locator('[data-testid=\compliance-advisory-reviews-tab\]')).toBeVisible();
 await expect(page.locator('[data-testid=\compliance-fair-lending-tab\]')).toBeVisible();
 await expect(page.locator('[data-testid=\compliance-privacy-requests-tab\]')).toBeVisible();
 await expect(page.locator('[data-testid=\compliance-security-events-tab\]')).toBeVisible();
 await expect(page.locator('[data-testid=\compliance-alerts-tab\]')).toBeVisible();
 await expect(page.locator('[data-testid=\compliance-retention-controls-tab\]')).toBeVisible();

 // Test artifacts tab
 await page.click('[data-testid=\compliance-artifacts-tab\]');
 await expect(page.locator('[data-testid=\artifacts-table\]')).toBeVisible();

 // Test decisions tab
 await page.click('[data-testid=\compliance-decisions-tab\]');
 await expect(page.locator('[data-testid=\decisions-table\]')).toBeVisible();

 // Test advisory reviews tab
 await page.click('[data-testid=\compliance-advisory-reviews-tab\]');
 await expect(page.locator('[data-testid=\advisory-reviews-table\]')).toBeVisible();

 // Test fair lending tab
 await page.click('[data-testid=\compliance-fair-lending-tab\]');
 await expect(page.locator('[data-testid=\fair-lending-metrics-table\]')).toBeVisible();

 // Test privacy requests tab
 await page.click('[data-testid=\compliance-privacy-requests-tab\]');
 await expect(page.locator('[data-testid=\privacy-requests-table\]')).toBeVisible();

 // Test security events tab
 await page.click('[data-testid=\compliance-security-events-tab\]');
 await expect(page.locator('[data-testid=\security-events-table\]')).toBeVisible();

 // Test alerts tab
 await page.click('[data-testid=\compliance-alerts-tab\]');
 await expect(page.locator('[data-testid=\compliance-alerts-table\]')).toBeVisible();

 // Test retention controls tab
 await page.click('[data-testid=\compliance-retention-controls-tab\]');
 await expect(page.locator('[data-testid=\retention-controls-table\]')).toBeVisible();

 // Take headed mode screenshot for demo
 if (!HEADLESS_MODE) {
 await helper.takeScreenshot(compliance-evidence--demo);
 }
 });

 test(${personaRole} - Compliance Evidence: fair lending metrics threshold validation, async () => {
 const ctx = apiHelper.createContext(personaRole);
 ctx.traceId = globalTraceId;

 const complianceResponse = await apiHelper.getComplianceEvidence(ctx);
 expect(complianceResponse.status).toBe(200);

 // Verify all fair lending metrics are within threshold
 for (const metric of complianceResponse.data.fairLending) {
 if (metric.status === \EXCEEDS_THRESHOLD\) {
 throw new Error(Fair lending metric exceeds threshold: > );
 }
 }
 });

 test(${personaRole} - Compliance Evidence: privacy request SLA compliance, async () => {
 const ctx = apiHelper.createContext(personaRole);
 ctx.traceId = globalTraceId;

 const complianceResponse = await apiHelper.getComplianceEvidence(ctx);
 expect(complianceResponse.status).toBe(200);

 // Verify privacy requests are within SLA
 const overdueRequests = complianceResponse.data.privacyRequests.filter(
 (r: any) => r.slaDays > 30 && r.status !== \COMPLETED\
 );
 expect(overdueRequests.length).toBe(0);
 });

 test(${personaRole} - Compliance Evidence: security event response time, async () => {
 const ctx = apiHelper.createContext(personaRole);
 ctx.traceId = globalTraceId;

 const complianceResponse = await apiHelper.getComplianceEvidence(ctx);
 expect(complianceResponse.status).toBe(200);

 // Verify no critical security events are open
 const criticalOpenEvents = complianceResponse.data.securityEvents.filter(
 (e: any) => e.severity === \CRITICAL\ && e.status === \OPEN\
 );
 expect(criticalOpenEvents.length).toBe(0);
 });
 }

 // Test access control
 for (const personaRole of ALL_PERSONAS) {
 const persona = getPersona(personaRole);
 const restrictedRoutes = getRestrictedRoutes(personaRole);

 if (restrictedRoutes.includes(\/compliance/evidence\)) {
 test(${personaRole} - Compliance Evidence: access denied for restricted persona, async ({ page }) => {
 const helper = uiHelper(page);
 const ctx = apiHelper.createContext(personaRole);
 ctx.traceId = globalTraceId;

 const navResult = await helper.navigateTo(\/compliance/evidence\);
 expect(navResult.success).toBe(false);
 expect(navResult.url).toMatch(/403|unauthorized|login/);
 });
 }
 }
});
