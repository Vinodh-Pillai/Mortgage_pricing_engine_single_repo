import '@testing-library/jest-dom/vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
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
              events: ['AdminGovernanceOpened'],
              fallbackReason: 'Configured governance, policy, release, drift, incident, and audit services are unavailable; this response carries non-secret UI fallback records only.',
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

        if (url.endsWith('/offers')) {
          return {
            ok: true,
            json: async () => ({
              runId: 'run-test',
              status: 'UPSTREAM_EXPLAINABILITY_REQUIRED',
              offers: [],
              sortOptions: ['payment', 'apr', 'confidence'],
              selectedOfferId: null,
              commitBlocked: true,
              fallbackReason: 'Offer comparison requires a configured quote-service offers and explainability contract before commit.',
              uiTraceId: 'brw-s02-local-trace',
              events: ['OfferListRendered'],
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

        return {
          ok: true,
          status: 201,
          json: async () => ({
            runId: 'run-test',
            status: 'CREATED',
            nextRoute: '/quote/run-test/offers',
            validationSummary: { passed: true, status: 'PASSED', message: 'Required borrower intake fields are present.', blockers: {} },
            uiTraceId: 'brw-s01-local-trace',
            events: ['UIFlowOpened', 'BorrowerIntakeSubmitted'],
            fallbackMode: false,
            dependencyStatus: 'UPSTREAM_CONTRACT_NOT_CONFIGURED',
          }),
        };
      }),
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('renders accessible borrower intake and calls the BFF health boundary on load', async () => {
    render(<App />);

    expect(screen.getByText('Skip to main content')).toHaveAttribute('href', '#main-content');
    expect(screen.getByRole('banner')).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Main navigation' })).toBeInTheDocument();
    expect(screen.getByRole('main')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Start a quote run' })).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: /Borrower name/i })).toHaveAttribute('aria-invalid', 'false');

    await waitFor(() => expect(screen.getByText('BFF reachable')).toBeInTheDocument());
    expect(fetch).toHaveBeenCalledTimes(1);
    expect(fetch).toHaveBeenCalledWith('/api/ui/health', { headers: { Accept: 'application/json' } });
  });

  it('keeps invalid borrower intake on /quote/start and focuses the first invalid field', async () => {
    render(<App />);

    fireEvent.click(screen.getByRole('button', { name: 'Start quote run' }));

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
    fireEvent.click(screen.getByRole('button', { name: 'Start quote run' }));

    expect(await screen.findByRole('heading', { name: 'Offer comparison' })).toBeInTheDocument();
    expect(window.location.pathname).toBe('/quote/run-test/offers');
    expect(fetch).toHaveBeenCalledWith('/api/v1/tenants/ui-preview-tenant/quote-runs', expect.objectContaining({ method: 'POST' }));
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
    fireEvent.click(screen.getByRole('button', { name: 'Start quote run' }));

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
    expect(within(queue).getByText('SLA contract required')).toBeInTheDocument();
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
    expect(await screen.findByText('Downstream executed: no')).toBeInTheDocument();
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

  it('renders bounded missing explanation fallback and blocks commit for offers without explainability', async () => {
    window.history.pushState({}, '', '/quote/run-test/offers');

    render(<App />);

    expect(await screen.findByText('Explanation data required')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Offer comparison' })).toBeInTheDocument();
    expect(screen.getByText(/No comparable offers are loaded from pricing-bff/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Continue to lock workflow' })).toBeDisabled();
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
              { offerId: 'offer-b', rank: 2, productLabel: 'Offer B', payment: '2000', apr: '6.5', confidence: 'medium', rationaleChips: ['BFF supplied'], scenarioFlags: ['scenario-linked'], explanationStatus: 'AVAILABLE', sourceScenarioId: 'scenario-test' },
              { offerId: 'offer-a', rank: 1, productLabel: 'Offer A', payment: '1000', apr: '6.0', confidence: 'high', rationaleChips: ['BFF supplied'], scenarioFlags: [], explanationStatus: 'AVAILABLE', sourceScenarioId: 'scenario-test' },
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
        return { ok: true, json: async () => ({ runId: 'run-test', offerId: 'offer-a', status: 'AVAILABLE', rationaleLines: ['Rationale supplied by BFF'], scenarioFlags: [], commitBlocked: false, message: '', uiTraceId: 'brw-s02-local-trace' }) } as Response;
      }
      if (url.endsWith('/offer-a/select') && init?.method === 'POST') {
        return { ok: true, json: async () => ({ runId: 'run-test', selectedOfferId: 'offer-a', status: 'SELECTED', nextRoute: '/quote/run-test/lock', sourceScenarioId: 'scenario-test', auditRef: 'audit-test', message: 'selected', uiTraceId: 'brw-s02-local-trace', events: ['OfferSelectionMade'] }) } as Response;
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
        return { ok: true, json: async () => ({ runId: 'run-test', selectedOfferId: 'offer-a', status: 'READY', lockDisabled: false, blockers: [], disclosureText: 'Confirm lock disclosure.', nextAction: 'Confirm lock request', uiTraceId: 'brw-s04-local-trace', events: ['LockAttempted'], dependencyStatus: 'UPSTREAM_LOCK_CONTRACT_NOT_CONFIGURED' }) } as Response;
      }
      if (url.endsWith('/lock/confirm') && init?.method === 'POST') {
        return { ok: true, status: 201, json: async () => ({ runId: 'run-test', selectedOfferId: 'offer-a', status: 'CONFIRMED', lockId: 'lock-test', lockStatus: 'LOCK_REQUEST_RECORDED', expiresAt: 'Pending configured lock-service response', statusRoute: '/quote/run-test/status', message: 'Lock request recorded for selected offer offer-a.', uiTraceId: 'brw-s04-local-trace', events: ['LockSuccess'], blockers: [] }) } as Response;
      }
      throw new Error(`Unexpected fetch ${url}`);
    });

    render(<App />);

    expect(await screen.findByText('Ready to confirm')).toBeInTheDocument();
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
    expect(screen.getByText('Support handoff route: /partners/support/reprice')).toBeInTheDocument();
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
    expect(screen.getByText('Configured upstream repricing remains outside this UI fallback slice.')).toBeInTheDocument();
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

    expect(await screen.findByRole('heading', { name: 'Partner transport reliability' })).toBeInTheDocument();
    expect(await screen.findByText('latest 30 events')).toBeInTheDocument();
    expect(screen.getByText('DLQ size requires configured integration-service metrics')).toBeInTheDocument();
    expect(screen.getByText('UPSTREAM_PARTNER_CONTRACT_NOT_CONFIGURED')).toBeInTheDocument();
    expect(screen.getByText('Replay requires request correlation and explicit idempotency confirmation before it can be recorded.')).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Replay correlation id'), { target: { value: 'corr-123' } });
    fireEvent.click(screen.getByLabelText('I confirm idempotency for this replay request.'));
    fireEvent.click(screen.getByRole('button', { name: 'Request replay' }));

    expect(await screen.findByText('Webhook replay requested')).toBeInTheDocument();
    expect(screen.getByText('Downstream executed: no')).toBeInTheDocument();
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
    expect(screen.getByText('policy-version-required / policy-digest-required / jurisdiction-config-required')).toBeInTheDocument();
    expect(screen.getByText('CHAIN_CONTINUITY_UNVERIFIED')).toBeInTheDocument();
    expect(screen.getByText('RULE_SOURCE_REQUIRED')).toBeInTheDocument();
    expect(screen.getByText('Identity: unverified')).toBeInTheDocument();
    expect(screen.getByText('Log record: logRecordId-required')).toBeInTheDocument();
    expect(screen.getByText('P2 · workflow · missing_evidence')).toBeInTheDocument();
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

  it('renders admin governance release gates with open decisions as blockers', async () => {
    window.history.pushState({}, '', '/admin/release-readiness');

    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Admin governance and release gate controls' })).toBeInTheDocument();
    expect(await screen.findByText('Release status RED')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Deploy release candidate' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Execute rollback' })).toBeDisabled();
    expect(screen.getByRole('table', { name: 'Release gates' })).toBeInTheDocument();
    expect(screen.getByText('OD-001 unresolved blocks RBAC source and role-to-privilege ingestion.')).toBeInTheDocument();
    expect(screen.getByText('OD-001 · BLOCKING')).toBeInTheDocument();
    expect(screen.getByText('OD-002 · BLOCKING')).toBeInTheDocument();
    expect(screen.getByText('OD-004 · BLOCKING')).toBeInTheDocument();
    expect(screen.getByText('OD-005 · BLOCKING')).toBeInTheDocument();
    expect(screen.getByText('Feature flag activation blocked')).toBeInTheDocument();
    expect(screen.getByText('Market rule promotion blocked')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Approve change request' })).toBeDisabled();
    expect(screen.getByText('Configured baseline and alert threshold are required; no numeric threshold is inferred.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Close incident' })).toBeDisabled();
    expect(screen.getByRole('table', { name: 'Override audit ledger' })).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith('/api/v1/admin/governance', expect.any(Object));
  });
});
