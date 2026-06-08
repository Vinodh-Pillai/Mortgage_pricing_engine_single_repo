import { type FormEvent, useEffect, useRef, useState } from 'react';
import {
  fetchOfferComparison,
  fetchOfferExplanation,
  selectOffer,
  type OfferComparisonView,
  type OfferExplanationView,
  type OfferSummary,
} from './lib/api/offers';
import { launchQuoteRun, type BorrowerIntake, type IntakeValidation, type QuoteRunLaunch } from './lib/api/quoteRuns';
import { confirmLock, fetchLockWorkflow, type LockConfirmationResult, type LockWorkflowView } from './lib/api/locks';
import {
  fetchPartnerQuoteDetail,
  fetchPartnerQuotes,
  requestPartnerReprice,
  type PartnerQuoteDetail,
  type PartnerQuoteListView,
  type PartnerQuoteSummary,
  type PartnerRepriceResult,
} from './lib/api/partnerQuotes';
import {
  fetchPartnerWebhookHealth,
  requestPartnerWebhookEndpointTest,
  requestPartnerWebhookReplay,
  requestPartnerWebhookSafetyToggle,
  type PartnerSafetyToggleResult,
  type PartnerWebhookActionResult,
  type PartnerWebhookDeliveryAttempt,
  type PartnerWebhookHealthView,
} from './lib/api/partnerTransport';
import {
  addOpsCaseNote,
  assignOpsCase,
  fetchOpsCaseDetail,
  fetchOpsCases,
  updateOpsCaseStatus,
  type OpsCaseActionResult,
  type OpsCaseDetail,
  type OpsCaseListView,
  type OpsCaseSummary,
} from './lib/api/opsCases';
import {
  fetchComplianceEvidenceRegistry,
  type ComplianceEvidenceRegistryView,
} from './lib/api/complianceEvidence';
import {
  fetchQualityDashboard,
  requestQualityEvidenceExport,
  type QualityDashboardView,
  type QualityEvidenceExport,
} from './lib/api/quality';
import {
  fetchAdminGovernance,
  type AdminGovernanceView,
  type IncidentReviewSummary,
} from './lib/api/adminGovernance';
import { fetchUiHealth, type UiHealth } from './lib/api/uiHealth';

type HealthState =
  | { kind: 'loading' }
  | { kind: 'reachable'; health: UiHealth }
  | { kind: 'unreachable'; message: string };

const fallbackNotice = 'Navigation shell is available. Backend workflow data is not loaded in this foundation slice.';
const uiTraceId = 'brw-s01-local-trace';
const tenantBoundaryPlaceholder = 'ui-preview-tenant';
const partnerBoundaryPlaceholder = 'partner-preview';
const selectedOfferStoragePrefix = 'wcpe:selectedOfferId:';

type LaunchState =
  | { kind: 'idle' }
  | { kind: 'submitting' }
  | { kind: 'blocked'; validation: IntakeValidation }
  | { kind: 'created'; launch: QuoteRunLaunch }
  | { kind: 'outage'; message: string };

const initialIntake: BorrowerIntake = {
  borrowerName: '',
  contactEmail: '',
  quoteGoal: '',
};

export function App() {
  const [healthState, setHealthState] = useState<HealthState>({ kind: 'loading' });
  const [intake, setIntake] = useState<BorrowerIntake>(initialIntake);
  const [errors, setErrors] = useState<Partial<Record<keyof BorrowerIntake, string>>>({});
  const [launchState, setLaunchState] = useState<LaunchState>({ kind: 'idle' });
  const [activeRunId, setActiveRunId] = useState<string | null>(() => runIdFromPath(window.location.pathname));
  const [activeScreen, setActiveScreen] = useState<'offers' | 'lock' | 'status' | null>(() => screenFromPath(window.location.pathname));
  const [partnerTransportActive] = useState(() => partnerTransportPathActive(window.location.pathname));
  const [partnerQuotesActive] = useState(() => window.location.pathname.startsWith('/partners/quotes'));
  const [opsCasesActive] = useState(() => opsPathActive(window.location.pathname));
  const [complianceActive] = useState(() => compliancePathActive(window.location.pathname));
  const [qualityActive] = useState(() => qualityPathActive(window.location.pathname));
  const [adminActive] = useState(() => adminPathActive(window.location.pathname));
  const borrowerNameRef = useRef<HTMLInputElement>(null);
  const contactEmailRef = useRef<HTMLInputElement>(null);
  const quoteGoalRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    let active = true;

    fetchUiHealth()
      .then((health) => {
        if (active) setHealthState({ kind: 'reachable', health });
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'BFF status request failed';
        if (active) setHealthState({ kind: 'unreachable', message });
      });

    return () => {
      active = false;
    };
  }, []);

  function updateField(field: keyof BorrowerIntake, value: string) {
    setIntake((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
  }

  function focusFirstInvalid(nextErrors: Partial<Record<keyof BorrowerIntake, string>>) {
    if (nextErrors.borrowerName) borrowerNameRef.current?.focus();
    else if (nextErrors.contactEmail) contactEmailRef.current?.focus();
    else if (nextErrors.quoteGoal) quoteGoalRef.current?.focus();
  }

  function validateRequiredFields(values: BorrowerIntake): Partial<Record<keyof BorrowerIntake, string>> {
    const nextErrors: Partial<Record<keyof BorrowerIntake, string>> = {};
    if (!values.borrowerName.trim()) nextErrors.borrowerName = 'Borrower name is required.';
    if (!values.contactEmail.trim()) nextErrors.contactEmail = 'Contact email is required.';
    if (!values.quoteGoal.trim()) nextErrors.quoteGoal = 'Quote goal is required.';
    return nextErrors;
  }

  async function submitIntake(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextErrors = validateRequiredFields(intake);
    if (Object.keys(nextErrors).length > 0) {
      setErrors(nextErrors);
      setLaunchState({
        kind: 'blocked',
        validation: {
          passed: false,
          status: 'BLOCKED',
          message: 'Complete the highlighted required fields.',
          blockers: nextErrors,
        },
      });
      focusFirstInvalid(nextErrors);
      return;
    }

    setLaunchState({ kind: 'submitting' });
    try {
      const launch = await launchQuoteRun(tenantBoundaryPlaceholder, intake);
      if (launch.status === 'BLOCKED') {
        setErrors(launch.validationSummary.blockers);
        setLaunchState({ kind: 'blocked', validation: launch.validationSummary });
        focusFirstInvalid(launch.validationSummary.blockers);
        return;
      }

      if (launch.nextRoute) {
        window.history.pushState({ runId: launch.runId, screenId: 'BRW-S02' }, '', launch.nextRoute);
      }
      setActiveRunId(launch.runId);
      setActiveScreen('offers');
      setLaunchState({ kind: 'created', launch });
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : 'BFF borrower intake boundary is unavailable.';
      setLaunchState({ kind: 'outage', message });
    }
  }

  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">
        Skip to main content
      </a>
      <header className="shell-header" role="banner">
        <div>
          <p className="eyebrow">World Class Pricing Engine</p>
          <h1>Pricing Workbench</h1>
        </div>
        <form className="shell-search" role="search" aria-label="Workbench search">
          <label htmlFor="global-search">Search</label>
          <input id="global-search" name="search" type="search" placeholder="Search workbench" />
        </form>
      </header>

      <div className="shell-grid">
        <nav className="side-nav" aria-label="Main navigation">
          <h2>Workspace</h2>
          <a href="#borrower-intake">Start quote</a>
          <a href="/ops/dashboard">Operations cases</a>
          <a href="/compliance/evidence">Compliance evidence</a>
          <a href="/quality/validation">Quality guardrails</a>
          <a href="/admin/release-readiness">Admin release gates</a>
          <a href="/partners/quotes">Partner quotes</a>
          <a href="/partners/webhooks">Partner webhooks</a>
          <a href="#status-panel">BFF status</a>
          <a href="#notice-panel">Notices</a>
          <a href="#help-panel">Help</a>
        </nav>

        <main id="main-content" tabIndex={-1}>
          <nav aria-label="Breadcrumb" className="breadcrumbs">
            <ol>
              <li>Home</li>
              <li aria-current="page">{adminActive ? 'Admin Release Gates' : qualityActive ? 'Quality Guardrails' : complianceActive ? 'Compliance Evidence' : opsCasesActive ? 'Operations Cases' : partnerTransportActive ? 'Partner Transport' : partnerQuotesActive ? 'Partner Quotes' : activeRunId ? screenLabel(activeScreen) : 'Start Quote'}</li>
            </ol>
          </nav>

          {adminActive ? (
            <AdminGovernanceSection />
          ) : qualityActive ? (
            <QualityGuardrailsSection />
          ) : complianceActive ? (
            <ComplianceEvidenceRegistrySection />
          ) : opsCasesActive ? (
            <OperationsCaseTriageSection />
          ) : partnerTransportActive ? (
            <PartnerTransportReliabilitySection partnerId={partnerBoundaryPlaceholder} />
          ) : partnerQuotesActive ? (
            <PartnerQuoteLifecycleSection partnerId={partnerBoundaryPlaceholder} />
          ) : activeRunId && activeScreen === 'lock' ? (
            <LockLifecycleSection runId={activeRunId} />
          ) : activeRunId ? (
            <OfferComparisonSection runId={activeRunId} />
          ) : (
            <>
              <section className="hero" aria-labelledby="borrower-title">
                <p className="eyebrow">Borrower · BRW-S01</p>
                <h2 id="borrower-title">Start a quote run</h2>
                <p>
                  Enter the required intake details to launch a deterministic quote run through the pricing-bff boundary.
                  Pricing, credit, and approval decisions are not made on this screen.
                </p>
              </section>

              <section id="borrower-intake" className="panel" aria-labelledby="intake-heading">
                <div className="panel-heading-row">
                  <div>
                    <p className="eyebrow">Route /quote/start</p>
                    <h2 id="intake-heading">Borrower intake</h2>
                  </div>
                  <span className="trace-badge">ui_trace_id: {uiTraceId}</span>
                </div>

                <LaunchBanner state={launchState} onRetry={() => setLaunchState({ kind: 'idle' })} />

                <form className="intake-form" onSubmit={submitIntake} noValidate>
                  <div className="field-group">
                    <label htmlFor="borrowerName">Borrower name <span aria-hidden="true">*</span></label>
                    <input
                      ref={borrowerNameRef}
                      id="borrowerName"
                      name="borrowerName"
                      value={intake.borrowerName}
                      aria-invalid={Boolean(errors.borrowerName)}
                      aria-describedby={errors.borrowerName ? 'borrowerName-error' : undefined}
                      onChange={(event) => updateField('borrowerName', event.target.value)}
                    />
                    {errors.borrowerName ? <p id="borrowerName-error" role="alert">{errors.borrowerName}</p> : null}
                  </div>

                  <div className="field-group">
                    <label htmlFor="contactEmail">Contact email <span aria-hidden="true">*</span></label>
                    <input
                      ref={contactEmailRef}
                      id="contactEmail"
                      name="contactEmail"
                      type="email"
                      value={intake.contactEmail}
                      aria-invalid={Boolean(errors.contactEmail)}
                      aria-describedby={errors.contactEmail ? 'contactEmail-error' : undefined}
                      onChange={(event) => updateField('contactEmail', event.target.value)}
                    />
                    {errors.contactEmail ? <p id="contactEmail-error" role="alert">{errors.contactEmail}</p> : null}
                  </div>

                  <div className="field-group field-group--full">
                    <label htmlFor="quoteGoal">Quote goal <span aria-hidden="true">*</span></label>
                    <textarea
                      ref={quoteGoalRef}
                      id="quoteGoal"
                      name="quoteGoal"
                      value={intake.quoteGoal}
                      aria-invalid={Boolean(errors.quoteGoal)}
                      aria-describedby={errors.quoteGoal ? 'quoteGoal-error' : 'quoteGoal-help'}
                      onChange={(event) => updateField('quoteGoal', event.target.value)}
                    />
                    <p id="quoteGoal-help" className="field-help">Describe what the borrower wants to compare without entering pricing assumptions.</p>
                    {errors.quoteGoal ? <p id="quoteGoal-error" role="alert">{errors.quoteGoal}</p> : null}
                  </div>

                  <button type="submit" disabled={launchState.kind === 'submitting'}>
                    {launchState.kind === 'submitting' ? 'Starting run...' : 'Start quote run'}
                  </button>
                </form>
              </section>
            </>
          )}

          <section id="status-panel" className="panel" aria-labelledby="status-heading" tabIndex={-1}>
            <h2 id="status-heading">BFF boundary status</h2>
            <HealthPanel state={healthState} />
          </section>

          <section id="notice-panel" className="panel" aria-labelledby="notice-heading">
            <h2 id="notice-heading">Notices</h2>
            <p role="status">{fallbackNotice}</p>
          </section>

          <section id="help-panel" className="panel" aria-labelledby="help-heading">
            <h2 id="help-heading">Help</h2>
            <button type="button" onClick={() => document.getElementById('status-panel')?.focus()}>
              Return focus to status
            </button>
          </section>
        </main>
      </div>
    </div>
  );
}

type LockState =
  | { kind: 'loading' }
  | { kind: 'loaded'; workflow: LockWorkflowView }
  | { kind: 'unreachable'; message: string };

type PartnerQuoteState =
  | { kind: 'loading' }
  | { kind: 'loaded'; view: PartnerQuoteListView }
  | { kind: 'unreachable'; message: string };

type PartnerQuoteDetailState =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'loaded'; detail: PartnerQuoteDetail }
  | { kind: 'unreachable'; message: string };

type PartnerTransportState =
  | { kind: 'loading' }
  | { kind: 'loaded'; view: PartnerWebhookHealthView }
  | { kind: 'unreachable'; message: string };

type OpsCaseState =
  | { kind: 'loading' }
  | { kind: 'loaded'; view: OpsCaseListView }
  | { kind: 'unreachable'; message: string };

type OpsCaseDetailState =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'loaded'; detail: OpsCaseDetail }
  | { kind: 'unreachable'; message: string };

type ComplianceEvidenceState =
  | { kind: 'loading' }
  | { kind: 'loaded'; view: ComplianceEvidenceRegistryView }
  | { kind: 'unreachable'; message: string };

type QualityDashboardState =
  | { kind: 'loading' }
  | { kind: 'loaded'; view: QualityDashboardView }
  | { kind: 'unreachable'; message: string };

type AdminGovernanceState =
  | { kind: 'loading' }
  | { kind: 'loaded'; view: AdminGovernanceView }
  | { kind: 'unreachable'; message: string };

function opsPathActive(pathname: string) {
  return pathname.startsWith('/ops/dashboard') || pathname.startsWith('/ops/queues') || pathname.startsWith('/ops/cases') || pathname.startsWith('/ops/escalations');
}

function partnerTransportPathActive(pathname: string) {
  return pathname.startsWith('/partners/webhooks') || pathname.startsWith('/partners/admin/safety') || pathname.startsWith('/partners/alerts');
}

function compliancePathActive(pathname: string) {
  return pathname.startsWith('/compliance') || pathname.startsWith('/privacy/requests') || pathname.startsWith('/security/events');
}

function qualityPathActive(pathname: string) {
  return pathname.startsWith('/quality/');
}

function adminPathActive(pathname: string) {
  return pathname.startsWith('/admin/');
}

function AdminGovernanceSection() {
  const [adminState, setAdminState] = useState<AdminGovernanceState>({ kind: 'loading' });

  useEffect(() => {
    let active = true;
    fetchAdminGovernance()
      .then((view) => {
        if (active) setAdminState({ kind: 'loaded', view });
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'BFF admin governance boundary is unavailable.';
        if (active) setAdminState({ kind: 'unreachable', message });
      });
    return () => {
      active = false;
    };
  }, []);

  if (adminState.kind === 'loading') {
    return <section className="panel" aria-labelledby="admin-heading"><h2 id="admin-heading">Admin governance and release gates</h2><p role="status">Loading admin governance...</p></section>;
  }

  if (adminState.kind === 'unreachable') {
    return (
      <section className="panel" aria-labelledby="admin-heading">
        <h2 id="admin-heading">Admin governance and release gates</h2>
        <div className="banner banner--blocked" role="alert">{adminState.message}</div>
      </section>
    );
  }

  const view = adminState.view;
  return (
    <>
      <section className="hero hero--admin" aria-labelledby="admin-title">
        <p className="eyebrow">Admin · AG-S01 / AG-S07</p>
        <h2 id="admin-title">Admin governance and release gate controls</h2>
        <p>
          Review policy versions, flag conflicts, market-rule completeness, change approvals, release readiness,
          drift, incidents, and override audit evidence through the pricing-bff boundary. Release actions stay disabled
          while configured governance contracts and open decisions are unresolved.
        </p>
      </section>

      <section className="panel" aria-labelledby="admin-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Route /admin/release-readiness</p>
            <h2 id="admin-heading">Release candidate readiness</h2>
          </div>
          <span className="trace-badge">tenant: {view.tenantContext} · {view.uiTraceId}</span>
        </div>
        <div className="banner banner--blocked" role="alert">
          <strong>Release status {view.releaseCandidate.readinessStatus}</strong>
          <span>{view.fallbackReason}</span>
          <span>Dependency status: {view.dependencyStatus}</span>
        </div>
        <dl className="status-grid">
          <dt>Candidate</dt><dd>{view.releaseCandidate.candidateId}</dd>
          <dt>Environment</dt><dd>{view.releaseCandidate.environmentTarget}</dd>
          <dt>Fingerprint</dt><dd>{view.releaseCandidate.releaseFingerprint}</dd>
          <dt>Manifest</dt><dd>{view.releaseCandidate.manifestRef}</dd>
          <dt>Signature</dt><dd>{view.releaseCandidate.signature}</dd>
        </dl>
        <button type="button" disabled={view.releaseCandidate.deployDisabled} aria-describedby="release-blockers">
          Deploy release candidate
        </button>
        <button type="button" className="button-secondary" disabled={view.releaseCandidate.rollbackDisabled}>
          Execute rollback
        </button>
        <div id="release-blockers">
          <ChipList label="Release blockers" values={view.releaseCandidate.blockers} />
        </div>
        <ChipList label="Affected subsystems" values={view.releaseCandidate.affectedSubsystems} />
        <div className="quote-table" role="table" aria-label="Release gates">
          <div role="row" className="quote-table__row quote-table__row--head">
            <span role="columnheader">Gate</span>
            <span role="columnheader">Status</span>
            <span role="columnheader">Mandatory</span>
            <span role="columnheader">Artifact</span>
            <span role="columnheader">Action</span>
          </div>
          {view.releaseCandidate.gates.map((gate) => (
            <div key={gate.gateName} role="row" className="quote-table__row">
              <span role="cell">{gate.gateName}</span>
              <span role="cell">{gate.status}</span>
              <span role="cell">{gate.mandatory ? 'yes' : 'no'}</span>
              <span role="cell">{gate.artifactRef}</span>
              <span role="cell">Blocked until evidence links exist</span>
            </div>
          ))}
        </div>
      </section>

      <section className="panel" aria-labelledby="open-decisions-heading">
        <h2 id="open-decisions-heading">Open decision blockers</h2>
        <ul className="offer-list" aria-label="Open decisions blocking release">
          {view.openDecisions.map((decision) => (
            <li key={decision.decisionId} role="alert">
              <h3>{decision.decisionId} · {decision.status}</h3>
              <p>{decision.title}</p>
              <p>Resolution record: {decision.resolutionRef}</p>
            </li>
          ))}
        </ul>
      </section>

      <section className="panel" aria-labelledby="policy-heading">
        <h2 id="policy-heading">Policy registry, flag conflicts, and market-rule staging</h2>
        <div className="quote-table" role="table" aria-label="Policy registry">
          <div role="row" className="quote-table__row quote-table__row--head">
            <span role="columnheader">Version</span>
            <span role="columnheader">Status</span>
            <span role="columnheader">Owner</span>
            <span role="columnheader">Hash</span>
            <span role="columnheader">Lineage</span>
          </div>
          {view.policies.map((policy) => (
            <div key={policy.versionId} role="row" className="quote-table__row">
              <span role="cell">{policy.versionId}</span>
              <span role="cell">{policy.status}</span>
              <span role="cell">{policy.owner}</span>
              <span role="cell">{policy.hashSignature}</span>
              <span role="cell">parent {policy.parentVersionId}</span>
            </div>
          ))}
        </div>
        {view.policies.map((policy) => <ChipList key={`${policy.versionId}-impacts`} label={`${policy.versionId} diff impacts`} values={policy.diffImpacts} />)}
        {view.featureFlags.map((flag) => (
          <div key={flag.flagId} className="banner banner--blocked" role="alert">
            <strong>Feature flag activation blocked</strong>
            <span>{flag.flagId} · {flag.environmentTarget}</span>
            <span>{flag.emergencyToggleGate}</span>
            <ChipList label="Unresolved flags" values={flag.unresolvedFlags} />
          </div>
        ))}
        {view.marketRules.map((rule) => (
          <div key={rule.ruleId} className="banner banner--blocked" role="alert">
            <strong>Market rule promotion blocked</strong>
            <span>{rule.ruleId} · {rule.stagingStatus}</span>
            <span>{rule.completenessGate}</span>
            <ChipList label="Missing market-rule fields" values={rule.missingRequiredFields} />
          </div>
        ))}
      </section>

      <section className="panel" aria-labelledby="change-requests-heading">
        <h2 id="change-requests-heading">Change requests and approval state machine</h2>
        {view.changeRequests.map((request) => (
          <article key={request.requestId} className="status-card status-card--offline" role="alert">
            <h3>{request.requestId} · {request.state}</h3>
            <p>{request.requestType} · {request.riskLevel} · owner {request.owner}</p>
            <button type="button" disabled={request.promotionDisabled}>Approve change request</button>
            <ChipList label="Required state sequence" values={request.requiredStateSequence} />
            <ChipList label="Change request blockers" values={request.blockers} />
          </article>
        ))}
      </section>

      <section className="panel" aria-labelledby="drift-incident-heading">
        <h2 id="drift-incident-heading">Drift alerts, incidents, rollback, and override ledger</h2>
        {view.driftAlerts.map((alert) => (
          <div key={alert.alertId} className="banner banner--blocked" role="alert">
            <strong>{alert.alertId} · {alert.severity}</strong>
            <span>{alert.summary}</span>
            <span>Owner: {alert.owner} · Environment: {alert.environment}</span>
          </div>
        ))}
        <ul className="offer-list" aria-label="Admin incidents">
          {view.incidents.map((incident) => <AdminIncidentCard key={incident.incidentId} incident={incident} />)}
        </ul>
        <div className="quote-table" role="table" aria-label="Override audit ledger">
          <div role="row" className="quote-table__row quote-table__row--head">
            <span role="columnheader">Ledger</span>
            <span role="columnheader">Actor</span>
            <span role="columnheader">Field</span>
            <span role="columnheader">Policy</span>
            <span role="columnheader">Audit ref</span>
          </div>
          {view.overrideLedger.map((entry) => (
            <div key={entry.ledgerId} role="row" className="quote-table__row">
              <span role="cell">{entry.ledgerId}</span>
              <span role="cell">{entry.actor}</span>
              <span role="cell">{entry.fieldPath}</span>
              <span role="cell">{entry.policyRef}</span>
              <span role="cell">{entry.auditRef}</span>
            </div>
          ))}
        </div>
      </section>
    </>
  );
}

function AdminIncidentCard({ incident }: { incident: IncidentReviewSummary }) {
  return (
    <li role="alert">
      <h3>{incident.incidentId} · {incident.status}</h3>
      <p>{incident.closureGate}</p>
      <p>Rollback target: {incident.rollbackTarget}</p>
      <button type="button" disabled>Close incident</button>
      <button type="button" className="button-secondary" disabled>Start rollback wizard</button>
    </li>
  );
}

function QualityGuardrailsSection() {
  const [qualityState, setQualityState] = useState<QualityDashboardState>({ kind: 'loading' });
  const [exportResult, setExportResult] = useState<QualityEvidenceExport | null>(null);

  useEffect(() => {
    let active = true;
    fetchQualityDashboard()
      .then((view) => {
        if (active) setQualityState({ kind: 'loaded', view });
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'BFF quality analytics boundary is unavailable.';
        if (active) setQualityState({ kind: 'unreachable', message });
      });
    return () => {
      active = false;
    };
  }, []);

  async function exportEvidence() {
    setExportResult(await requestQualityEvidenceExport());
  }

  if (qualityState.kind === 'loading') {
    return <section className="panel" aria-labelledby="quality-heading"><h2 id="quality-heading">Quality guardrails dashboard</h2><p role="status">Loading quality guardrails...</p></section>;
  }

  if (qualityState.kind === 'unreachable') {
    return (
      <section className="panel" aria-labelledby="quality-heading">
        <h2 id="quality-heading">Quality guardrails dashboard</h2>
        <div className="banner banner--blocked" role="alert">{qualityState.message}</div>
      </section>
    );
  }

  const view = qualityState.view;
  return (
    <>
      <section className="hero hero--quality" aria-labelledby="quality-title">
        <p className="eyebrow">Quality · QL-S01 / QL-S07</p>
        <h2 id="quality-title">Quality guardrails dashboard</h2>
        <p>
          Review validation loop status, release readiness, drift, fairness, incidents, replay, and contract conformance
          through the pricing-bff boundary. The UI reports configured evidence states and does not invent thresholds.
        </p>
      </section>

      <section className="panel" aria-labelledby="quality-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Route /quality/validation</p>
            <h2 id="quality-heading">Validation results console</h2>
          </div>
          <span className="trace-badge">tenant: {view.tenantContext} · {view.uiTraceId}</span>
        </div>
        <div className="banner banner--blocked" role="alert">
          <strong>Loop status {view.validationRun.loopStatus}</strong>
          <span>{view.validationRun.nextAction}</span>
          <span>Dependency status: {view.dependencyStatus}</span>
        </div>
        <div className="quote-table quality-stage-table" role="table" aria-label="Validation stages">
          <div role="row" className="quote-table__row quote-table__row--head">
            <span role="columnheader">Stage</span>
            <span role="columnheader">Status</span>
            <span role="columnheader">Timestamp</span>
            <span role="columnheader">Trace</span>
            <span role="columnheader">Action</span>
          </div>
          {view.validationRun.stages.map((stage) => (
            <div key={stage.stageId} role="row" className="quote-table__row">
              <span role="cell">{stage.stageId} · {stage.label}</span>
              <span role="cell">{stage.status}</span>
              <span role="cell">{stage.timestampLabel}</span>
              <span role="cell">{view.validationRun.runId}</span>
              <span role="cell">Preserve evidence</span>
            </div>
          ))}
        </div>
        {view.validationRun.openBlockers.map((blocker) => (
          <article key={blocker.blockerId} className="status-card status-card--offline" role="alert">
            <h3>{blocker.severity} · {blocker.reasonClass}</h3>
            <p>{blocker.summary}</p>
            <p>Owner: {blocker.owner} · Status: {blocker.status}</p>
          </article>
        ))}
        <ChipList label="Evidence paths" values={view.validationRun.evidencePaths} />
      </section>

      <section className="panel" aria-labelledby="readiness-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Route /quality/readiness</p>
            <h2 id="readiness-heading">Release gate health board</h2>
          </div>
          <span className="trace-badge">readiness: {view.readiness.readinessStatus}</span>
        </div>
        <button type="button" disabled={view.readiness.deploymentDisabled} aria-disabled={view.readiness.deploymentDisabled}>
          Approve for rollout
        </button>
        {view.readiness.deploymentDisabled ? <p role="status">Deployment action disabled until readiness passes and blockers clear.</p> : null}
        <ChipList label="Readiness blockers" values={view.readiness.blockList} />
        <ChipList label="Signoff refs" values={view.readiness.signoffRefs} />
        <ChipList label="Dependency checks" values={view.readiness.dependencyChecks} />
        <p>Evidence set completeness: {view.readiness.evidenceSetCompleteness}</p>
      </section>

      <section className="panel" aria-labelledby="drift-fairness-heading">
        <h2 id="drift-fairness-heading">Drift and fairness monitoring</h2>
        <div className="banner banner--blocked" role="alert">
          <strong>Drift lockout</strong>
          <span>{view.drift.lockoutReason}</span>
          <span>Cache staleness: {view.drift.cacheStaleness}</span>
        </div>
        <dl className="status-grid">
          <dt>Metric family</dt><dd>{view.drift.metricFamily}</dd>
          <dt>Window</dt><dd>{view.drift.window}</dd>
          <dt>Baseline</dt><dd>{view.drift.windowBaseline}</dd>
          <dt>Sample counts</dt><dd>{view.fairness.sampleCountsLabel}</dd>
          <dt>Fairness redaction</dt><dd>{view.fairness.redacted ? 'Protected class labels masked' : 'Role-authorized dimensions visible'}</dd>
        </dl>
        <ChipList label="Affected products" values={view.drift.affectedProducts} />
        <ChipList label="Protected class dimensions" values={view.fairness.protectedClassDimensions} />
        <ChipList label="Fairness evidence refs" values={view.fairness.evidenceRefs} />
        <ul className="offer-list" aria-label="Drift metrics">
          {view.drift.metrics.map((metric) => (
            <li key={metric.metricName}>
              <h3>{metric.metricName} · {metric.severity}</h3>
              <p>{metric.deviationLabel}</p>
            </li>
          ))}
        </ul>
      </section>

      <section className="panel" aria-labelledby="incident-replay-heading">
        <h2 id="incident-replay-heading">Incidents and deterministic replay</h2>
        <div className="quote-table" role="table" aria-label="Quality incidents">
          <div role="row" className="quote-table__row quote-table__row--head">
            <span role="columnheader">Incident</span>
            <span role="columnheader">Severity</span>
            <span role="columnheader">Owner</span>
            <span role="columnheader">Stage</span>
            <span role="columnheader">Evidence</span>
          </div>
          {view.incidents.map((incident) => (
            <div key={incident.incidentId} role="row" className="quote-table__row">
              <span role="cell">{incident.incidentId} ({incident.incidentClass})</span>
              <span role="cell">{incident.severity}</span>
              <span role="cell">{incident.escalationTarget}</span>
              <span role="cell">{incident.lifecycleStage}</span>
              <span role="cell">{incident.evidencePackageId}</span>
            </div>
          ))}
        </div>
        <div className="banner banner--blocked" role="alert">
          <strong>Replay blocked</strong>
          <span>{view.replay.blockedReason}</span>
        </div>
        <ChipList label="Replay modes" values={view.replay.replayModes} />
      </section>

      <section className="panel" aria-labelledby="contracts-heading">
        <h2 id="contracts-heading">Contract conformance and evidence export</h2>
        <ul className="offer-list" aria-label="Contract conformance checks">
          {view.contracts.map((contract) => (
            <li key={contract.contractId}>
              <h3>{contract.contractId} · {contract.status}</h3>
              <p>{contract.summary}</p>
              <ChipList label={`${contract.contractId} failures`} values={contract.failures} />
            </li>
          ))}
        </ul>
        <button type="button" onClick={() => void exportEvidence()}>Prepare redacted evidence export</button>
        <QualityEvidenceExportPanel exportResult={exportResult ?? view.evidenceExport} />
      </section>
    </>
  );
}

function QualityEvidenceExportPanel({ exportResult }: { exportResult: QualityEvidenceExport }) {
  return (
    <div className={exportResult.completenessStatus === 'COMPLETE' ? 'banner banner--success' : 'banner banner--blocked'} role={exportResult.completenessStatus === 'COMPLETE' ? 'status' : 'alert'}>
      <strong>Evidence export {exportResult.completenessStatus.toLowerCase()}</strong>
      <span>Package: {exportResult.packageId}</span>
      <span>Redacted: {exportResult.redacted ? 'yes' : 'no'}</span>
      <ChipList label="Export evidence refs" values={exportResult.evidenceRefs} />
      <ChipList label="Export blockers" values={exportResult.blockers} />
    </div>
  );
}

function ComplianceEvidenceRegistrySection() {
  const [registryState, setRegistryState] = useState<ComplianceEvidenceState>({ kind: 'loading' });

  useEffect(() => {
    let active = true;
    fetchComplianceEvidenceRegistry()
      .then((view) => {
        if (active) setRegistryState({ kind: 'loaded', view });
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'BFF compliance evidence boundary is unavailable.';
        if (active) setRegistryState({ kind: 'unreachable', message });
      });
    return () => {
      active = false;
    };
  }, []);

  if (registryState.kind === 'loading') {
    return <section className="panel" aria-labelledby="compliance-heading"><h2 id="compliance-heading">Compliance evidence registry</h2><p role="status">Loading compliance evidence...</p></section>;
  }

  if (registryState.kind === 'unreachable') {
    return (
      <section className="panel" aria-labelledby="compliance-heading">
        <h2 id="compliance-heading">Compliance evidence registry</h2>
        <div className="banner banner--blocked" role="alert">{registryState.message}</div>
      </section>
    );
  }

  const view = registryState.view;
  return (
    <>
      <section className="hero hero--compliance" aria-labelledby="compliance-title">
        <p className="eyebrow">Compliance · SEC-S01 / SEC-S07</p>
        <h2 id="compliance-title">Compliance evidence registry</h2>
        <p>
          Inspect immutable evidence references, decision rationale, DSAR gates, security events, alert routing, and
          retention controls through the pricing-bff boundary. Legal and retention values remain configuration-owned.
        </p>
      </section>

      <section className="panel" aria-labelledby="compliance-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Route /compliance/evidence</p>
            <h2 id="compliance-heading">Evidence catalog</h2>
          </div>
          <span className="trace-badge">tenant: {view.tenantContext}</span>
        </div>
        <div className="banner banner--blocked" role="alert">
          <strong>Dependency unavailable fallback</strong>
          <span>{view.fallbackReason}</span>
          <span>Dependency status: {view.dependencyStatus}</span>
        </div>
        <div className="quote-table" role="table" aria-label="Compliance evidence artifacts">
          <div role="row" className="quote-table__row quote-table__row--head">
            <span role="columnheader">Artifact</span>
            <span role="columnheader">Policy</span>
            <span role="columnheader">Continuity</span>
            <span role="columnheader">Retention</span>
            <span role="columnheader">Trace</span>
          </div>
          {view.artifacts.map((artifact) => (
            <div key={artifact.artifactId} role="row" className="quote-table__row">
              <span role="cell">{artifact.artifactId} ({artifact.artifactType})</span>
              <span role="cell">{artifact.policyVersion} / {artifact.policyDigest} / {artifact.jurisdictionCode}</span>
              <span role="cell">{artifact.continuityStatus}</span>
              <span role="cell">{artifact.retentionClass}</span>
              <span role="cell">{artifact.traceId}</span>
            </div>
          ))}
        </div>
        {view.artifacts.map((artifact) => (
          <div key={`${artifact.artifactId}-blockers`} className="banner banner--blocked" role="alert">
            <strong>{artifact.artifactId} progression blocked</strong>
            <ChipList label="Module links" values={artifact.moduleLinks} />
            <ChipList label="Evidence blockers" values={artifact.blockers} />
          </div>
        ))}
      </section>

      <section className="panel" aria-labelledby="decision-heading">
        <h2 id="decision-heading">Decision rationale and privacy gates</h2>
        <div className="partner-detail-grid">
          {view.decisions.map((decision) => (
            <article key={decision.decisionId} className="status-card status-card--offline">
              <h3>{decision.reasonCode}</h3>
              <p>{decision.humanText}</p>
              <p>Jurisdiction: {decision.jurisdictionCode}</p>
              <p>Disclosure artifact: {decision.disclosureArtifactRef}</p>
              <ChipList label="Reason tiers" values={decision.reasonTiers} />
            </article>
          ))}
          {view.privacyRequests.map((request) => (
            <article key={request.requestId} className="status-card status-card--offline">
              <h3>DSAR {request.requestId}</h3>
              <p>Borrower: {request.borrowerRef}</p>
              <p>Identity: {request.identityStatus}</p>
              <p>SLA: {request.slaState}</p>
              <p>Consent audit: {request.consentAuditRef}</p>
              <ChipList label="Privacy blockers" values={request.blockers} />
            </article>
          ))}
        </div>
      </section>

      <section className="panel" aria-labelledby="security-alerts-heading">
        <h2 id="security-alerts-heading">Security events, alerts, and retention</h2>
        <div className="partner-detail-grid">
          {view.securityEvents.map((event) => (
            <article key={event.eventId} className="status-card status-card--offline">
              <h3>{event.category} · {event.severity}</h3>
              <p>Log record: {event.logRecordId}</p>
              <p>Owner: {event.owner}</p>
              <p>Acknowledged: {event.acknowledged ? 'yes' : 'no'}</p>
              <ChipList label="Security blockers" values={event.blockers} />
            </article>
          ))}
          {view.alerts.map((alert) => (
            <article key={alert.alertId} className="status-card status-card--offline">
              <h3>{alert.alertId}</h3>
              <p>{alert.severity} · {alert.alertClass} · {alert.triggerType}</p>
              <p>Route target: {alert.routeTarget}</p>
              <ChipList label="Alert blockers" values={alert.blockers} />
            </article>
          ))}
          {view.retentionControls.map((control) => (
            <article key={control.ruleId} className="status-card status-card--offline" role="alert">
              <h3>{control.ruleId}</h3>
              <p>{control.retentionClass}</p>
              <p>{control.retentionWindow}</p>
              <p>{control.deletionGateReason}</p>
              <p>{control.backupEvidence}</p>
            </article>
          ))}
        </div>
      </section>
    </>
  );
}

function OperationsCaseTriageSection() {
  const [caseState, setCaseState] = useState<OpsCaseState>({ kind: 'loading' });
  const [selectedCaseId, setSelectedCaseId] = useState<string | null>(caseIdFromOpsPath(window.location.pathname));
  const [detailState, setDetailState] = useState<OpsCaseDetailState>({ kind: 'idle' });
  const [owner, setOwner] = useState('');
  const [note, setNote] = useState('');
  const [escalationReason, setEscalationReason] = useState('');
  const [resolutionCode, setResolutionCode] = useState('');
  const [actionResult, setActionResult] = useState<OpsCaseActionResult | null>(null);

  useEffect(() => {
    let active = true;
    fetchOpsCases()
      .then((view) => {
        if (!active) return;
        setCaseState({ kind: 'loaded', view });
        setSelectedCaseId((current) => current ?? view.cases[0]?.caseId ?? null);
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'BFF operations case boundary is unavailable.';
        if (active) setCaseState({ kind: 'unreachable', message });
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!selectedCaseId) {
      setDetailState({ kind: 'idle' });
      return;
    }
    let active = true;
    setDetailState({ kind: 'loading' });
    setActionResult(null);
    fetchOpsCaseDetail(selectedCaseId)
      .then((detail) => {
        if (active) setDetailState({ kind: 'loaded', detail });
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'BFF operations case detail boundary is unavailable.';
        if (active) setDetailState({ kind: 'unreachable', message });
      });
    return () => {
      active = false;
    };
  }, [selectedCaseId]);

  function selectCase(caseId: string) {
    window.history.pushState({ caseId, screenId: 'OPS-S02' }, '', `/ops/cases/${encodeURIComponent(caseId)}`);
    setSelectedCaseId(caseId);
  }

  async function assignSelectedCase() {
    if (!selectedCaseId || !owner.trim()) return;
    setActionResult(await assignOpsCase(selectedCaseId, owner.trim()));
  }

  async function addNoteToSelectedCase() {
    if (!selectedCaseId || !note.trim()) return;
    setActionResult(await addOpsCaseNote(selectedCaseId, note.trim()));
  }

  async function escalateSelectedCase() {
    if (!selectedCaseId || !escalationReason.trim()) return;
    setActionResult(await updateOpsCaseStatus(selectedCaseId, 'ESCALATED', escalationReason.trim(), ''));
  }

  async function resolveSelectedCase() {
    if (!selectedCaseId || !resolutionCode.trim()) return;
    setActionResult(await updateOpsCaseStatus(selectedCaseId, 'RESOLVED', '', resolutionCode.trim()));
  }

  if (caseState.kind === 'loading') {
    return <section className="panel" aria-labelledby="ops-heading"><h2 id="ops-heading">Operations case triage</h2><p role="status">Loading operations cases...</p></section>;
  }

  if (caseState.kind === 'unreachable') {
    return (
      <section className="panel" aria-labelledby="ops-heading">
        <h2 id="ops-heading">Operations case triage</h2>
        <div className="banner banner--blocked" role="alert">{caseState.message}</div>
      </section>
    );
  }

  return (
    <>
      <section className="hero" aria-labelledby="ops-title">
        <p className="eyebrow">Operations · OPS-S01 / OPS-S02 / OPS-S03</p>
        <h2 id="ops-title">Operations case triage</h2>
        <p>Review BFF-supplied case context, preserve blocker detail, and gate intervention actions without changing pricing state.</p>
      </section>

      <section className="panel" aria-labelledby="ops-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Route /ops/dashboard</p>
            <h2 id="ops-heading">Case queue</h2>
          </div>
          <span className="trace-badge">tenant: {caseState.view.tenantContext}</span>
        </div>

        <OpsCaseQueue cases={caseState.view.cases} selectedCaseId={selectedCaseId} onSelect={selectCase} />
      </section>

      <section className="panel" aria-labelledby="ops-detail-heading">
        <h2 id="ops-detail-heading">Case timeline and controls</h2>
        {detailState.kind === 'idle' ? <p>Select a case to inspect timeline and intervention controls.</p> : null}
        {detailState.kind === 'loading' ? <p role="status">Loading operations case detail...</p> : null}
        {detailState.kind === 'unreachable' ? <div className="banner banner--blocked" role="alert">{detailState.message}</div> : null}
        {detailState.kind === 'loaded' ? (
          <div className="partner-detail-grid">
            <dl>
              <dt>Case</dt><dd>{detailState.detail.caseId}</dd>
              <dt>Status</dt><dd>{detailState.detail.status}</dd>
              <dt>Owner</dt><dd>{detailState.detail.owner}</dd>
              <dt>SLA</dt><dd>{detailState.detail.slaState}</dd>
              <dt>Age</dt><dd>{detailState.detail.ageLabel}</dd>
              <dt>Trace</dt><dd>{detailState.detail.uiTraceId}</dd>
            </dl>
            <ul className="offer-list" aria-label="Case timeline">
              {detailState.detail.timeline.map((event) => (
                <li key={event.eventId}>
                  <h3>{event.eventType}</h3>
                  <p>{event.summary}</p>
                </li>
              ))}
            </ul>
            <ChipList label="Evidence packets" values={detailState.detail.evidencePacketIds} />
            <div className="field-group">
              <label htmlFor="ops-owner">Assign owner</label>
              <input id="ops-owner" value={owner} onChange={(event) => setOwner(event.target.value)} />
              <button type="button" disabled={!owner.trim()} onClick={() => void assignSelectedCase()}>Assign case</button>
            </div>
            <div className="field-group">
              <label htmlFor="ops-note">Add case note</label>
              <textarea id="ops-note" value={note} onChange={(event) => setNote(event.target.value)} />
              <button type="button" disabled={!note.trim()} onClick={() => void addNoteToSelectedCase()}>Add note</button>
            </div>
            <div className="field-group">
              <label htmlFor="ops-escalation-reason">Escalation reason</label>
              <textarea id="ops-escalation-reason" value={escalationReason} onChange={(event) => setEscalationReason(event.target.value)} />
              <button type="button" disabled={!escalationReason.trim()} onClick={() => void escalateSelectedCase()}>Escalate with reason</button>
            </div>
            <div className="field-group">
              <label htmlFor="ops-resolution-code">Resolution code</label>
              <input id="ops-resolution-code" value={resolutionCode} onChange={(event) => setResolutionCode(event.target.value)} />
              <button type="button" disabled={!resolutionCode.trim()} onClick={() => void resolveSelectedCase()}>Resolve case</button>
            </div>
          </div>
        ) : null}
        {actionResult ? <OpsActionResultBanner result={actionResult} /> : null}
      </section>
    </>
  );
}

function OpsCaseQueue({ cases, selectedCaseId, onSelect }: { cases: OpsCaseSummary[]; selectedCaseId: string | null; onSelect: (caseId: string) => void }) {
  if (!cases.length) return <p role="status">No operations cases are loaded from pricing-bff.</p>;
  return (
    <div className="quote-table" role="table" aria-label="Operations cases">
      <div role="row" className="quote-table__row quote-table__row--head">
        <span role="columnheader">Case</span>
        <span role="columnheader">Priority</span>
        <span role="columnheader">SLA</span>
        <span role="columnheader">Owner</span>
        <span role="columnheader">Action</span>
      </div>
      {cases.map((opsCase) => (
        <div key={opsCase.caseId} role="row" className={selectedCaseId === opsCase.caseId ? 'quote-table__row quote-table__row--selected' : 'quote-table__row'}>
          <span role="cell">{opsCase.contextSummary} ({opsCase.caseId})</span>
          <span role="cell">{opsCase.priority}</span>
          <span role="cell">{opsCase.slaState}</span>
          <span role="cell">{opsCase.owner}</span>
          <span role="cell"><button type="button" onClick={() => onSelect(opsCase.caseId)}>Open case</button></span>
        </div>
      ))}
    </div>
  );
}

function OpsActionResultBanner({ result }: { result: OpsCaseActionResult }) {
  const accepted = result.status !== 'BLOCKED';
  return (
    <div className={accepted ? 'banner banner--success' : 'banner banner--blocked'} role={accepted ? 'status' : 'alert'}>
      <strong>{accepted ? 'Case action recorded' : 'Case action blocked'}</strong>
      <span>{result.message ?? result.immutableSummary}</span>
      {'downstreamExecuted' in result ? <span>Downstream executed: {result.downstreamExecuted ? 'yes' : 'no'}</span> : null}
    </div>
  );
}

function PartnerQuoteLifecycleSection({ partnerId }: { partnerId: string }) {
  const [statusFilter, setStatusFilter] = useState('');
  const [quoteState, setQuoteState] = useState<PartnerQuoteState>({ kind: 'loading' });
  const [selectedQuoteId, setSelectedQuoteId] = useState<string | null>(null);
  const [detailState, setDetailState] = useState<PartnerQuoteDetailState>({ kind: 'idle' });
  const [repriceResult, setRepriceResult] = useState<PartnerRepriceResult | null>(null);

  useEffect(() => {
    let active = true;
    setQuoteState({ kind: 'loading' });
    fetchPartnerQuotes(partnerId, statusFilter)
      .then((view) => {
        if (!active) return;
        setQuoteState({ kind: 'loaded', view });
        setSelectedQuoteId((current) => current && view.quotes.some((quote) => quote.quoteId === current) ? current : view.quotes[0]?.quoteId ?? null);
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'BFF partner quote list boundary is unavailable.';
        if (active) setQuoteState({ kind: 'unreachable', message });
      });
    return () => {
      active = false;
    };
  }, [partnerId, statusFilter]);

  useEffect(() => {
    if (!selectedQuoteId) {
      setDetailState({ kind: 'idle' });
      return;
    }

    let active = true;
    setDetailState({ kind: 'loading' });
    setRepriceResult(null);
    fetchPartnerQuoteDetail(partnerId, selectedQuoteId)
      .then((detail) => {
        if (active) setDetailState({ kind: 'loaded', detail });
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'BFF partner quote detail boundary is unavailable.';
        if (active) setDetailState({ kind: 'unreachable', message });
      });
    return () => {
      active = false;
    };
  }, [partnerId, selectedQuoteId]);

  async function attemptReprice(quoteId: string) {
    const result = await requestPartnerReprice(partnerId, quoteId);
    setRepriceResult(result);
  }

  return (
    <>
      <section className="hero" aria-labelledby="partner-quotes-title">
        <p className="eyebrow">Partner · CH-S01 / CH-S02 / CH-S03 / CH-S04</p>
        <h2 id="partner-quotes-title">Partner quote lifecycle</h2>
        <p>
          Review partner-shared quote state, filter by lifecycle status, and keep all detail views inside the BFF tenant context.
        </p>
      </section>

      <section className="panel" aria-labelledby="partner-quotes-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Route /partners/quotes</p>
            <h2 id="partner-quotes-heading">Quote dashboard</h2>
          </div>
          {quoteState.kind === 'loaded' ? <span className="trace-badge">tenant: {quoteState.view.tenantContext}</span> : null}
        </div>

        <div className="offer-toolbar" aria-label="Partner quote filters">
          <label htmlFor="partner-status-filter">Filter status</label>
          <select id="partner-status-filter" value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}>
            <option value="">All statuses</option>
            <option value="ACTIVE">ACTIVE</option>
            <option value="BLOCKED">BLOCKED</option>
            <option value="REPRICE_AVAILABLE">REPRICE_AVAILABLE</option>
          </select>
        </div>

        {quoteState.kind === 'loading' ? <p role="status">Loading partner quotes...</p> : null}
        {quoteState.kind === 'unreachable' ? <div className="banner banner--blocked" role="alert">{quoteState.message}</div> : null}
        {quoteState.kind === 'loaded' ? <PartnerQuoteList quotes={quoteState.view.quotes} selectedQuoteId={selectedQuoteId} onSelect={setSelectedQuoteId} /> : null}
      </section>

      <section className="panel" aria-labelledby="partner-detail-heading">
        <h2 id="partner-detail-heading">Quote detail</h2>
        {detailState.kind === 'idle' ? <p>Select a quote to view lifecycle detail without changing tenant context.</p> : null}
        {detailState.kind === 'loading' ? <p role="status">Loading partner quote detail...</p> : null}
        {detailState.kind === 'unreachable' ? <div className="banner banner--blocked" role="alert">{detailState.message}</div> : null}
        {detailState.kind === 'loaded' ? (
          <PartnerQuoteDetailPanel detail={detailState.detail} result={repriceResult} onAttemptReprice={() => void attemptReprice(detailState.detail.quoteId)} />
        ) : null}
      </section>
    </>
  );
}

function PartnerQuoteList({ quotes, selectedQuoteId, onSelect }: { quotes: PartnerQuoteSummary[]; selectedQuoteId: string | null; onSelect: (quoteId: string) => void }) {
  if (!quotes.length) return <p role="status">No partner quotes match the selected status.</p>;

  return (
    <div className="quote-table" role="table" aria-label="Partner quotes">
      <div role="row" className="quote-table__row quote-table__row--head">
        <span role="columnheader">Quote</span>
        <span role="columnheader">Status</span>
        <span role="columnheader">SLA</span>
        <span role="columnheader">Lock</span>
        <span role="columnheader">Action</span>
      </div>
      {quotes.map((quote) => (
        <div key={quote.quoteId} role="row" className={selectedQuoteId === quote.quoteId ? 'quote-table__row quote-table__row--selected' : 'quote-table__row'}>
          <span role="cell">{quote.borrowerLabel} ({quote.quoteId})</span>
          <span role="cell">{quote.status}</span>
          <span role="cell">{quote.slaState}</span>
          <span role="cell">{quote.lockState}</span>
          <span role="cell"><button type="button" onClick={() => onSelect(quote.quoteId)}>View details</button></span>
        </div>
      ))}
    </div>
  );
}

function PartnerQuoteDetailPanel({ detail, result, onAttemptReprice }: { detail: PartnerQuoteDetail; result: PartnerRepriceResult | null; onAttemptReprice: () => void }) {
  const action = detail.actions.reprice;
  return (
    <div className="partner-detail-grid">
      <dl>
        <dt>Tenant context</dt><dd>{detail.tenantContext}</dd>
        <dt>Quote status</dt><dd>{detail.status}</dd>
        <dt>SLA state</dt><dd>{detail.slaState}</dd>
        <dt>Lock state</dt><dd>{detail.lockState}</dd>
        <dt>Trace</dt><dd>{detail.uiTraceId}</dd>
      </dl>
      <ChipList label="Lifecycle events" values={detail.lifecycleEvents} />
      <ChipList label="Error flags" values={detail.errorFlags} />
      {action.visible ? (
        <button type="button" onClick={onAttemptReprice}>Request reprice</button>
      ) : (
        <button type="button" className="button-secondary" onClick={onAttemptReprice}>Show reprice guidance</button>
      )}
      {!action.permitted ? (
        <div className="banner banner--blocked" role="alert">
          <strong>Reprice blocked</strong>
          <span>{action.guidance}</span>
          <span>Support handoff route: {action.supportHandoffRoute}</span>
        </div>
      ) : null}
      {result ? (
        <div className={result.status === 'ACCEPTED' ? 'banner banner--success' : 'banner banner--blocked'} role={result.status === 'ACCEPTED' ? 'status' : 'alert'}>
          <strong>{result.status === 'ACCEPTED' ? 'Reprice request recorded' : 'Reprice request blocked'}</strong>
          <span>{result.message}</span>
          <span>{result.guidance}</span>
          <span>Support handoff route: {result.supportHandoffRoute}</span>
        </div>
      ) : null}
    </div>
  );
}

function PartnerTransportReliabilitySection({ partnerId }: { partnerId: string }) {
  const [transportState, setTransportState] = useState<PartnerTransportState>({ kind: 'loading' });
  const [selectedAttempt, setSelectedAttempt] = useState<PartnerWebhookDeliveryAttempt | null>(null);
  const [correlationId, setCorrelationId] = useState('');
  const [idempotencyConfirmed, setIdempotencyConfirmed] = useState(false);
  const [safetyConfirmed, setSafetyConfirmed] = useState(false);
  const [replayResult, setReplayResult] = useState<PartnerWebhookActionResult | null>(null);
  const [endpointTestResult, setEndpointTestResult] = useState<PartnerWebhookActionResult | null>(null);
  const [safetyResult, setSafetyResult] = useState<PartnerSafetyToggleResult | null>(null);

  useEffect(() => {
    let active = true;
    setTransportState({ kind: 'loading' });
    fetchPartnerWebhookHealth(partnerId)
      .then((view) => {
        if (!active) return;
        setTransportState({ kind: 'loaded', view });
        setSelectedAttempt((current) => current ?? view.deliveryAttempts.find((attempt) => attempt.status !== 'DELIVERED') ?? view.deliveryAttempts[0] ?? null);
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'BFF partner transport boundary is unavailable.';
        if (active) setTransportState({ kind: 'unreachable', message });
      });
    return () => {
      active = false;
    };
  }, [partnerId]);

  async function requestReplay() {
    if (!selectedAttempt || !correlationId.trim() || !idempotencyConfirmed) return;
    const result = await requestPartnerWebhookReplay(
      partnerId,
      selectedAttempt.webhookId,
      selectedAttempt.eventId,
      correlationId.trim(),
      idempotencyConfirmed,
    );
    setReplayResult(result);
  }

  async function requestEndpointTest() {
    if (!selectedAttempt) return;
    const result = await requestPartnerWebhookEndpointTest(partnerId, selectedAttempt.webhookId);
    setEndpointTestResult(result);
  }

  async function toggleSafety(route: string, webhookId: string, paused: boolean) {
    if (!safetyConfirmed) return;
    const result = await requestPartnerWebhookSafetyToggle(partnerId, webhookId, route, !paused, safetyConfirmed);
    setSafetyResult(result);
  }

  if (transportState.kind === 'loading') {
    return <section className="panel" aria-labelledby="partner-transport-heading"><h2 id="partner-transport-heading">Partner transport reliability</h2><p role="status">Loading transport health...</p></section>;
  }

  if (transportState.kind === 'unreachable') {
    return (
      <section className="panel" aria-labelledby="partner-transport-heading">
        <h2 id="partner-transport-heading">Partner transport reliability</h2>
        <div className="banner banner--blocked" role="alert">{transportState.message}</div>
      </section>
    );
  }

  const view = transportState.view;
  const replayDisabled = !selectedAttempt || !correlationId.trim() || !idempotencyConfirmed;

  return (
    <>
      <section className="hero" aria-labelledby="partner-transport-title">
        <p className="eyebrow">Partner · CH-S05 / CH-S06 / CH-S07</p>
        <h2 id="partner-transport-title">Partner transport reliability</h2>
        <p>
          Inspect webhook delivery health, replay gating, and safety toggles through relative pricing-bff APIs only.
        </p>
      </section>

      <section className="panel" aria-labelledby="partner-transport-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Route /partners/webhooks</p>
            <h2 id="partner-transport-heading">Webhook retry health</h2>
          </div>
          <span className="trace-badge">tenant: {view.tenantContext}</span>
        </div>

        <dl className="status-grid">
          <dt>Retry health</dt><dd>{view.retryHealthSummary}</dd>
          <dt>Events shown</dt><dd>{view.eventWindow}</dd>
          <dt>DLQ size</dt><dd>{view.dlqSizeStatus}</dd>
          <dt>Retry window</dt><dd>{view.retryWindowStatus}</dd>
          <dt>Trace</dt><dd>{view.uiTraceId}</dd>
        </dl>

        <div className="quote-table" role="table" aria-label="Webhook delivery attempts">
          <div role="row" className="quote-table__row quote-table__row--head">
            <span role="columnheader">Event</span>
            <span role="columnheader">Status</span>
            <span role="columnheader">Root cause</span>
            <span role="columnheader">Last success</span>
            <span role="columnheader">Action</span>
          </div>
          {view.deliveryAttempts.map((attempt) => (
            <div key={attempt.eventId} role="row" className={selectedAttempt?.eventId === attempt.eventId ? 'quote-table__row quote-table__row--selected' : 'quote-table__row'}>
              <span role="cell">{attempt.eventId} ({attempt.route})</span>
              <span role="cell">{attempt.status}</span>
              <span role="cell">{attempt.rootCauseCode}</span>
              <span role="cell">{attempt.lastSuccessfulAt}</span>
              <span role="cell"><button type="button" onClick={() => setSelectedAttempt(attempt)}>Review controls</button></span>
            </div>
          ))}
        </div>
      </section>

      <section className="panel" aria-labelledby="webhook-controls-heading">
        <h2 id="webhook-controls-heading">Replay and endpoint controls</h2>
        {selectedAttempt ? (
          <div className="partner-detail-grid">
            <dl>
              <dt>Selected webhook</dt><dd>{selectedAttempt.webhookId}</dd>
              <dt>Selected event</dt><dd>{selectedAttempt.eventId}</dd>
              <dt>Failure reason</dt><dd>{selectedAttempt.failureReason}</dd>
              <dt>Idempotency</dt><dd>{selectedAttempt.idempotencyKeyState}</dd>
              <dt>Masking</dt><dd>{selectedAttempt.maskingIndicator}</dd>
              <dt>Consent</dt><dd>{selectedAttempt.consentIndicator}</dd>
            </dl>
            <div className="field-group">
              <label htmlFor="webhook-correlation">Replay correlation id</label>
              <input id="webhook-correlation" value={correlationId} onChange={(event) => setCorrelationId(event.target.value)} />
              <p className="field-help">{view.replayAction.confirmationRequirement}</p>
            </div>
            <label className="checkbox-row">
              <input type="checkbox" checked={idempotencyConfirmed} onChange={(event) => setIdempotencyConfirmed(event.target.checked)} />
              I confirm idempotency for this replay request.
            </label>
            <button type="button" disabled={replayDisabled} onClick={() => void requestReplay()}>Request replay</button>
            {replayDisabled ? <p role="status">{view.replayAction.disabledReason}</p> : null}
            <button type="button" className="button-secondary" onClick={() => void requestEndpointTest()}>Show endpoint test guidance</button>
          </div>
        ) : <p role="status">No webhook events are available.</p>}
        {replayResult ? <TransportResultBanner result={replayResult} acceptedLabel="Webhook replay requested" blockedLabel="Webhook replay blocked" /> : null}
        {endpointTestResult ? <TransportResultBanner result={endpointTestResult} acceptedLabel="Endpoint test requested" blockedLabel="Endpoint test blocked" /> : null}
      </section>

      <section className="panel" aria-labelledby="safety-toggle-heading">
        <h2 id="safety-toggle-heading">Safety toggles</h2>
        <label className="checkbox-row">
          <input type="checkbox" checked={safetyConfirmed} onChange={(event) => setSafetyConfirmed(event.target.checked)} />
          I confirm this safety toggle change.
        </label>
        <ul className="offer-list" aria-label="Partner safety toggles">
          {view.safetyToggles.map((toggle) => (
            <li key={`${toggle.webhookId}-${toggle.route}`}>
              <h3>{toggle.route}</h3>
              <p>{toggle.visibleState}</p>
              <p>Current pause state: {toggle.paused ? 'Paused' : 'Active'}</p>
              <button type="button" disabled={!safetyConfirmed} onClick={() => void toggleSafety(toggle.route, toggle.webhookId, toggle.paused)}>
                {toggle.paused ? 'Resume auto-emit' : 'Pause auto-emit'}
              </button>
            </li>
          ))}
        </ul>
        {!safetyConfirmed ? <p role="status">Safety toggle change requires explicit confirmation.</p> : null}
        {safetyResult ? (
          <div className={safetyResult.status === 'VISIBLE' ? 'banner banner--success' : 'banner banner--blocked'} role={safetyResult.status === 'VISIBLE' ? 'status' : 'alert'}>
            <strong>{safetyResult.status === 'VISIBLE' ? 'Safety toggle visible' : 'Safety toggle blocked'}</strong>
            <span>{safetyResult.message}</span>
            <span>Current pause state: {safetyResult.paused ? 'Paused' : 'Active'}</span>
          </div>
        ) : null}
      </section>
    </>
  );
}

function TransportResultBanner({ result, acceptedLabel, blockedLabel }: { result: PartnerWebhookActionResult; acceptedLabel: string; blockedLabel: string }) {
  return (
    <div className={result.status === 'ACCEPTED' ? 'banner banner--success' : 'banner banner--blocked'} role={result.status === 'ACCEPTED' ? 'status' : 'alert'}>
      <strong>{result.status === 'ACCEPTED' ? acceptedLabel : blockedLabel}</strong>
      <span>{result.message}</span>
      <span>{result.guidance}</span>
      <span>Downstream executed: {result.downstreamExecuted ? 'yes' : 'no'}</span>
    </div>
  );
}

function LockLifecycleSection({ runId }: { runId: string }) {
  const selectedOfferId = sessionStorage.getItem(`${selectedOfferStoragePrefix}${runId}`);
  const [lockState, setLockState] = useState<LockState>({ kind: 'loading' });
  const [disclosuresAccepted, setDisclosuresAccepted] = useState(false);
  const [confirmation, setConfirmation] = useState<LockConfirmationResult | null>(null);
  const [confirming, setConfirming] = useState(false);

  useEffect(() => {
    let active = true;
    fetchLockWorkflow(tenantBoundaryPlaceholder, runId, selectedOfferId)
      .then((workflow) => {
        if (active) setLockState({ kind: 'loaded', workflow });
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'BFF lock lifecycle boundary is unavailable.';
        if (active) setLockState({ kind: 'unreachable', message });
      });
    return () => {
      active = false;
    };
  }, [runId, selectedOfferId]);

  async function submitLock() {
    if (!selectedOfferId || lockState.kind !== 'loaded' || lockState.workflow.lockDisabled || !disclosuresAccepted) return;
    setConfirming(true);
    try {
      const result = await confirmLock(tenantBoundaryPlaceholder, runId, selectedOfferId, disclosuresAccepted);
      setConfirmation(result);
      if (result.status === 'CONFIRMED' && result.statusRoute) {
        window.history.pushState({ runId, selectedOfferId, screenId: 'BRW-S05' }, '', result.statusRoute);
      }
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : 'BFF lock confirmation boundary is unavailable.';
      setConfirmation({
        runId,
        selectedOfferId,
        status: 'BLOCKED',
        lockId: null,
        lockStatus: null,
        expiresAt: null,
        statusRoute: null,
        message,
        uiTraceId: 'brw-s04-local-trace',
        events: ['LockBlocked'],
        blockers: [message],
      });
    } finally {
      setConfirming(false);
    }
  }

  if (lockState.kind === 'loading') {
    return <section className="panel" aria-labelledby="lock-heading"><h2 id="lock-heading">Lock workflow</h2><p role="status">Loading lock preconditions...</p></section>;
  }

  if (lockState.kind === 'unreachable') {
    return (
      <section className="panel" aria-labelledby="lock-heading">
        <h2 id="lock-heading">Lock workflow</h2>
        <div className="banner banner--blocked" role="alert">{lockState.message}</div>
      </section>
    );
  }

  const workflow = lockState.workflow;
  const confirmDisabled = workflow.lockDisabled || !disclosuresAccepted || confirming;

  return (
    <>
      <section className="hero" aria-labelledby="lock-title">
        <p className="eyebrow">Borrower · BRW-S04</p>
        <h2 id="lock-title">Lock terms for run {runId}</h2>
        <p>Review lock preconditions and confirm only with selected-offer context supplied by pricing-bff.</p>
      </section>

      <section className="panel" aria-labelledby="lock-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Route /quote/{runId}/lock</p>
            <h2 id="lock-heading">Lock workflow</h2>
          </div>
          <span className="trace-badge">ui_trace_id: {workflow.uiTraceId}</span>
        </div>

        {workflow.lockDisabled ? (
          <div className="banner banner--blocked" role="alert" aria-live="assertive">
            <strong>Lock is blocked</strong>
            <ul>{workflow.blockers.map((blocker) => <li key={blocker}>{blocker}</li>)}</ul>
          </div>
        ) : (
          <div className="banner banner--info" role="status">
            <strong>Ready to confirm</strong>
            <span>Selected offer {workflow.selectedOfferId} is present. Final eligibility is not inferred by the UI.</span>
          </div>
        )}

        <div className="lock-summary">
          <dl>
            <dt>Selected offer</dt><dd>{valueText(workflow.selectedOfferId)}</dd>
            <dt>Precondition status</dt><dd>{workflow.status}</dd>
            <dt>Dependency status</dt><dd>{workflow.dependencyStatus}</dd>
            <dt>Next action</dt><dd>{workflow.nextAction}</dd>
          </dl>
        </div>

        <label className="check-row">
          <input
            type="checkbox"
            checked={disclosuresAccepted}
            disabled={workflow.lockDisabled}
            onChange={(event) => setDisclosuresAccepted(event.target.checked)}
          />
          <span>{workflow.disclosureText}</span>
        </label>

        <button type="button" disabled={confirmDisabled} aria-disabled={confirmDisabled} onClick={() => void submitLock()}>
          {confirming ? 'Confirming lock...' : 'Confirm lock'}
        </button>

        {confirmation ? <LockConfirmationPanel result={confirmation} /> : null}
      </section>
    </>
  );
}

function LockConfirmationPanel({ result }: { result: LockConfirmationResult }) {
  if (result.status === 'CONFIRMED') {
    return (
      <div className="banner banner--success" role="status" aria-live="polite">
        <strong>Lock details returned</strong>
        <span>{result.message}</span>
        <span>Lock id: {result.lockId}</span>
        <span>Status: {result.lockStatus}</span>
        <span>Expiry: {result.expiresAt}</span>
      </div>
    );
  }

  return (
    <div className="banner banner--blocked" role="alert" aria-live="assertive">
      <strong>{result.status === 'CONFLICT' ? 'Lock conflict' : 'Lock remains blocked'}</strong>
      <span>{result.message}</span>
      <ul>{result.blockers.map((blocker) => <li key={blocker}>{blocker}</li>)}</ul>
      <span>Selected offer context retained: {valueText(result.selectedOfferId)}</span>
    </div>
  );
}

function LaunchBanner({ state, onRetry }: { state: LaunchState; onRetry: () => void }) {
  if (state.kind === 'idle') {
    return <p className="banner banner--info">Required fields are marked with an asterisk. Missing data keeps you on /quote/start.</p>;
  }

  if (state.kind === 'submitting') {
    return <p className="banner banner--info" role="status">Submitting borrower intake through pricing-bff...</p>;
  }

  if (state.kind === 'blocked') {
    return (
      <div className="banner banner--blocked" role="alert" aria-live="assertive">
        <strong>{state.validation.message}</strong>
        <span>Progression is blocked until the required intake fields are complete.</span>
      </div>
    );
  }

  if (state.kind === 'outage') {
    return (
      <div className="banner banner--blocked" role="alert" aria-live="assertive">
        <strong>Service outage fallback</strong>
        <span>{state.message} Retry when the BFF boundary is reachable. Support reference: {uiTraceId}.</span>
        <button type="button" onClick={onRetry}>Retry intake</button>
      </div>
    );
  }

  return (
    <div className="banner banner--success" role="status">
      <strong>Run created</strong>
      <span>Run id {state.launch.runId} is ready for BRW-S02 offers at {state.launch.nextRoute}.</span>
    </div>
  );
}

type OfferState =
  | { kind: 'loading' }
  | { kind: 'loaded'; comparison: OfferComparisonView }
  | { kind: 'unreachable'; message: string };

function OfferComparisonSection({ runId }: { runId: string }) {
  const [offerState, setOfferState] = useState<OfferState>({ kind: 'loading' });
  const [sortKey, setSortKey] = useState('payment');
  const [confidenceFilter, setConfidenceFilter] = useState('');
  const [selectedOffer, setSelectedOffer] = useState<OfferSummary | null>(null);
  const [explanation, setExplanation] = useState<OfferExplanationView | null>(null);
  const [selectionMessage, setSelectionMessage] = useState<string>('');

  useEffect(() => {
    let active = true;
    fetchOfferComparison(tenantBoundaryPlaceholder, runId)
      .then((comparison) => {
        if (!active) return;
        setOfferState({ kind: 'loaded', comparison });
        if (comparison.sortOptions[0]) setSortKey(comparison.sortOptions[0]);
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'BFF offer comparison boundary is unavailable.';
        if (active) setOfferState({ kind: 'unreachable', message });
      });
    return () => {
      active = false;
    };
  }, [runId]);

  async function inspectOffer(offer: OfferSummary) {
    setSelectedOffer(offer);
    setSelectionMessage('');
    const offerExplanation = await fetchOfferExplanation(tenantBoundaryPlaceholder, runId, offer.offerId);
    setExplanation(offerExplanation);
  }

  async function continueToLock() {
    if (!selectedOffer || !explanation || explanation.commitBlocked) {
      setSelectionMessage('Selection is blocked until explanation data is available for the selected offer.');
      return;
    }

    const result = await selectOffer(tenantBoundaryPlaceholder, runId, selectedOffer.offerId, selectedOffer.sourceScenarioId);
    if (result.status === 'SELECTED' && result.selectedOfferId && result.nextRoute) {
      sessionStorage.setItem(`${selectedOfferStoragePrefix}${runId}`, result.selectedOfferId);
      window.history.pushState({ runId, selectedOfferId: result.selectedOfferId, screenId: 'BRW-S04' }, '', result.nextRoute);
      setSelectionMessage(`Offer ${result.selectedOfferId} selected for lock workflow.`);
      return;
    }

    setSelectionMessage(result.message || 'Offer selection remains blocked.');
  }

  if (offerState.kind === 'loading') {
    return <section className="panel" aria-labelledby="offers-heading"><h2 id="offers-heading">Compare offers</h2><p role="status">Loading offers...</p></section>;
  }

  if (offerState.kind === 'unreachable') {
    return (
      <section className="panel" aria-labelledby="offers-heading">
        <h2 id="offers-heading">Compare offers</h2>
        <div className="banner banner--blocked" role="alert">{offerState.message}</div>
      </section>
    );
  }

  const comparison = offerState.comparison;
  const visibleOffers = stableSortedOffers(comparison.offers, sortKey).filter((offer) =>
    confidenceFilter ? valueText(offer.confidence).toLowerCase().includes(confidenceFilter.toLowerCase()) : true,
  );
  const commitBlocked = comparison.commitBlocked || !selectedOffer || explanation?.commitBlocked !== false;

  return (
    <>
      <section className="hero" aria-labelledby="offers-title">
        <p className="eyebrow">Borrower · BRW-S02 / BRW-S03</p>
        <h2 id="offers-title">Compare offers for run {runId}</h2>
        <p>
          Compare BFF-supplied offer rows and inspect explainability before continuing. The UI does not calculate,
          replace, or infer pricing values.
        </p>
      </section>

      <section className="panel" aria-labelledby="offers-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Route /quote/{runId}/offers</p>
            <h2 id="offers-heading">Offer comparison</h2>
          </div>
          <span className="trace-badge">ui_trace_id: {comparison.uiTraceId}</span>
        </div>

        {comparison.fallbackReason ? (
          <div className="banner banner--blocked" role="alert">
            <strong>Explanation data required</strong>
            <span>{comparison.fallbackReason}</span>
          </div>
        ) : null}

        <div className="offer-toolbar" aria-label="Offer comparison controls">
          <label htmlFor="offer-sort">Sort by</label>
          <select id="offer-sort" value={sortKey} onChange={(event) => setSortKey(event.target.value)}>
            {comparison.sortOptions.map((option) => <option key={option} value={option}>{option}</option>)}
          </select>
          <label htmlFor="confidence-filter">Filter confidence</label>
          <input
            id="confidence-filter"
            value={confidenceFilter}
            onChange={(event) => setConfidenceFilter(event.target.value)}
            placeholder="Confidence text"
          />
        </div>

        {visibleOffers.length === 0 ? (
          <p role="status">No comparable offers are loaded from pricing-bff. Selection is blocked until explainability data is available.</p>
        ) : (
          <div className="offer-grid" role="list" aria-label="Comparable offers">
            {visibleOffers.map((offer) => (
              <article
                key={offer.offerId}
                className={selectedOffer?.offerId === offer.offerId ? 'offer-card offer-card--selected' : 'offer-card'}
                role="listitem"
                aria-label={`Offer ${offer.offerId} rank ${offer.rank} confidence ${valueText(offer.confidence)}`}
              >
                <h3>{offer.productLabel || offer.offerId}</h3>
                <dl>
                  <dt>Payment</dt><dd>{valueText(offer.payment)}</dd>
                  <dt>APR</dt><dd>{valueText(offer.apr)}</dd>
                  <dt>Confidence</dt><dd>{valueText(offer.confidence)}</dd>
                </dl>
                <ChipList label="Rationale" values={offer.rationaleChips} />
                <ChipList label="Scenario flags" values={offer.scenarioFlags} />
                <button type="button" aria-pressed={selectedOffer?.offerId === offer.offerId} onClick={() => void inspectOffer(offer)}>
                  Inspect explanation
                </button>
              </article>
            ))}
          </div>
        )}
      </section>

      <section className="panel" aria-labelledby="explain-heading">
        <h2 id="explain-heading">Explanation panel</h2>
        {explanation ? (
          explanation.status === 'AVAILABLE' ? (
            <ul>{explanation.rationaleLines.map((line) => <li key={line}>{line}</li>)}</ul>
          ) : (
            <div className="banner banner--blocked" role="alert">
              <strong>Explanation missing</strong>
              <span>{explanation.message}</span>
            </div>
          )
        ) : (
          <p>Choose an offer to keep the list context visible while explanation details load.</p>
        )}
        <button type="button" disabled={commitBlocked} onClick={() => void continueToLock()}>
          Continue to lock workflow
        </button>
        {selectionMessage ? <p role="status">{selectionMessage}</p> : null}
      </section>
    </>
  );
}

function ChipList({ label, values }: { label: string; values: string[] }) {
  if (!values.length) return <p className="field-help">No {label.toLowerCase()} provided by BFF.</p>;
  return <ul className="chip-list" aria-label={label}>{values.map((value) => <li key={value}>{value}</li>)}</ul>;
}

function stableSortedOffers(offers: OfferSummary[], sortKey: string) {
  return offers
    .map((offer, index) => ({ offer, index }))
    .sort((left, right) => compareOfferValue(left.offer, right.offer, sortKey) || left.index - right.index)
    .map(({ offer }) => offer);
}

function compareOfferValue(left: OfferSummary, right: OfferSummary, sortKey: string) {
  const leftValue = offerSortValue(left, sortKey);
  const rightValue = offerSortValue(right, sortKey);
  if (leftValue < rightValue) return -1;
  if (leftValue > rightValue) return 1;
  return left.rank - right.rank;
}

function offerSortValue(offer: OfferSummary, sortKey: string) {
  const candidate = sortKey === 'apr' ? offer.apr : sortKey === 'confidence' ? offer.confidence : sortKey === 'payment' ? offer.payment : offer.rank;
  const numeric = Number(candidate);
  return Number.isFinite(numeric) ? numeric : valueText(candidate).toLowerCase();
}

function valueText(value: string | number | null | undefined) {
  if (value === null || value === undefined || value === '') return 'Not provided';
  return String(value);
}

function runIdFromPath(pathname: string) {
  const match = pathname.match(/^\/quote\/([^/]+)\/(offers|lock|status)/);
  return match ? decodeURIComponent(match[1]) : null;
}

function screenFromPath(pathname: string): 'offers' | 'lock' | 'status' | null {
  const match = pathname.match(/^\/quote\/[^/]+\/(offers|lock|status)/);
  return match ? (match[1] as 'offers' | 'lock' | 'status') : null;
}

function screenLabel(screen: 'offers' | 'lock' | 'status' | null) {
  if (screen === 'lock') return 'Lock Terms';
  if (screen === 'status') return 'Lock Status';
  return 'Compare Offers';
}

function caseIdFromOpsPath(pathname: string) {
  const match = pathname.match(/^\/ops\/cases\/([^/]+)/);
  return match ? decodeURIComponent(match[1]) : null;
}

function HealthPanel({ state }: { state: HealthState }) {
  if (state.kind === 'loading') {
    return <p role="status">Checking BFF health...</p>;
  }

  if (state.kind === 'unreachable') {
    return (
      <div role="status" className="status-card status-card--offline">
        <strong>BFF unreachable</strong>
        <span>{state.message}</span>
      </div>
    );
  }

  return (
    <div role="status" className="status-card status-card--online">
      <strong>BFF reachable</strong>
      <span>{state.health.service}</span>
      <span>{state.health.dependencyStatus}</span>
    </div>
  );
}
