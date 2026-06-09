import { type FormEvent, useEffect, useRef, useState } from 'react';
import {
  fetchOfferComparison,
  fetchOfferExplanation,
  selectOffer,
  type OfferComparisonView,
  type OfferExplanationView,
  type OfferSummary,
} from './lib/api/offers';
import {
  fetchPricingWaterfall,
  fetchScenarioIntakeMetadata,
  launchQuoteRun,
  type BorrowerIntake,
  type IntakeValidation,
  type PricingWaterfallView,
  type QuoteRunLaunch,
  type ScenarioIntakeField,
  type ScenarioIntakeMetadata,
  type WaterfallLedgerRow,
} from './lib/api/quoteRuns';
import { confirmLock, fetchLockWorkflow, type LockConfirmationResult, type LockWorkflowView } from './lib/api/locks';
import {
  fetchEligibilityModule,
  type EligibilityBlockerView,
  type EligibilityDecisionView,
  type EligibilityModuleView,
} from './lib/api/eligibility';
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
  fetchRateFeedOperations,
  type RateFeedGridBlocker,
  type RateFeedOperationsView,
} from './lib/api/rateFeedOps';
import {
  fetchPerformanceDashboard,
  type PerformanceBlocker,
  type PerformanceDashboardView,
  type PerformanceSignalGroup,
} from './lib/api/observabilityPerformance';
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
import {
  fetchCustomRuleEvidence,
  type CustomRuleEvidenceView,
  type RuleEvidenceRow,
} from './lib/api/customRules';
import {
  fetchAuditReplayWorkbench,
  type AuditReplayRecordSummary,
  type AuditReplayRunSummary,
  type AuditReplayWorkbenchView,
} from './lib/api/auditReplay';
import {
  fetchTenantPlatformCoverage,
  type TenantPlatformControl,
  type TenantPlatformCoverageView,
} from './lib/api/tenantPlatform';
import { fetchUiHealth, type UiHealth } from './lib/api/uiHealth';
import { createTenantWorkspace, type TenantSetupRequest, type TenantSetupResult } from './lib/api/tenants';
import {
  createProductCatalogEntry,
  fetchProductCatalogManager,
  type ProductCatalogManagerView,
  type ProductSetupRequest,
  type ProductSetupResult,
} from './lib/api/products';
import { resolveWorkbenchModule, WorkbenchModuleRail, workbenchModules } from './screens/workbenchShell/WorkbenchShell';

type HealthState =
  | { kind: 'loading' }
  | { kind: 'reachable'; health: UiHealth }
  | { kind: 'unreachable'; message: string };

const fallbackNotice = 'The guided workbench is available with safe local setup records where external integrations are not configured.';
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

type MetadataState =
  | { kind: 'loading' }
  | { kind: 'loaded'; metadata: ScenarioIntakeMetadata }
  | { kind: 'unreachable'; message: string };

const initialIntake: BorrowerIntake = {
  borrowerName: '',
  contactEmail: '',
  quoteGoal: '',
  scenarioName: '',
  channel: '',
  externalLoanId: '',
  sourceSystem: '',
  borrowerCreditStatus: '',
  creditScore: '',
  loanPurpose: '',
  loanAmount: '',
  propertyState: '',
  occupancyType: '',
  monthlyIncome: '',
  liquidAssets: '',
};

const initialTenantSetup: TenantSetupRequest = {
  tenantName: '',
  operationsContact: '',
  launchGoal: '',
};

const initialProductSetup: ProductSetupRequest = {
  productName: '',
  productOwner: '',
  borrowerNeed: '',
};

export function App() {
  const [healthState, setHealthState] = useState<HealthState>({ kind: 'loading' });
  const [intake, setIntake] = useState<BorrowerIntake>(initialIntake);
  const [tenantSetup, setTenantSetup] = useState<TenantSetupRequest>(initialTenantSetup);
  const [productSetup, setProductSetup] = useState<ProductSetupRequest>(initialProductSetup);
  const [errors, setErrors] = useState<Partial<Record<keyof BorrowerIntake, string>>>({});
  const [tenantErrors, setTenantErrors] = useState<Partial<Record<keyof TenantSetupRequest, string>>>({});
  const [productErrors, setProductErrors] = useState<Partial<Record<keyof ProductSetupRequest, string>>>({});
  const [launchState, setLaunchState] = useState<LaunchState>({ kind: 'idle' });
  const [metadataState, setMetadataState] = useState<MetadataState>({ kind: 'loading' });
  const [tenantResult, setTenantResult] = useState<TenantSetupResult | null>(null);
  const [productResult, setProductResult] = useState<ProductSetupResult | null>(null);
  const [activeRunId, setActiveRunId] = useState<string | null>(() => runIdFromPath(window.location.pathname));
  const [activeScreen, setActiveScreen] = useState<'offers' | 'lock' | 'status' | 'eligibility' | 'waterfall' | null>(() => screenFromPath(window.location.pathname));
  const [partnerTransportActive] = useState(() => partnerTransportPathActive(window.location.pathname));
  const [partnerQuotesActive] = useState(() => window.location.pathname.startsWith('/partners/quotes'));
  const [opsCasesActive] = useState(() => opsPathActive(window.location.pathname));
  const [performanceActive] = useState(() => performancePathActive(window.location.pathname));
  const [rateFeedOpsActive] = useState(() => rateFeedOpsPathActive(window.location.pathname));
  const [complianceActive] = useState(() => compliancePathActive(window.location.pathname));
  const [qualityActive] = useState(() => qualityPathActive(window.location.pathname));
  const [adminActive] = useState(() => adminPathActive(window.location.pathname));
  const [productCatalogManagerActive] = useState(() => productCatalogManagerPathActive(window.location.pathname));
  const [customRulesActive] = useState(() => customRulesPathActive(window.location.pathname));
  const [auditReplayActive] = useState(() => auditReplayPathActive(window.location.pathname));
  const [tenantPlatformActive] = useState(() => tenantPlatformPathActive(window.location.pathname));
  const activeModule = resolveWorkbenchModule(window.location.pathname);
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
        const message = error instanceof Error ? error.message : 'Connection status request failed';
        if (active) setHealthState({ kind: 'unreachable', message });
      });

    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    let active = true;

    fetchScenarioIntakeMetadata(tenantBoundaryPlaceholder)
      .then((metadata) => {
        if (active) setMetadataState({ kind: 'loaded', metadata });
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'Scenario intake metadata is unavailable.';
        if (active) setMetadataState({ kind: 'unreachable', message });
      });

    return () => {
      active = false;
    };
  }, []);

  function updateField(field: keyof BorrowerIntake, value: string) {
    setIntake((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
  }

  function updateTenantField(field: keyof TenantSetupRequest, value: string) {
    setTenantSetup((current) => ({ ...current, [field]: value }));
    setTenantErrors((current) => ({ ...current, [field]: undefined }));
  }

  function updateProductField(field: keyof ProductSetupRequest, value: string) {
    setProductSetup((current) => ({ ...current, [field]: value }));
    setProductErrors((current) => ({ ...current, [field]: undefined }));
  }

  function validateTenantSetup(values: TenantSetupRequest): Partial<Record<keyof TenantSetupRequest, string>> {
    const nextErrors: Partial<Record<keyof TenantSetupRequest, string>> = {};
    if (!values.tenantName.trim()) nextErrors.tenantName = 'Workspace name is required.';
    if (!values.operationsContact.trim()) nextErrors.operationsContact = 'Operations contact is required.';
    if (!values.launchGoal.trim()) nextErrors.launchGoal = 'Launch goal is required.';
    return nextErrors;
  }

  function validateProductSetup(values: ProductSetupRequest): Partial<Record<keyof ProductSetupRequest, string>> {
    const nextErrors: Partial<Record<keyof ProductSetupRequest, string>> = {};
    if (!values.productName.trim()) nextErrors.productName = 'Product name is required.';
    if (!values.productOwner.trim()) nextErrors.productOwner = 'Product owner is required.';
    if (!values.borrowerNeed.trim()) nextErrors.borrowerNeed = 'Borrower need is required.';
    return nextErrors;
  }

  function focusFirstInvalid(nextErrors: Partial<Record<keyof BorrowerIntake, string>>) {
    if (nextErrors.borrowerName) borrowerNameRef.current?.focus();
    else if (nextErrors.contactEmail) contactEmailRef.current?.focus();
    else if (nextErrors.quoteGoal) quoteGoalRef.current?.focus();
  }

  function validateRequiredFields(values: BorrowerIntake): Partial<Record<keyof BorrowerIntake, string>> {
    const nextErrors: Partial<Record<keyof BorrowerIntake, string>> = {};
    const metadataFields = metadataState.kind === 'loaded'
      ? metadataState.metadata.fieldGroups.flatMap((group) => group.fields)
      : [];
    const requiredFields: Pick<ScenarioIntakeField, 'fieldId' | 'label'>[] = [
      { fieldId: 'borrowerName', label: 'Borrower name' },
      { fieldId: 'contactEmail', label: 'Contact email' },
      { fieldId: 'quoteGoal', label: 'Quote goal' },
      ...metadataFields.filter((field) => field.required),
    ];

    requiredFields.forEach((field) => {
      if (!values[field.fieldId].trim()) nextErrors[field.fieldId] = `${field.label} is required.`;
    });
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
      const message = error instanceof Error ? error.message : 'Quick quote setup is unavailable.';
      setLaunchState({ kind: 'outage', message });
    }
  }

  async function submitTenantSetup(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextErrors = validateTenantSetup(tenantSetup);
    if (Object.keys(nextErrors).length > 0) {
      setTenantErrors(nextErrors);
      setTenantResult({ tenantId: null, status: 'BLOCKED', message: 'Complete the highlighted workspace fields.', nextStep: 'Finish setup details.', placeholders: [] });
      return;
    }
    setTenantResult(await createTenantWorkspace(tenantSetup));
  }

  async function submitProductSetup(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextErrors = validateProductSetup(productSetup);
    if (Object.keys(nextErrors).length > 0) {
      setProductErrors(nextErrors);
      setProductResult({ productId: null, status: 'BLOCKED', message: 'Complete the highlighted product fields.', nextStep: 'Finish product details.', placeholders: [] });
      return;
    }
    setProductResult(await createProductCatalogEntry(productSetup));
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
          {workbenchModules.map((screen) => (
            <a key={screen.id} href={screen.routePattern.replace(':runId', activeRunId ?? 'run-test')} aria-current={screen.id === activeModule.id ? 'page' : undefined}>
              {screen.label}
            </a>
          ))}
          <a href="#status-panel">Connection status</a>
          <a href="#notice-panel">Notices</a>
          <a href="#help-panel">Help</a>
        </nav>

        <main id="main-content" tabIndex={-1}>
          <nav aria-label="Breadcrumb" className="breadcrumbs">
            <ol>
              <li>Home</li>
              <li aria-current="page">{activeRunId ? screenLabel(activeScreen) : activeModule.breadcrumb}</li>
            </ol>
          </nav>

          {!activeRunId && activeModule.id === 'shell' ? <WorkbenchModuleRail activeModuleId={activeModule.id} /> : null}

          {tenantPlatformActive ? (
            <TenantPlatformCoverageSection />
          ) : auditReplayActive ? (
            <AuditReplayWorkbenchSection />
          ) : customRulesActive ? (
            <CustomRuleEvidenceSection />
          ) : performanceActive ? (
            <PerformanceDashboardSection />
          ) : rateFeedOpsActive ? (
            <RateFeedOperationsSection />
          ) : productCatalogManagerActive ? (
            <ProductCatalogManagerSection />
          ) : adminActive ? (
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
          ) : activeRunId && activeScreen === 'eligibility' ? (
            <EligibilityExplanationSection runId={activeRunId} />
          ) : activeRunId && activeScreen === 'waterfall' ? (
            <PricingWaterfallSection runId={activeRunId} />
          ) : activeRunId ? (
            <OfferComparisonSection runId={activeRunId} />
          ) : (
            <>
              <section className="hero" aria-labelledby="borrower-title">
                <p className="eyebrow">Guided pricing workflow</p>
                <h2 id="borrower-title">Start pricing work in one place</h2>
                <p>
                  Set up a tenant workspace, prepare product catalog entries, start a quick quote, compare offers, and continue
                  to lock and operations surfaces without entering rates or policy assumptions in the UI.
                </p>
              </section>

              <section id="tenant-setup" className="panel" aria-labelledby="tenant-heading">
                <div className="panel-heading-row">
                  <div>
                    <p className="eyebrow">Workspace setup</p>
                    <h2 id="tenant-heading">Tenant onboarding</h2>
                  </div>
                </div>
                <p className="field-help">Create a local setup record for a tenant workspace. External identity and tenant integrations remain configuration-owned.</p>
                <form className="intake-form" onSubmit={submitTenantSetup} noValidate>
                  <WorkflowField id="tenantName" label="Workspace name" value={tenantSetup.tenantName} error={tenantErrors.tenantName} onChange={(value) => updateTenantField('tenantName', value)} />
                  <WorkflowField id="operationsContact" label="Operations contact" value={tenantSetup.operationsContact} error={tenantErrors.operationsContact} onChange={(value) => updateTenantField('operationsContact', value)} />
                  <WorkflowField id="launchGoal" label="Launch goal" value={tenantSetup.launchGoal} error={tenantErrors.launchGoal} onChange={(value) => updateTenantField('launchGoal', value)} multiline />
                  <button type="submit">Create tenant workspace</button>
                </form>
                {tenantResult ? <WorkflowResultBanner result={tenantResult} successLabel="Tenant workspace recorded" blockedLabel="Tenant setup needs attention" /> : null}
              </section>

              <section id="product-management" className="panel" aria-labelledby="product-heading">
                <div className="panel-heading-row">
                  <div>
                    <p className="eyebrow">Catalog setup</p>
                    <h2 id="product-heading">Product management</h2>
                  </div>
                </div>
                <p className="field-help">Record product catalog intent without entering mortgage pricing rates, eligibility thresholds, or regulatory values.</p>
                <form className="intake-form" onSubmit={submitProductSetup} noValidate>
                  <WorkflowField id="productName" label="Product name" value={productSetup.productName} error={productErrors.productName} onChange={(value) => updateProductField('productName', value)} />
                  <WorkflowField id="productOwner" label="Product owner" value={productSetup.productOwner} error={productErrors.productOwner} onChange={(value) => updateProductField('productOwner', value)} />
                  <WorkflowField id="borrowerNeed" label="Borrower need served" value={productSetup.borrowerNeed} error={productErrors.borrowerNeed} onChange={(value) => updateProductField('borrowerNeed', value)} multiline />
                  <button type="submit">Add product draft</button>
                </form>
                {productResult ? <WorkflowResultBanner result={productResult} successLabel="Product draft recorded" blockedLabel="Product setup needs attention" /> : null}
              </section>

              <section id="borrower-intake" className="panel" aria-labelledby="intake-heading">
                <div className="panel-heading-row">
                  <div>
                    <p className="eyebrow">Quick quote</p>
                    <h2 id="intake-heading">Borrower quote details</h2>
                  </div>
                  {launchState.kind === 'outage' ? null : <DiagnosticsDetails items={[`Support reference: ${uiTraceId}`]} />}
                </div>

                <LaunchBanner state={launchState} onRetry={() => setLaunchState({ kind: 'idle' })} />
                <ScenarioIntakeMetadataPanel state={metadataState} />

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

                  {metadataState.kind === 'loaded' ? <AdvancedScenarioIntake metadata={metadataState.metadata} intake={intake} errors={errors} onChange={updateField} /> : null}

                  <button type="submit" disabled={launchState.kind === 'submitting'}>
                    {launchState.kind === 'submitting' ? 'Starting quote...' : 'Start quick quote'}
                  </button>
                </form>
              </section>
            </>
          )}

          <section id="status-panel" className="panel" aria-labelledby="status-heading" tabIndex={-1}>
            <h2 id="status-heading">Connection status</h2>
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

type RateFeedOpsState =
  | { kind: 'loading' }
  | { kind: 'loaded'; view: RateFeedOperationsView }
  | { kind: 'unreachable'; message: string };

type PerformanceDashboardState =
  | { kind: 'loading' }
  | { kind: 'loaded'; view: PerformanceDashboardView }
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

type CustomRuleEvidenceState =
  | { kind: 'loading' }
  | { kind: 'loaded'; view: CustomRuleEvidenceView }
  | { kind: 'unreachable'; message: string };

type AuditReplayWorkbenchState =
  | { kind: 'loading' }
  | { kind: 'loaded'; view: AuditReplayWorkbenchView }
  | { kind: 'unreachable'; message: string };

type TenantPlatformCoverageState =
  | { kind: 'loading' }
  | { kind: 'loaded'; view: TenantPlatformCoverageView }
  | { kind: 'unreachable'; message: string };

type ProductCatalogManagerState =
  | { kind: 'loading' }
  | { kind: 'loaded'; view: ProductCatalogManagerView }
  | { kind: 'unreachable'; message: string };

function opsPathActive(pathname: string) {
  return pathname.startsWith('/ops/dashboard') || pathname.startsWith('/ops/queues') || pathname.startsWith('/ops/cases') || pathname.startsWith('/ops/escalations');
}

function rateFeedOpsPathActive(pathname: string) {
  return pathname.startsWith('/ops/rate-feeds');
}

function performancePathActive(pathname: string) {
  return pathname.startsWith('/ops/performance');
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

function productCatalogManagerPathActive(pathname: string) {
  return pathname.startsWith('/admin/products/catalog');
}

function customRulesPathActive(pathname: string) {
  return pathname.startsWith('/custom-rules/');
}

function auditReplayPathActive(pathname: string) {
  return pathname.startsWith('/audit/replay');
}

function tenantPlatformPathActive(pathname: string) {
  return pathname.startsWith('/platform/tenant-context');
}

function WorkflowField({
  id,
  label,
  value,
  error,
  multiline = false,
  onChange,
}: {
  id: string;
  label: string;
  value: string;
  error?: string;
  multiline?: boolean;
  onChange: (value: string) => void;
}) {
  const errorId = `${id}-error`;
  return (
    <div className={multiline ? 'field-group field-group--full' : 'field-group'}>
      <label htmlFor={id}>{label} <span aria-hidden="true">*</span></label>
      {multiline ? (
        <textarea id={id} value={value} aria-invalid={Boolean(error)} aria-describedby={error ? errorId : undefined} onChange={(event) => onChange(event.target.value)} />
      ) : (
        <input id={id} value={value} aria-invalid={Boolean(error)} aria-describedby={error ? errorId : undefined} onChange={(event) => onChange(event.target.value)} />
      )}
      {error ? <p id={errorId} role="alert">{error}</p> : null}
    </div>
  );
}

function WorkflowResultBanner({ result, successLabel, blockedLabel }: { result: TenantSetupResult | ProductSetupResult; successLabel: string; blockedLabel: string }) {
  const accepted = result.status === 'RECORDED';
  return (
    <div className={accepted ? 'banner banner--success' : 'banner banner--blocked'} role={accepted ? 'status' : 'alert'}>
      <strong>{accepted ? successLabel : blockedLabel}</strong>
      <span>{result.message}</span>
      <span>{result.nextStep}</span>
      <ChipList label="Setup notes" values={result.placeholders} />
    </div>
  );
}

function DiagnosticsDetails({ items }: { items: string[] }) {
  if (!items.length) return null;
  return (
    <details className="trace-badge">
      <summary>Technical details for support</summary>
      <ul>{items.map((item) => <li key={item}>{item}</li>)}</ul>
    </details>
  );
}

function serviceReadinessText(value: string | null | undefined) {
  if (!value) return 'Not provided';
  return 'Configuration needed before live service use.';
}

function ScenarioIntakeMetadataPanel({ state }: { state: MetadataState }) {
  if (state.kind === 'loading') {
    return <p className="banner banner--info" role="status">Loading backend-owned scenario intake metadata...</p>;
  }

  if (state.kind === 'unreachable') {
    return <p className="banner banner--blocked" role="alert">{state.message}</p>;
  }

  const metadata = state.metadata;
  return (
    <div className="scenario-metadata-panel" aria-label="Scenario intake metadata evidence">
      <dl className="status-grid">
        <dt>Metadata source</dt><dd>{businessFacingText(metadata.dependencyStatus)}</dd>
        <dt>Audit package</dt><dd>{businessFacingText(metadata.auditPackageId)}</dd>
        <dt>Replay hash</dt><dd>{businessFacingText(metadata.replayHashRef)}</dd>
      </dl>
      <ChipList label="Decision-quality controls" values={metadata.decisionControls.map(businessFacingText)} />
      <ChipList label="Scenario intake blockers" values={metadata.validationIssues.map((issue) => `${issue.severity}: ${issue.message}`)} />
      <p className="field-help">{businessFacingText(metadata.fallbackReason)}</p>
    </div>
  );
}

function AdvancedScenarioIntake({
  metadata,
  intake,
  errors,
  onChange,
}: {
  metadata: ScenarioIntakeMetadata;
  intake: BorrowerIntake;
  errors: Partial<Record<keyof BorrowerIntake, string>>;
  onChange: (field: keyof BorrowerIntake, value: string) => void;
}) {
  return (
    <details className="advanced-intake" open>
      <summary>Advanced scenario facts from backend metadata</summary>
      <div className="advanced-intake__groups">
        {metadata.fieldGroups.map((group) => (
          <fieldset key={group.groupId} className="advanced-intake__group">
            <legend>{group.label}</legend>
            <p className="field-help">{group.helpText}</p>
            {group.fields.map((field) => <MetadataField key={field.fieldId} field={field} intake={intake} error={errors[field.fieldId]} onChange={onChange} />)}
          </fieldset>
        ))}
      </div>
    </details>
  );
}

function MetadataField({
  field,
  intake,
  error,
  onChange,
}: {
  field: ScenarioIntakeField;
  intake: BorrowerIntake;
  error?: string;
  onChange: (field: keyof BorrowerIntake, value: string) => void;
}) {
  const errorId = `${field.fieldId}-metadata-error`;
  const helpId = `${field.fieldId}-metadata-help`;
  return (
    <div className={field.dataType === 'textarea' ? 'field-group field-group--full' : 'field-group'}>
      <label htmlFor={field.fieldId}>{field.label} {field.required ? <span aria-hidden="true">*</span> : null}</label>
      {field.dataType === 'textarea' ? (
        <textarea id={field.fieldId} value={intake[field.fieldId]} aria-invalid={Boolean(error)} aria-describedby={error ? errorId : helpId} onChange={(event) => onChange(field.fieldId, event.target.value)} />
      ) : (
        <input id={field.fieldId} type={field.dataType === 'email' ? 'email' : field.dataType === 'number' ? 'number' : 'text'} value={intake[field.fieldId]} aria-invalid={Boolean(error)} aria-describedby={error ? errorId : helpId} onChange={(event) => onChange(field.fieldId, event.target.value)} />
      )}
      <p id={helpId} className="field-help">{field.helpText} Source: {businessFacingText(field.sourceRef)}. Decision quality: {businessFacingText(field.decisionQuality)}.</p>
      <ChipList label={`${field.fieldId} validation messages`} values={field.validationMessages.map(businessFacingText)} />
      {error ? <p id={errorId} role="alert">{error}</p> : null}
    </div>
  );
}

function ProductCatalogManagerSection() {
  const [catalogState, setCatalogState] = useState<ProductCatalogManagerState>({ kind: 'loading' });

  useEffect(() => {
    let active = true;
    fetchProductCatalogManager()
      .then((view) => {
        if (active) setCatalogState({ kind: 'loaded', view });
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'Product catalog manager is unavailable.';
        if (active) setCatalogState({ kind: 'unreachable', message });
      });
    return () => {
      active = false;
    };
  }, []);

  if (catalogState.kind === 'loading') {
    return <section className="panel" aria-labelledby="product-catalog-manager-heading"><h2 id="product-catalog-manager-heading">Product catalog manager</h2><p role="status">Loading product catalog manager...</p></section>;
  }

  if (catalogState.kind === 'unreachable') {
    return (
      <section className="panel" aria-labelledby="product-catalog-manager-heading">
        <h2 id="product-catalog-manager-heading">Product catalog manager</h2>
        <div className="banner banner--blocked" role="alert">{catalogState.message}</div>
      </section>
    );
  }

  const view = catalogState.view;
  return (
    <>
      <section className="hero hero--admin" aria-labelledby="product-catalog-title">
        <p className="eyebrow">Catalog cockpit</p>
        <h2 id="product-catalog-title">Product catalog manager</h2>
        <p>
          Review product drafts, domain lists, lifecycle controls, snapshots, events, and audit evidence from backend-owned
          metadata. Publishing stays blocked when catalog contracts are unavailable instead of using UI-side product policy.
        </p>
      </section>

      <section className="panel" aria-labelledby="product-catalog-manager-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Backend-owned catalog areas</p>
            <h2 id="product-catalog-manager-heading">Catalog sections and validation</h2>
          </div>
          <DiagnosticsDetails items={[`Workspace ${view.tenantContext}`, `Support reference: ${view.uiTraceId}`]} />
        </div>
        <p className="field-help">{businessFacingText(view.fallbackReason)}</p>
        <div className="module-rail__grid" role="list" aria-label="Product catalog manager sections">
          {view.areas.map((area) => (
            <article key={area.areaId} className="module-card" role="listitem">
              <p className="module-card__route">{businessFacingText(area.status)}</p>
              <strong className="module-card__title">{area.label}</strong>
              <p>{businessFacingText(area.guidance)}</p>
              <dl>
                <dt>Metadata source</dt>
                <dd>{businessFacingText(area.sourceRef)}</dd>
              </dl>
              <ChipList label={`${area.label} fields`} values={area.fields.map(businessFacingText)} />
              <ChipList label={`${area.label} validation`} values={area.validationMessages.map(businessFacingText)} />
            </article>
          ))}
        </div>
      </section>

      <section className="panel" aria-labelledby="catalog-lifecycle-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Lifecycle and evidence</p>
            <h2 id="catalog-lifecycle-heading">Approval, publish, rollback, snapshots, and audit</h2>
          </div>
        </div>
        <div className="banner banner--blocked" role="alert">
          <strong>{businessFacingText(view.lifecycle.state)}</strong>
          <span>{businessFacingText(view.lifecycle.blocker)}</span>
        </div>
        <dl className="status-grid">
          <dt>Actions disabled</dt><dd>{view.lifecycle.actionsDisabled ? 'Yes' : 'No'}</dd>
          <dt>Setup status</dt><dd>{businessFacingText(view.dependencyStatus)}</dd>
        </dl>
        <ChipList label="Lifecycle actions" values={view.lifecycle.actions.map(businessFacingText)} />
        <ChipList label="Snapshot and event references" values={view.lifecycle.snapshotRefs.map(businessFacingText)} />
        <ChipList label="Audit and replay references" values={view.lifecycle.auditRefs.map(businessFacingText)} />
        <ChipList label="Catalog manager events" values={view.events.map(businessFacingText)} />
      </section>
    </>
  );
}

function CustomRuleEvidenceSection() {
  const [ruleState, setRuleState] = useState<CustomRuleEvidenceState>({ kind: 'loading' });

  useEffect(() => {
    let active = true;
    fetchCustomRuleEvidence()
      .then((view) => {
        if (active) setRuleState({ kind: 'loaded', view });
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'Custom rule evidence is unavailable.';
        if (active) setRuleState({ kind: 'unreachable', message });
      });
    return () => {
      active = false;
    };
  }, []);

  if (ruleState.kind === 'loading') {
    return <section className="panel" aria-labelledby="custom-rules-heading"><h2 id="custom-rules-heading">Custom rule evidence</h2><p role="status">Loading custom rule evidence...</p></section>;
  }

  if (ruleState.kind === 'unreachable') {
    return (
      <section className="panel" aria-labelledby="custom-rules-heading">
        <h2 id="custom-rules-heading">Custom rule evidence</h2>
        <div className="banner banner--blocked" role="alert">{ruleState.message}</div>
      </section>
    );
  }

  const view = ruleState.view;
  return (
    <>
      <section className="hero" aria-labelledby="custom-rules-title">
        <p className="eyebrow">Custom rules · PII-21-S01</p>
        <h2 id="custom-rules-title">Custom field and calculation evidence</h2>
        <p>
          Review backend-supplied field metadata, decision-quality blockers, matched and skipped rules, and replay references.
          The workbench displays configured evidence only; it does not calculate prices or infer mortgage policy.
        </p>
      </section>

      <section className="panel" aria-labelledby="custom-rules-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Metadata-driven fields</p>
            <h2 id="custom-rules-heading">Custom rule evidence</h2>
          </div>
          <DiagnosticsDetails items={[`Support reference: ${view.uiTraceId}`, `Workspace ${view.tenantContext}`]} />
        </div>

        <div className="banner banner--blocked" role="alert">
          <strong>Rule-backed commit blocked</strong>
          <span>{businessFacingText(view.fallbackReason)}</span>
          <span>Setup status: {serviceReadinessText(view.dependencyStatus)}</span>
          <ChipList label="Commit blockers" values={view.commitBlockers.map(businessFacingText)} />
        </div>

        <div className="quote-table" role="table" aria-label="Custom field metadata">
          <div role="row" className="quote-table__row quote-table__row--head">
            <span role="columnheader">Field</span>
            <span role="columnheader">Type</span>
            <span role="columnheader">Decision quality</span>
            <span role="columnheader">Source</span>
            <span role="columnheader">Rule state</span>
          </div>
          {view.fields.map((field) => (
            <div key={field.fieldId} role="row" className="quote-table__row">
              <span role="cell"><strong>{field.label}</strong><br /><span className="field-help">{field.helpText}</span></span>
              <span role="cell">{businessFacingText(field.dataType)}</span>
              <span role="cell">{businessFacingText(field.decisionQuality)}</span>
              <span role="cell">{businessFacingText(field.sourceRef)}</span>
              <span role="cell">{field.requiredForRules ? 'Required by backend rule evidence' : 'Optional metadata'}<ChipList label={`${field.fieldId} allowed values`} values={field.allowedValues.map(businessFacingText)} /><ChipList label={`${field.fieldId} validation messages`} values={field.validationMessages.map(businessFacingText)} /></span>
            </div>
          ))}
        </div>
        <button type="button" disabled={view.commitDisabled} aria-describedby="rule-commit-blockers">Commit rule-backed calculation</button>
        <p id="rule-commit-blockers" className="field-help">Commit remains disabled while backend evidence reports UNKNOWN or CONFLICTING required facts.</p>
      </section>

      <section className="panel" aria-labelledby="calculation-evidence-heading">
        <h2 id="calculation-evidence-heading">Calculation evidence panel</h2>
        <dl className="status-grid">
          <dt>Precision</dt><dd>{businessFacingText(view.evidence.precision)}</dd>
          <dt>Replay hash</dt><dd>{businessFacingText(view.evidence.replayHashRef)}</dd>
        </dl>
        <ChipList label="Reason codes" values={view.evidence.reasonCodes.map(businessFacingText)} />
        <RuleEvidenceTable label="Matched rules" rows={view.evidence.matchedRules} />
        <RuleEvidenceTable label="Skipped rules" rows={view.evidence.skippedRules} />
      </section>

      <section className="panel" aria-labelledby="design-evidence-heading">
        <h2 id="design-evidence-heading">Design evidence status</h2>
        <div className="banner banner--blocked" role="alert">
          <strong>{businessFacingText(view.designEvidence.status)}</strong>
          <span>{view.designEvidence.blocker}</span>
          <ChipList label="Safe design evidence options" values={view.designEvidence.safeOptions} />
        </div>
      </section>
    </>
  );
}

function AuditReplayWorkbenchSection() {
  const [auditState, setAuditState] = useState<AuditReplayWorkbenchState>({ kind: 'loading' });

  useEffect(() => {
    let active = true;
    fetchAuditReplayWorkbench()
      .then((view) => {
        if (active) setAuditState({ kind: 'loaded', view });
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'Audit replay workbench is unavailable.';
        if (active) setAuditState({ kind: 'unreachable', message });
      });
    return () => {
      active = false;
    };
  }, []);

  if (auditState.kind === 'loading') {
    return <section className="panel" aria-labelledby="audit-replay-heading"><h2 id="audit-replay-heading">Audit replay evidence workbench</h2><p role="status">Loading audit replay evidence...</p></section>;
  }

  if (auditState.kind === 'unreachable') {
    return (
      <section className="panel" aria-labelledby="audit-replay-heading">
        <h2 id="audit-replay-heading">Audit replay evidence workbench</h2>
        <div className="banner banner--blocked" role="alert">{auditState.message}</div>
      </section>
    );
  }

  const view = auditState.view;
  return (
    <>
      <section className="hero" aria-labelledby="audit-replay-title">
        <p className="eyebrow">Audit replay · PII-22-S10</p>
        <h2 id="audit-replay-title">Audit replay evidence workbench</h2>
        <p>
          Search audit records, verify integrity, inspect quote and lock replay blockers, and keep evidence export decisions
          backend-owned. The UI displays refs and states only; it does not infer pricing, retention, or compliance rules.
        </p>
      </section>

      <section className="panel" aria-labelledby="audit-replay-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Search and integrity</p>
            <h2 id="audit-replay-heading">Audit record search results</h2>
          </div>
          <DiagnosticsDetails items={[`Support reference: ${view.uiTraceId}`, `Workspace ${view.tenantContext}`]} />
        </div>
        <div className="banner banner--blocked" role="alert">
          <strong>Audit replay setup required</strong>
          <span>{businessFacingText(view.fallbackReason)}</span>
          <span>Setup status: {serviceReadinessText(view.dependencyStatus)}</span>
          <ChipList label="Audit replay blockers" values={view.blockers.map(businessFacingText)} />
        </div>
        <div className="quote-table" role="table" aria-label="Audit replay records">
          <div role="row" className="quote-table__row quote-table__row--head">
            <span role="columnheader">Event</span>
            <span role="columnheader">Subject</span>
            <span role="columnheader">Hash integrity</span>
            <span role="columnheader">Redaction and retention</span>
            <span role="columnheader">Export eligibility</span>
          </div>
          {view.records.map((record) => <AuditRecordEvidenceRow key={record.eventId} record={record} />)}
        </div>
      </section>

      <section className="panel" aria-labelledby="audit-replay-runs-heading">
        <h2 id="audit-replay-runs-heading">Quote and lock replay diffs</h2>
        <div className="module-rail__grid" role="list" aria-label="Replay run summaries">
          {view.replayRuns.map((run) => <AuditReplayRunCard key={run.runId} run={run} />)}
        </div>
      </section>

      <section className="panel" aria-labelledby="audit-export-heading">
        <h2 id="audit-export-heading">Evidence export retention and redaction</h2>
        <dl className="status-grid">
          <dt>Export id</dt><dd>{businessFacingText(view.exportSummary.exportId)}</dd>
          <dt>Status</dt><dd>{businessFacingText(view.exportSummary.status)}</dd>
          <dt>Redaction profile</dt><dd>{businessFacingText(view.exportSummary.redactionProfile)}</dd>
          <dt>Retention until</dt><dd>{businessFacingText(view.exportSummary.retentionUntil)}</dd>
          <dt>Legal hold</dt><dd>{view.exportSummary.legalHold ? 'Active' : 'Not active'}</dd>
          <dt>Download eligible</dt><dd>{view.exportSummary.downloadEligible ? 'Yes' : 'No'}</dd>
          <dt>Manifest hash</dt><dd>{businessFacingText(view.exportSummary.manifestHash)}</dd>
        </dl>
        <button type="button" disabled={!view.exportSummary.downloadEligible} aria-describedby="audit-export-blockers">Download evidence export</button>
        <div id="audit-export-blockers">
          <ChipList label="Export blockers" values={view.exportSummary.blockers.map(businessFacingText)} />
          <ChipList label="Audit replay events" values={view.events.map(businessFacingText)} />
        </div>
      </section>

      <section className="panel" aria-labelledby="audit-contract-heading">
        <h2 id="audit-contract-heading">Backend contract registry</h2>
        <div className="quote-table" role="table" aria-label="Audit replay contract registry">
          <div role="row" className="quote-table__row quote-table__row--head">
            <span role="columnheader">Contract</span>
            <span role="columnheader">Route</span>
            <span role="columnheader">Preserved backend decision</span>
          </div>
          {view.contractRefs.map((contract) => (
            <div key={contract.contractId} role="row" className="quote-table__row">
              <span role="cell">{businessFacingText(contract.contractId)}</span>
              <span role="cell">{contract.route}</span>
              <span role="cell">{businessFacingText(contract.preservedDecision)}</span>
            </div>
          ))}
        </div>
      </section>
    </>
  );
}

function AuditRecordEvidenceRow({ record }: { record: AuditReplayRecordSummary }) {
  return (
    <div role="row" className="quote-table__row">
      <span role="cell"><strong>{businessFacingText(record.eventId)}</strong><br /><span className="field-help">{businessFacingText(record.action)}</span></span>
      <span role="cell">{businessFacingText(record.subjectType)} · {businessFacingText(record.subjectId)}</span>
      <span role="cell">{businessFacingText(record.hashIntegrity)}<ChipList label={`${record.eventId} evidence refs`} values={record.evidenceRefs.map(businessFacingText)} /></span>
      <span role="cell">{businessFacingText(record.redactionProfile)}<br />{businessFacingText(record.retentionState)}<br />Legal hold: {record.legalHold ? 'active' : 'not active'}</span>
      <span role="cell">{businessFacingText(record.exportEligibility)}</span>
    </div>
  );
}

function AuditReplayRunCard({ run }: { run: AuditReplayRunSummary }) {
  return (
    <article className="module-card module-card--light" role="listitem">
      <p className="module-card__route">{businessFacingText(run.status)}</p>
      <strong className="module-card__title">{businessFacingText(run.replayType)} replay for {businessFacingText(run.subjectId)}</strong>
      <dl>
        <dt>Original hash</dt><dd>{businessFacingText(run.originalHash)}</dd>
        <dt>Replay hash</dt><dd>{businessFacingText(run.replayHash)}</dd>
      </dl>
      <ChipList label={`${run.runId} diffs`} values={run.diffs.map(businessFacingText)} />
      <ChipList label={`${run.runId} version refs`} values={run.versionRefs.map(businessFacingText)} />
      <ChipList label={`${run.runId} missing dependencies`} values={run.missingDependencyBlockers.map(businessFacingText)} />
    </article>
  );
}

function TenantPlatformCoverageSection() {
  const [coverageState, setCoverageState] = useState<TenantPlatformCoverageState>({ kind: 'loading' });

  useEffect(() => {
    let active = true;
    fetchTenantPlatformCoverage()
      .then((view) => {
        if (active) setCoverageState({ kind: 'loaded', view });
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'Tenant platform diagnostics are unavailable.';
        if (active) setCoverageState({ kind: 'unreachable', message });
      });
    return () => {
      active = false;
    };
  }, []);

  if (coverageState.kind === 'loading') {
    return <section className="panel" aria-labelledby="tenant-platform-heading"><h2 id="tenant-platform-heading">Tenant platform coverage</h2><p role="status">Loading tenant platform diagnostics...</p></section>;
  }

  if (coverageState.kind === 'unreachable') {
    return (
      <section className="panel" aria-labelledby="tenant-platform-heading">
        <h2 id="tenant-platform-heading">Tenant platform coverage</h2>
        <div className="banner banner--blocked" role="alert">{coverageState.message}</div>
      </section>
    );
  }

  const view = coverageState.view;
  return (
    <>
      <section className="hero" aria-labelledby="tenant-platform-title">
        <p className="eyebrow">Platform · PII-22-S08</p>
        <h2 id="tenant-platform-title">Tenant platform coverage</h2>
        <p>
          Review tenant context propagation, cache scoping, rate limiting, audit, event envelope, replay, and readiness
          evidence from configured services. The workbench shows references and blockers only; it does not expose secrets.
        </p>
      </section>

      <section className="panel" aria-labelledby="tenant-platform-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Tenant context diagnostics</p>
            <h2 id="tenant-platform-heading">Propagation and readiness evidence</h2>
          </div>
          <DiagnosticsDetails items={[`Workspace ${view.tenantContext}`, `Support reference: ${view.uiTraceId}`]} />
        </div>
        <div className="banner banner--blocked" role="alert">
          <strong>Configured diagnostics needed</strong>
          <span>{businessFacingText(view.fallbackReason)}</span>
          <span>Setup status: {serviceReadinessText(view.dependencyStatus)}</span>
        </div>
        <dl className="status-grid">
          <dt>Tenant id</dt><dd>{businessFacingText(view.trace.tenantIdRef)}</dd>
          <dt>Correlation id</dt><dd>{businessFacingText(view.trace.correlationIdRef)}</dd>
          <dt>Idempotency key</dt><dd>{businessFacingText(view.trace.idempotencyKeyRef)}</dd>
          <dt>Event envelope</dt><dd>{businessFacingText(view.trace.eventEnvelopeRef)}</dd>
          <dt>Audit reference</dt><dd>{businessFacingText(view.trace.auditRef)}</dd>
          <dt>Replay hash</dt><dd>{businessFacingText(view.trace.replayHashRef)}</dd>
        </dl>
      </section>

      <section className="panel" aria-labelledby="tenant-controls-heading">
        <h2 id="tenant-controls-heading">Platform control coverage</h2>
        <div className="module-rail__grid" role="list" aria-label="Tenant platform controls">
          {view.controls.map((control) => <TenantPlatformControlCard key={control.controlId} control={control} />)}
        </div>
      </section>

      <section className="panel" aria-labelledby="tenant-blockers-heading">
        <h2 id="tenant-blockers-heading">Blocked live integration gaps</h2>
        <ul className="offer-list" aria-label="Tenant platform blockers">
          {view.blockers.map((blocker) => (
            <li key={blocker.code} role="alert">
              <h3>{businessFacingText(blocker.code)}</h3>
              <p>{businessFacingText(blocker.message)}</p>
              <p>Owner: {businessFacingText(blocker.owner)}</p>
            </li>
          ))}
        </ul>
        <ChipList label="Tenant platform events" values={view.events.map(businessFacingText)} />
      </section>
    </>
  );
}

function TenantPlatformControlCard({ control }: { control: TenantPlatformControl }) {
  const blocked = control.status.toLowerCase().includes('blocked');
  return (
    <article className={blocked ? 'module-card module-card--active' : 'module-card'} role="listitem">
      <p className="module-card__route">{businessFacingText(control.status)}</p>
      <strong className="module-card__title">{control.label}</strong>
      <p>{businessFacingText(control.guidance)}</p>
      <ChipList label={`${control.label} evidence refs`} values={control.evidenceRefs.map(businessFacingText)} />
      <ChipList label={`${control.label} blockers`} values={control.blockers.map(businessFacingText)} />
    </article>
  );
}

function RuleEvidenceTable({ label, rows }: { label: string; rows: RuleEvidenceRow[] }) {
  return (
    <div className="quote-table" role="table" aria-label={label}>
      <div role="row" className="quote-table__row quote-table__row--head">
        <span role="columnheader">Rule</span>
        <span role="columnheader">Version</span>
        <span role="columnheader">Outcome</span>
        <span role="columnheader">Reason</span>
        <span role="columnheader">Facts</span>
      </div>
      {rows.map((row) => (
        <div key={`${row.ruleRef}-${row.versionRef}-${row.reasonCode}`} role="row" className="quote-table__row">
          <span role="cell">{businessFacingText(row.ruleRef)}</span>
          <span role="cell">{businessFacingText(row.versionRef)}</span>
          <span role="cell">{businessFacingText(row.outcome)}</span>
          <span role="cell">{businessFacingText(row.reasonCode)}</span>
          <span role="cell"><ChipList label={`${row.ruleRef} fact references`} values={row.factRefs.map(businessFacingText)} /></span>
        </div>
      ))}
    </div>
  );
}

function businessFacingText(value: string | number | null | undefined) {
  if (value === null || value === undefined || value === '') return 'Not provided';
  return String(value)
    .replace(/BFF/gi, 'workbench service')
    .replace(/Configured upstream/gi, 'Configured service')
    .replace(/upstream/gi, 'configured service')
    .replace(/downstream/gi, 'connected workflow')
    .replace(/SLA contract required/gi, 'Response target needs setup')
    .replace(/Awaiting configured SLA contract/gi, 'Response target needs setup')
    .replace(/SLA deadline supplied by configured privacy service/gi, 'Response target supplied by configured privacy service')
    .replace(/DLQ/gi, 'exception queue')
    .replace(/DSAR/gi, 'privacy request')
    .replace(/RBAC/gi, 'role access')
    .replace(/drift/gi, 'change')
    .replace(/override ledger/gi, 'change history')
    .replace(/policy[- ]?version/gi, 'guidance version')
    .replace(/policy digest/gi, 'guidance summary')
    .replace(/market-rule/gi, 'market guidance')
    .replace(/release gates?/gi, 'readiness checks')
    .replace(/dependency status/gi, 'setup status')
    .replace(/route/gi, 'path')
    .replace(/contract/gi, 'setup')
    .replace(/_/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
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
        const message = error instanceof Error ? error.message : 'Admin release controls are unavailable.';
        if (active) setAdminState({ kind: 'unreachable', message });
      });
    return () => {
      active = false;
    };
  }, []);

  if (adminState.kind === 'loading') {
    return <section className="panel" aria-labelledby="admin-heading"><h2 id="admin-heading">Admin governance and readiness checks</h2><p role="status">Loading admin governance...</p></section>;
  }

  if (adminState.kind === 'unreachable') {
    return (
      <section className="panel" aria-labelledby="admin-heading">
        <h2 id="admin-heading">Admin governance and readiness checks</h2>
        <div className="banner banner--blocked" role="alert">{adminState.message}</div>
      </section>
    );
  }

  const view = adminState.view;
  return (
    <>
      <section className="hero hero--admin" aria-labelledby="admin-title">
        <p className="eyebrow">Admin · AG-S01 / AG-S07</p>
        <h2 id="admin-title">Admin governance and readiness controls</h2>
        <p>
          Review guidance updates, flag conflicts, market guidance completeness, change approvals, release readiness,
          incidents, and audit evidence in one guided board. Release actions stay disabled while configured governance
          setup and open decisions are unresolved.
        </p>
      </section>

      <section className="panel" aria-labelledby="admin-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Admin readiness</p>
            <h2 id="admin-heading">Release candidate readiness</h2>
          </div>
          <DiagnosticsDetails items={[`Workspace ${view.tenantContext}`]} />
        </div>
        <div className="banner banner--blocked" role="alert">
          <strong>Release status {view.releaseCandidate.readinessStatus}</strong>
          <span>{view.fallbackReason}</span>
          <span>Setup status: {serviceReadinessText(view.dependencyStatus)}</span>
        </div>
        <div className="quote-table" role="table" aria-label="Governance descriptors">
          <div role="row" className="quote-table__row quote-table__row--head">
            <span role="columnheader">Descriptor</span>
            <span role="columnheader">Type</span>
            <span role="columnheader">Allowed operators</span>
            <span role="columnheader">Value sources</span>
            <span role="columnheader">Version</span>
          </div>
          {view.descriptors.map((descriptor) => (
            <div key={descriptor.stableId} role="row" className="quote-table__row">
              <span role="cell"><strong>{descriptor.stableId}</strong><br />{descriptor.label}</span>
              <span role="cell">{businessFacingText(descriptor.type)}</span>
              <span role="cell"><ChipList label={`${descriptor.stableId} allowed operators`} values={descriptor.allowedOperators.map(businessFacingText)} /></span>
              <span role="cell"><ChipList label={`${descriptor.stableId} value sources`} values={descriptor.valueSources.map(businessFacingText)} /></span>
              <span role="cell">{businessFacingText(descriptor.versionRef)}<br />{businessFacingText(descriptor.decisionQualityRequirement)}<ChipList label={`${descriptor.stableId} validation messages`} values={descriptor.validationMessages.map(businessFacingText)} /></span>
            </div>
          ))}
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
          <ChipList label="Release blockers" values={view.releaseCandidate.blockers.map(businessFacingText)} />
        </div>
          <ChipList label="Affected subsystems" values={view.releaseCandidate.affectedSubsystems.map(businessFacingText)} />
        <div className="quote-table" role="table" aria-label="Readiness checks">
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

      <section className="panel" aria-labelledby="config-review-heading">
        <h2 id="config-review-heading">Pending config review impact</h2>
        <div className="banner banner--blocked" role="alert">
          <strong>{view.pendingReview.reviewId} · {businessFacingText(view.pendingReview.state)}</strong>
          <span>Simulation, approval, publish, rollback, audit, and consumer impact stay visible from backend-owned refs.</span>
          <span>Audit record: {businessFacingText(view.pendingReview.auditRef)}</span>
        </div>
        <dl className="status-grid">
          <dt>Simulation</dt><dd>{view.pendingReview.simulationVisible ? 'Visible' : 'Blocked'}</dd>
          <dt>Approval</dt><dd>{view.pendingReview.approvalVisible ? 'Visible' : 'Blocked'}</dd>
          <dt>Publish</dt><dd>{view.pendingReview.publishVisible ? 'Visible' : 'Blocked'}</dd>
          <dt>Rollback</dt><dd>{view.pendingReview.rollbackVisible ? 'Visible' : 'Blocked'}</dd>
        </dl>
        <ChipList label="Downstream consumer impact" values={view.pendingReview.downstreamConsumers.map(businessFacingText)} />
        <ChipList label="Pending config review blockers" values={view.pendingReview.blockers.map(businessFacingText)} />
      </section>

      <section className="panel" aria-labelledby="dynamic-rule-evidence-heading">
        <h2 id="dynamic-rule-evidence-heading">Dynamic rule evidence</h2>
        <dl className="status-grid">
          <dt>Precision metadata</dt><dd>{businessFacingText(view.dynamicRuleEvidence.precisionMetadataRef)}</dd>
          <dt>Replay hash</dt><dd>{businessFacingText(view.dynamicRuleEvidence.replayHashRef)}</dd>
        </dl>
        <ChipList label="Action outputs" values={view.dynamicRuleEvidence.actionOutputs.map(businessFacingText)} />
        <ChipList label="Fact references" values={view.dynamicRuleEvidence.factRefs.map(businessFacingText)} />
        <RuleEvidenceTable label="Governance matched rules" rows={view.dynamicRuleEvidence.matchedRules} />
        <RuleEvidenceTable label="Governance skipped rules" rows={view.dynamicRuleEvidence.skippedRules} />
      </section>

      <section className="panel" aria-labelledby="open-decisions-heading">
        <h2 id="open-decisions-heading">Open decision blockers</h2>
        <ul className="offer-list" aria-label="Open decisions blocking release">
          {view.openDecisions.map((decision) => (
            <li key={decision.decisionId} role="alert">
              <h3>{decision.decisionId} · {decision.status}</h3>
              <p>{businessFacingText(decision.title)}</p>
              <p>Resolution record: {businessFacingText(decision.resolutionRef)}</p>
            </li>
          ))}
        </ul>
      </section>

      <section className="panel" aria-labelledby="policy-heading">
        <h2 id="policy-heading">Guidance registry, flag conflicts, and market readiness</h2>
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
              <span role="cell">previous {policy.parentVersionId}</span>
            </div>
          ))}
        </div>
        {view.policies.map((policy) => <ChipList key={`${policy.versionId}-impacts`} label={`${policy.versionId} change impacts`} values={policy.diffImpacts.map(businessFacingText)} />)}
        {view.featureFlags.map((flag) => (
          <div key={flag.flagId} className="banner banner--blocked" role="alert">
            <strong>Feature flag activation blocked</strong>
            <span>{flag.flagId} · {flag.environmentTarget}</span>
            <span>{businessFacingText(flag.emergencyToggleGate)}</span>
            <ChipList label="Unresolved flags" values={flag.unresolvedFlags.map(businessFacingText)} />
          </div>
        ))}
        {view.marketRules.map((rule) => (
          <div key={rule.ruleId} className="banner banner--blocked" role="alert">
            <strong>Market guidance promotion blocked</strong>
            <span>{rule.ruleId} · {rule.stagingStatus}</span>
            <span>{businessFacingText(rule.completenessGate)}</span>
            <ChipList label="Missing market guidance fields" values={rule.missingRequiredFields.map(businessFacingText)} />
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
            <ChipList label="Required state sequence" values={request.requiredStateSequence.map(businessFacingText)} />
            <ChipList label="Change request blockers" values={request.blockers.map(businessFacingText)} />
          </article>
        ))}
      </section>

      <section className="panel" aria-labelledby="drift-incident-heading">
        <h2 id="drift-incident-heading">Change alerts, incidents, rollback, and audit history</h2>
        {view.driftAlerts.map((alert) => (
          <div key={alert.alertId} className="banner banner--blocked" role="alert">
            <strong>{alert.alertId} · {alert.severity}</strong>
            <span>{businessFacingText(alert.summary)}</span>
            <span>Owner: {alert.owner} · Environment: {alert.environment}</span>
          </div>
        ))}
        <ul className="offer-list" aria-label="Admin incidents">
          {view.incidents.map((incident) => <AdminIncidentCard key={incident.incidentId} incident={incident} />)}
        </ul>
        <div className="quote-table" role="table" aria-label="Change audit history">
          <div role="row" className="quote-table__row quote-table__row--head">
            <span role="columnheader">Record</span>
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
      <p>{businessFacingText(incident.closureGate)}</p>
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
        const message = error instanceof Error ? error.message : 'Quality dashboard is unavailable.';
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
          Review validation status, release readiness, fairness, incidents, replay readiness, and evidence checks.
          The UI reports configured evidence states and does not invent thresholds.
        </p>
      </section>

      <section className="panel" aria-labelledby="quality-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Quality validation</p>
            <h2 id="quality-heading">Validation results console</h2>
          </div>
          <DiagnosticsDetails items={[`Workspace ${view.tenantContext}`]} />
        </div>
        <div className="banner banner--blocked" role="alert">
          <strong>Loop status {view.validationRun.loopStatus}</strong>
          <span>{view.validationRun.nextAction}</span>
          <span>Setup status: {serviceReadinessText(view.dependencyStatus)}</span>
        </div>
        <div className="quote-table quality-stage-table" role="table" aria-label="Validation stages">
          <div role="row" className="quote-table__row quote-table__row--head">
            <span role="columnheader">Stage</span>
            <span role="columnheader">Status</span>
            <span role="columnheader">Timestamp</span>
            <span role="columnheader">Reference</span>
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
        <ChipList label="Evidence records" values={view.validationRun.evidencePaths.map(businessFacingText)} />
      </section>

      <section className="panel" aria-labelledby="readiness-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Readiness</p>
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
        <ChipList label="Setup checks" values={view.readiness.dependencyChecks.map(businessFacingText)} />
        <p>Evidence set completeness: {businessFacingText(view.readiness.evidenceSetCompleteness)}</p>
      </section>

      <section className="panel" aria-labelledby="drift-fairness-heading">
        <h2 id="drift-fairness-heading">Change and fairness monitoring</h2>
        <div className="banner banner--blocked" role="alert">
          <strong>Change review lockout</strong>
          <span>{businessFacingText(view.drift.lockoutReason)}</span>
          <span>Freshness: {businessFacingText(view.drift.cacheStaleness)}</span>
        </div>
        <dl className="status-grid">
          <dt>Metric family</dt><dd>{view.drift.metricFamily}</dd>
          <dt>Window</dt><dd>{view.drift.window}</dd>
          <dt>Baseline</dt><dd>{businessFacingText(view.drift.windowBaseline)}</dd>
          <dt>Sample counts</dt><dd>{view.fairness.sampleCountsLabel}</dd>
          <dt>Fairness redaction</dt><dd>{view.fairness.redacted ? 'Protected class labels masked' : 'Role-authorized dimensions visible'}</dd>
        </dl>
        <ChipList label="Affected products" values={view.drift.affectedProducts.map(businessFacingText)} />
        <ChipList label="Protected class dimensions" values={view.fairness.protectedClassDimensions} />
        <ChipList label="Fairness evidence records" values={view.fairness.evidenceRefs.map(businessFacingText)} />
        <ul className="offer-list" aria-label="Change metrics">
          {view.drift.metrics.map((metric) => (
            <li key={metric.metricName}>
              <h3>{businessFacingText(metric.metricName)} · {metric.severity}</h3>
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
        const message = error instanceof Error ? error.message : 'Compliance evidence is unavailable.';
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
          Inspect evidence references, decision rationale, privacy requests, security events, alert routing, and
          retention controls. Legal and retention values remain configuration-owned.
        </p>
      </section>

      <section className="panel" aria-labelledby="compliance-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Compliance evidence</p>
            <h2 id="compliance-heading">Evidence catalog</h2>
          </div>
          <DiagnosticsDetails items={[`Workspace ${view.tenantContext}`]} />
        </div>
        <div className="banner banner--blocked" role="alert">
          <strong>Configured evidence unavailable</strong>
          <span>{businessFacingText(view.fallbackReason)}</span>
          <span>Setup status: {serviceReadinessText(view.dependencyStatus)}</span>
        </div>
        <div className="quote-table" role="table" aria-label="Compliance evidence artifacts">
          <div role="row" className="quote-table__row quote-table__row--head">
            <span role="columnheader">Artifact</span>
            <span role="columnheader">Guidance reference</span>
            <span role="columnheader">Continuity</span>
            <span role="columnheader">Retention</span>
            <span role="columnheader">Reference</span>
          </div>
          {view.artifacts.map((artifact) => (
            <div key={artifact.artifactId} role="row" className="quote-table__row">
              <span role="cell">{artifact.artifactId} ({artifact.artifactType})</span>
              <span role="cell">{businessFacingText(artifact.policyVersion)} / {artifact.jurisdictionCode}</span>
              <span role="cell">{businessFacingText(artifact.continuityStatus)}</span>
              <span role="cell">{businessFacingText(artifact.retentionClass)}</span>
              <span role="cell">Available to support</span>
            </div>
          ))}
        </div>
        {view.artifacts.map((artifact) => (
          <div key={`${artifact.artifactId}-blockers`} className="banner banner--blocked" role="alert">
            <strong>{artifact.artifactId} progression blocked</strong>
            <ChipList label="Workflow links" values={artifact.moduleLinks.map(businessFacingText)} />
            <ChipList label="Evidence blockers" values={artifact.blockers.map(businessFacingText)} />
          </div>
        ))}
      </section>

      <section className="panel" aria-labelledby="decision-heading">
        <h2 id="decision-heading">Decision rationale and privacy gates</h2>
        <div className="partner-detail-grid">
          {view.decisions.map((decision) => (
            <article key={decision.decisionId} className="status-card status-card--offline">
              <h3>{businessFacingText(decision.reasonCode)}</h3>
              <p>{decision.humanText}</p>
              <p>Jurisdiction: {decision.jurisdictionCode}</p>
              <p>Disclosure record: {businessFacingText(decision.disclosureArtifactRef)}</p>
              <ChipList label="Reason categories" values={decision.reasonTiers.map(businessFacingText)} />
            </article>
          ))}
          {view.privacyRequests.map((request) => (
            <article key={request.requestId} className="status-card status-card--offline">
              <h3>Privacy request {request.requestId}</h3>
              <p>Borrower: {request.borrowerRef}</p>
              <p>Identity: {businessFacingText(request.identityStatus)}</p>
              <p>Response target: {businessFacingText(request.slaState)}</p>
              <p>Consent history: {businessFacingText(request.consentAuditRef)}</p>
              <ChipList label="Privacy blockers" values={request.blockers.map(businessFacingText)} />
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
              <p>Security record: {businessFacingText(event.logRecordId)}</p>
              <p>Owner: {event.owner}</p>
              <p>Acknowledged: {event.acknowledged ? 'yes' : 'no'}</p>
              <ChipList label="Security blockers" values={event.blockers.map(businessFacingText)} />
            </article>
          ))}
          {view.alerts.map((alert) => (
            <article key={alert.alertId} className="status-card status-card--offline">
              <h3>{alert.alertId}</h3>
              <p>{alert.severity} · {businessFacingText(alert.alertClass)} · {businessFacingText(alert.triggerType)}</p>
              <p>Alert destination: {businessFacingText(alert.routeTarget)}</p>
              <ChipList label="Alert blockers" values={alert.blockers.map(businessFacingText)} />
            </article>
          ))}
          {view.retentionControls.map((control) => (
            <article key={control.ruleId} className="status-card status-card--offline" role="alert">
              <h3>{control.ruleId}</h3>
              <p>{businessFacingText(control.retentionClass)}</p>
              <p>{businessFacingText(control.retentionWindow)}</p>
              <p>{businessFacingText(control.deletionGateReason)}</p>
              <p>{businessFacingText(control.backupEvidence)}</p>
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
        const message = error instanceof Error ? error.message : 'Operations cases are unavailable.';
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
        const message = error instanceof Error ? error.message : 'Operations case detail is unavailable.';
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
        <p>Review case context, preserve blocker detail, and gate intervention actions without changing pricing state.</p>
      </section>

      <section className="panel" aria-labelledby="ops-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Operations queue</p>
            <h2 id="ops-heading">Case queue</h2>
          </div>
          <DiagnosticsDetails items={[`Workspace ${caseState.view.tenantContext}`]} />
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
              <dt>Response target</dt><dd>{businessFacingText(detailState.detail.slaState)}</dd>
              <dt>Age</dt><dd>{businessFacingText(detailState.detail.ageLabel)}</dd>
            </dl>
            <ul className="offer-list" aria-label="Case timeline">
              {detailState.detail.timeline.map((event) => (
                <li key={event.eventId}>
                  <h3>{event.eventType}</h3>
                  <p>{event.summary}</p>
                </li>
              ))}
            </ul>
            <ChipList label="Evidence records" values={detailState.detail.evidencePacketIds.map(businessFacingText)} />
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

function RateFeedOperationsSection() {
  const [rateFeedState, setRateFeedState] = useState<RateFeedOpsState>({ kind: 'loading' });

  useEffect(() => {
    let active = true;
    fetchRateFeedOperations()
      .then((view) => {
        if (active) setRateFeedState({ kind: 'loaded', view });
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'Rate feed operations are unavailable.';
        if (active) setRateFeedState({ kind: 'unreachable', message });
      });
    return () => {
      active = false;
    };
  }, []);

  if (rateFeedState.kind === 'loading') {
    return <section className="panel" aria-labelledby="rate-feed-heading"><h2 id="rate-feed-heading">Rate feed operations</h2><p role="status">Loading rate feed operations...</p></section>;
  }

  if (rateFeedState.kind === 'unreachable') {
    return (
      <section className="panel" aria-labelledby="rate-feed-heading">
        <h2 id="rate-feed-heading">Rate feed operations</h2>
        <div className="banner banner--blocked" role="alert">{rateFeedState.message}</div>
      </section>
    );
  }

  const view = rateFeedState.view;
  return (
    <>
      <section className="hero hero--rate-feed" aria-labelledby="rate-feed-title">
        <p className="eyebrow">Rate operations · PII-22-S03</p>
        <h2 id="rate-feed-title">Rate feed operations</h2>
        <p>
          Track upload, parse, validation, activation, rejection, replay, and cache evidence from backend-owned rate feed
          facts. The workbench shows row blockers and source references without recalculating rates.
        </p>
      </section>

      <section className="panel" aria-labelledby="rate-feed-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Workflow cockpit</p>
            <h2 id="rate-feed-heading">Upload-to-replay workflow</h2>
          </div>
          <DiagnosticsDetails items={[`Workspace ${view.tenantContext}`, `Support reference: ${view.uiTraceId}`]} />
        </div>
        <div className="banner banner--blocked" role="alert">
          <strong>Live rate feed actions blocked</strong>
          <span>{businessFacingText(view.fallbackReason)}</span>
          <span>Setup status: {serviceReadinessText(view.dependencyStatus)}</span>
        </div>
        <div className="module-rail__grid" role="list" aria-label="Rate feed workflow steps">
          {view.workflowSteps.map((step) => (
            <article key={step.stepId} className="module-card module-card--light" role="listitem">
              <p className="module-card__route">{businessFacingText(step.status)}</p>
              <strong className="module-card__title">{step.label}</strong>
              <p>{businessFacingText(step.sourceBoundary)}</p>
              <dl>
                <dt>Audit reference</dt><dd>{businessFacingText(step.auditRef)}</dd>
                <dt>Result hash</dt><dd>{businessFacingText(step.resultHashRef)}</dd>
              </dl>
            </article>
          ))}
        </div>
      </section>

      <section className="panel" aria-labelledby="rate-grid-heading">
        <h2 id="rate-grid-heading">Grid blockers and source references</h2>
        <div className="quote-table" role="table" aria-label="Rate grid blockers">
          <div role="row" className="quote-table__row quote-table__row--head">
            <span role="columnheader">Source row</span>
            <span role="columnheader">Field</span>
            <span role="columnheader">Severity</span>
            <span role="columnheader">Blocker</span>
            <span role="columnheader">Source reference</span>
          </div>
          {view.rowBlockers.map((blocker) => <RateFeedBlockerRow key={`${blocker.rowRef}-${blocker.fieldName}-${blocker.blockerCode}`} blocker={blocker} />)}
        </div>
        <ChipList label="Backend source references" values={view.sourceReferences.map(businessFacingText)} />
      </section>

      <section className="panel" aria-labelledby="rate-evidence-heading">
        <h2 id="rate-evidence-heading">Activation, cache invalidation, and replay evidence</h2>
        <button type="button" disabled={view.actionsDisabled} aria-describedby="rate-feed-action-blockers">Publish rate sheet</button>
        <button type="button" className="button-secondary" disabled={view.actionsDisabled}>Request replay</button>
        <div id="rate-feed-action-blockers">
          <ChipList label="Replay and cache evidence" values={view.replayEvidence.map(businessFacingText)} />
          <ChipList label="Rate feed events" values={view.events.map(businessFacingText)} />
        </div>
      </section>
    </>
  );
}

function PerformanceDashboardSection() {
  const [performanceState, setPerformanceState] = useState<PerformanceDashboardState>({ kind: 'loading' });

  useEffect(() => {
    let active = true;
    fetchPerformanceDashboard()
      .then((view) => {
        if (active) setPerformanceState({ kind: 'loaded', view });
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'Performance dashboard is unavailable.';
        if (active) setPerformanceState({ kind: 'unreachable', message });
      });
    return () => {
      active = false;
    };
  }, []);

  if (performanceState.kind === 'loading') {
    return <section className="panel" aria-labelledby="performance-heading"><h2 id="performance-heading">Service performance dashboard</h2><p role="status">Loading performance dashboard...</p></section>;
  }

  if (performanceState.kind === 'unreachable') {
    return (
      <section className="panel" aria-labelledby="performance-heading">
        <h2 id="performance-heading">Service performance dashboard</h2>
        <div className="banner banner--blocked" role="alert">{performanceState.message}</div>
      </section>
    );
  }

  const view = performanceState.view;
  return (
    <>
      <section className="hero hero--performance" aria-labelledby="performance-title">
        <p className="eyebrow">Observability · PII-22-S09</p>
        <h2 id="performance-title">Service performance cockpit</h2>
        <p>
          Group performance, cache, backpressure, and load-test evidence by service, workspace, correlation id, and
          freshness. The dashboard shows configured service refs and blockers only; it does not change runtime settings.
        </p>
      </section>

      <section className="panel" aria-labelledby="performance-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Service grouped signals</p>
            <h2 id="performance-heading">Performance, cache, and freshness signals</h2>
          </div>
          <DiagnosticsDetails items={[`Workspace ${view.tenantContext}`, `Support reference: ${view.uiTraceId}`]} />
        </div>
        <div className="banner banner--blocked" role="alert">
          <strong>Configured observability setup needed</strong>
          <span>{businessFacingText(view.fallbackReason)}</span>
          <span>Setup status: {serviceReadinessText(view.dependencyStatus)}</span>
        </div>
        <div className="module-rail__grid" role="list" aria-label="Performance signal groups">
          {view.signalGroups.map((group) => <PerformanceSignalGroupCard key={`${group.serviceName}-${group.correlationId}`} group={group} />)}
        </div>
      </section>

      <section className="panel" aria-labelledby="performance-impact-heading">
        <h2 id="performance-impact-heading">Impact, source, and recovery ownership</h2>
        <div className="quote-table" role="table" aria-label="Performance impacts">
          <div role="row" className="quote-table__row quote-table__row--head">
            <span role="columnheader">Impact</span>
            <span role="columnheader">Source</span>
            <span role="columnheader">Recovery owner</span>
            <span role="columnheader">Runbook</span>
            <span role="columnheader">Summary</span>
          </div>
          {view.impacts.map((impact) => (
            <div key={impact.impactCode} role="row" className="quote-table__row">
              <span role="cell">{businessFacingText(impact.impactCode)}</span>
              <span role="cell">{businessFacingText(impact.source)}</span>
              <span role="cell">{businessFacingText(impact.recoveryOwner)}</span>
              <span role="cell">{businessFacingText(impact.runbookRef)}</span>
              <span role="cell">{businessFacingText(impact.summary)}</span>
            </div>
          ))}
        </div>
      </section>

      <section className="panel" aria-labelledby="performance-evidence-heading">
        <h2 id="performance-evidence-heading">Evidence links and unavailable evidence</h2>
        <button type="button" disabled={view.actionsDisabled} aria-describedby="performance-action-blockers">Change runtime configuration</button>
        <div id="performance-action-blockers">
          <ChipList label="Project-relative evidence links" values={view.evidenceLinks.map(businessFacingText)} />
          <PerformanceBlockerList blockers={view.blockers} />
          <ChipList label="Performance dashboard events" values={view.events.map(businessFacingText)} />
        </div>
      </section>
    </>
  );
}

function PerformanceSignalGroupCard({ group }: { group: PerformanceSignalGroup }) {
  return (
    <article className="module-card module-card--light" role="listitem">
      <p className="module-card__route">{businessFacingText(group.freshness)}</p>
      <strong className="module-card__title">{businessFacingText(group.serviceName)}</strong>
      <dl>
        <dt>Workspace</dt><dd>{businessFacingText(group.tenantContext)}</dd>
        <dt>Correlation id</dt><dd>{businessFacingText(group.correlationId)}</dd>
      </dl>
      {group.signals.map((signal) => (
        <div key={signal.signalId} className="signal-stack">
          <strong>{businessFacingText(signal.label)}</strong>
          <span>{businessFacingText(signal.source)}</span>
          <span>Source ref: {businessFacingText(signal.sourceRef)}</span>
          <ChipList label={`${signal.signalId} evidence`} values={signal.evidenceRefs.map(businessFacingText)} />
        </div>
      ))}
      <PerformanceBlockerList blockers={group.blockers} />
    </article>
  );
}

function PerformanceBlockerList({ blockers }: { blockers: PerformanceBlocker[] }) {
  if (!blockers.length) return null;
  return (
    <ul className="offer-list" aria-label="Performance blockers">
      {blockers.map((blocker) => (
        <li key={`${blocker.code}-${blocker.owner}`} role="alert">
          <h3>{businessFacingText(blocker.code)}</h3>
          <p>{businessFacingText(blocker.message)}</p>
          <p>Owner: {businessFacingText(blocker.owner)}</p>
        </li>
      ))}
    </ul>
  );
}

function RateFeedBlockerRow({ blocker }: { blocker: RateFeedGridBlocker }) {
  return (
    <div role="row" className="quote-table__row">
      <span role="cell">{businessFacingText(blocker.rowRef)}</span>
      <span role="cell">{businessFacingText(blocker.fieldName)}</span>
      <span role="cell">{businessFacingText(blocker.severity)}</span>
      <span role="cell"><strong>{businessFacingText(blocker.blockerCode)}</strong><br /><span className="field-help">{businessFacingText(blocker.resolutionState)}</span></span>
      <span role="cell">{businessFacingText(blocker.sourceReference)}</span>
    </div>
  );
}

function OpsCaseQueue({ cases, selectedCaseId, onSelect }: { cases: OpsCaseSummary[]; selectedCaseId: string | null; onSelect: (caseId: string) => void }) {
  if (!cases.length) return <p role="status">No operations cases are loaded.</p>;
  return (
    <div className="quote-table" role="table" aria-label="Operations cases">
      <div role="row" className="quote-table__row quote-table__row--head">
        <span role="columnheader">Case</span>
        <span role="columnheader">Priority</span>
        <span role="columnheader">Response target</span>
        <span role="columnheader">Owner</span>
        <span role="columnheader">Action</span>
      </div>
      {cases.map((opsCase) => (
        <div key={opsCase.caseId} role="row" className={selectedCaseId === opsCase.caseId ? 'quote-table__row quote-table__row--selected' : 'quote-table__row'}>
          <span role="cell">{opsCase.contextSummary} ({opsCase.caseId})</span>
          <span role="cell">{opsCase.priority}</span>
          <span role="cell">{businessFacingText(opsCase.slaState)}</span>
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
      {'downstreamExecuted' in result ? <span>Connected workflow run: {result.downstreamExecuted ? 'yes' : 'no'}</span> : null}
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
        const message = error instanceof Error ? error.message : 'Partner quote list is unavailable.';
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
        const message = error instanceof Error ? error.message : 'Partner quote detail is unavailable.';
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
          Review partner-shared quote state, filter by lifecycle status, and keep all detail views inside the selected workspace.
        </p>
      </section>

      <section className="panel" aria-labelledby="partner-quotes-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Partner quotes</p>
            <h2 id="partner-quotes-heading">Quote dashboard</h2>
          </div>
          {quoteState.kind === 'loaded' ? <DiagnosticsDetails items={[`Workspace ${quoteState.view.tenantContext}`]} /> : null}
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
        <span role="columnheader">Response target</span>
        <span role="columnheader">Lock</span>
        <span role="columnheader">Action</span>
      </div>
      {quotes.map((quote) => (
        <div key={quote.quoteId} role="row" className={selectedQuoteId === quote.quoteId ? 'quote-table__row quote-table__row--selected' : 'quote-table__row'}>
          <span role="cell">{quote.borrowerLabel} ({quote.quoteId})</span>
          <span role="cell">{quote.status}</span>
          <span role="cell">{businessFacingText(quote.slaState)}</span>
          <span role="cell">{businessFacingText(quote.lockState)}</span>
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
        <dt>Workspace</dt><dd>{detail.tenantContext}</dd>
        <dt>Quote status</dt><dd>{detail.status}</dd>
        <dt>Response target</dt><dd>{businessFacingText(detail.slaState)}</dd>
        <dt>Lock state</dt><dd>{businessFacingText(detail.lockState)}</dd>
      </dl>
      <ChipList label="Lifecycle events" values={detail.lifecycleEvents.map(businessFacingText)} />
      <ChipList label="Attention flags" values={detail.errorFlags.map(businessFacingText)} />
      {action.visible ? (
        <button type="button" onClick={onAttemptReprice}>Request reprice</button>
      ) : (
        <button type="button" className="button-secondary" onClick={onAttemptReprice}>Show reprice guidance</button>
      )}
      {!action.permitted ? (
        <div className="banner banner--blocked" role="alert">
          <strong>Reprice blocked</strong>
          <span>{businessFacingText(action.guidance)}</span>
          <DiagnosticsDetails items={[`Support path: ${action.supportHandoffRoute}`]} />
        </div>
      ) : null}
      {result ? (
        <div className={result.status === 'ACCEPTED' ? 'banner banner--success' : 'banner banner--blocked'} role={result.status === 'ACCEPTED' ? 'status' : 'alert'}>
          <strong>{result.status === 'ACCEPTED' ? 'Reprice request recorded' : 'Reprice request blocked'}</strong>
          <span>{result.message}</span>
          <span>{businessFacingText(result.guidance)}</span>
          <DiagnosticsDetails items={[`Support path: ${result.supportHandoffRoute}`]} />
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
        const message = error instanceof Error ? error.message : 'Partner connection controls are unavailable.';
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
    return <section className="panel" aria-labelledby="partner-transport-heading"><h2 id="partner-transport-heading">Partner connection reliability</h2><p role="status">Loading connection health...</p></section>;
  }

  if (transportState.kind === 'unreachable') {
    return (
      <section className="panel" aria-labelledby="partner-transport-heading">
        <h2 id="partner-transport-heading">Partner connection reliability</h2>
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
        <h2 id="partner-transport-title">Partner connection reliability</h2>
        <p>
          Inspect partner message delivery health, replay gating, and safety controls through the approved workbench service only.
        </p>
      </section>

      <section className="panel" aria-labelledby="partner-transport-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Partner connections</p>
            <h2 id="partner-transport-heading">Message delivery health</h2>
          </div>
          <DiagnosticsDetails items={[`Workspace ${view.tenantContext}`, `Support reference: ${view.uiTraceId}`]} />
        </div>

        <dl className="status-grid">
          <dt>Retry health</dt><dd>{businessFacingText(view.retryHealthSummary)}</dd>
          <dt>Events shown</dt><dd>{view.eventWindow}</dd>
          <dt>Exception queue</dt><dd>{businessFacingText(view.dlqSizeStatus)}</dd>
          <dt>Retry window</dt><dd>{businessFacingText(view.retryWindowStatus)}</dd>
        </dl>

        <div className="quote-table" role="table" aria-label="Webhook delivery attempts">
          <div role="row" className="quote-table__row quote-table__row--head">
            <span role="columnheader">Event</span>
            <span role="columnheader">Status</span>
            <span role="columnheader">Reason</span>
            <span role="columnheader">Last success</span>
            <span role="columnheader">Action</span>
          </div>
          {view.deliveryAttempts.map((attempt) => (
            <div key={attempt.eventId} role="row" className={selectedAttempt?.eventId === attempt.eventId ? 'quote-table__row quote-table__row--selected' : 'quote-table__row'}>
              <span role="cell">{attempt.eventId}</span>
              <span role="cell">{attempt.status}</span>
              <span role="cell">{businessFacingText(attempt.rootCauseCode)}</span>
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
              <dt>Selected message</dt><dd>{selectedAttempt.webhookId}</dd>
              <dt>Selected event</dt><dd>{selectedAttempt.eventId}</dd>
              <dt>Failure reason</dt><dd>{businessFacingText(selectedAttempt.failureReason)}</dd>
              <dt>Duplicate protection</dt><dd>{businessFacingText(selectedAttempt.idempotencyKeyState)}</dd>
              <dt>Data masking</dt><dd>{businessFacingText(selectedAttempt.maskingIndicator)}</dd>
              <dt>Consent</dt><dd>{businessFacingText(selectedAttempt.consentIndicator)}</dd>
            </dl>
            <div className="field-group">
              <label htmlFor="webhook-correlation">Replay correlation id</label>
              <input id="webhook-correlation" value={correlationId} onChange={(event) => setCorrelationId(event.target.value)} />
              <p className="field-help">{businessFacingText(view.replayAction.confirmationRequirement)}</p>
            </div>
            <label className="checkbox-row">
              <input type="checkbox" checked={idempotencyConfirmed} onChange={(event) => setIdempotencyConfirmed(event.target.checked)} />
              I confirm idempotency for this replay request.
            </label>
            <button type="button" disabled={replayDisabled} onClick={() => void requestReplay()}>Request replay</button>
            {replayDisabled ? <p role="status">{businessFacingText(view.replayAction.disabledReason)}</p> : null}
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
              <h3>Partner alert controls</h3>
              <p>{businessFacingText(toggle.visibleState)}</p>
              <DiagnosticsDetails items={[`Path: ${toggle.route}`]} />
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
      <span>{businessFacingText(result.guidance)}</span>
      <span>Connected workflow run: {result.downstreamExecuted ? 'yes' : 'no'}</span>
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
        const message = error instanceof Error ? error.message : 'Lock workflow is unavailable.';
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
        const message = error instanceof Error ? error.message : 'Lock confirmation is unavailable.';
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
        <p>Review lock preconditions and confirm only after an offer is selected in this workflow.</p>
      </section>

      <section className="panel" aria-labelledby="lock-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Lock workflow</p>
            <h2 id="lock-heading">Lock workflow</h2>
          </div>
          <DiagnosticsDetails items={[`Support reference: ${workflow.uiTraceId}`]} />
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
            <dt>Setup status</dt><dd>{serviceReadinessText(workflow.dependencyStatus)}</dd>
            <dt>Next action</dt><dd>{workflow.nextAction}</dd>
          </dl>
        </div>

        <section className="lock-cockpit" aria-labelledby="lock-cockpit-heading">
          <h3 id="lock-cockpit-heading">Lifecycle cockpit</h3>
          <div className="lock-cockpit__grid">
            <article>
              <h4>Selected quote refs</h4>
              <ChipList label="Selected quote references" values={(workflow.selectedQuoteRefs ?? []).map(businessFacingText)} />
            </article>
            <article>
              <h4>Required evidence</h4>
              <ChipList label="Lock required evidence" values={(workflow.requiredEvidence ?? []).map(businessFacingText)} />
            </article>
          </div>

          <div className="quote-table lock-table" role="table" aria-label="Freshness checks">
            <div role="row" className="quote-table__row quote-table__row--head">
              <span role="columnheader">Check</span>
              <span role="columnheader">Status</span>
              <span role="columnheader">Source</span>
              <span role="columnheader">Remediation</span>
            </div>
            {(workflow.freshnessChecks ?? []).map((check) => (
              <div key={`${check.label}-${check.sourceRef}`} role="row" className="quote-table__row">
                <span role="cell">{businessFacingText(check.label)}</span>
                <span role="cell">{businessFacingText(check.status)}</span>
                <span role="cell">{businessFacingText(check.sourceRef)}</span>
                <span role="cell">{businessFacingText(check.remediation)}</span>
              </div>
            ))}
          </div>

          <div className="quote-table lock-table" role="table" aria-label="Lock state machine transitions">
            <div role="row" className="quote-table__row quote-table__row--head">
              <span role="columnheader">From</span>
              <span role="columnheader">To</span>
              <span role="columnheader">Event id</span>
              <span role="columnheader">Status</span>
            </div>
            {(workflow.stateTransitions ?? []).map((transition) => (
              <div key={transition.eventId} role="row" className="quote-table__row">
                <span role="cell">{businessFacingText(transition.fromState)}</span>
                <span role="cell">{businessFacingText(transition.toState)}</span>
                <span role="cell">{transition.eventId}</span>
                <span role="cell">{businessFacingText(transition.status)}</span>
              </div>
            ))}
          </div>

          {(workflow.blockerDetails ?? []).length ? (
            <div className="quote-table lock-table" role="table" aria-label="Backend lock blockers">
              <div role="row" className="quote-table__row quote-table__row--head">
                <span role="columnheader">Code</span>
                <span role="columnheader">Message</span>
                <span role="columnheader">Remediation</span>
              </div>
              {(workflow.blockerDetails ?? []).map((blocker) => (
                <div key={blocker.code} role="row" className="quote-table__row">
                  <span role="cell">{blocker.code}</span>
                  <span role="cell">{businessFacingText(blocker.message)}</span>
                  <span role="cell">{businessFacingText(blocker.remediation)}</span>
                </div>
              ))}
            </div>
          ) : null}

          <LockAuditGroups groups={workflow.auditGroups ?? []} />
        </section>

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
        <LockAuditGroups groups={result.auditGroups ?? []} />
      </div>
    );
  }

  return (
    <div className="banner banner--blocked" role="alert" aria-live="assertive">
      <strong>{result.status === 'CONFLICT' ? 'Lock conflict' : 'Lock remains blocked'}</strong>
      <span>{result.message}</span>
      <ul>{result.blockers.map((blocker) => <li key={blocker}>{blocker}</li>)}</ul>
      <span>Selected offer context retained: {valueText(result.selectedOfferId)}</span>
      <LockAuditGroups groups={result.auditGroups ?? []} />
    </div>
  );
}

function LockAuditGroups({ groups }: { groups: NonNullable<LockWorkflowView['auditGroups']> }) {
  if (!groups.length) return null;
  return (
    <div className="lock-audit-groups" aria-label="Lock audit evidence grouped by backend event ids">
      {groups.map((group) => (
        <article key={group.eventId} className="lock-audit-groups__card">
          <h4>{businessFacingText(group.label)}</h4>
          <dl>
            <dt>Event id</dt><dd>{group.eventId}</dd>
            <dt>Replay hash</dt><dd>{businessFacingText(group.replayHash)}</dd>
            <dt>Export evidence</dt><dd>{businessFacingText(group.exportRef)}</dd>
          </dl>
          <ChipList label={`${group.eventId} evidence refs`} values={group.evidenceRefs.map(businessFacingText)} />
        </article>
      ))}
    </div>
  );
}

function LaunchBanner({ state, onRetry }: { state: LaunchState; onRetry: () => void }) {
  if (state.kind === 'idle') {
    return <p className="banner banner--info">Required fields are marked with an asterisk. Missing data keeps you on /quote/start.</p>;
  }

  if (state.kind === 'submitting') {
    return <p className="banner banner--info" role="status">Submitting borrower details...</p>;
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
        <span>{businessFacingText(state.message)} Retry when the workbench service is reachable.</span>
        <DiagnosticsDetails items={[`Support reference: ${uiTraceId}`]} />
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

type EligibilityState =
  | { kind: 'loading' }
  | { kind: 'loaded'; view: EligibilityModuleView }
  | { kind: 'unreachable'; message: string };

type PricingWaterfallState =
  | { kind: 'loading' }
  | { kind: 'loaded'; view: PricingWaterfallView }
  | { kind: 'unreachable'; message: string };

function PricingWaterfallSection({ runId }: { runId: string }) {
  const [waterfallState, setWaterfallState] = useState<PricingWaterfallState>({ kind: 'loading' });

  useEffect(() => {
    let active = true;
    fetchPricingWaterfall(tenantBoundaryPlaceholder, runId)
      .then((view) => {
        if (active) setWaterfallState({ kind: 'loaded', view });
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'Pricing waterfall evidence is unavailable.';
        if (active) setWaterfallState({ kind: 'unreachable', message });
      });
    return () => {
      active = false;
    };
  }, [runId]);

  if (waterfallState.kind === 'loading') {
    return <section className="panel" aria-labelledby="waterfall-heading"><h2 id="waterfall-heading">Pricing waterfall</h2><p role="status">Loading pricing waterfall evidence...</p></section>;
  }

  if (waterfallState.kind === 'unreachable') {
    return (
      <section className="panel" aria-labelledby="waterfall-heading">
        <h2 id="waterfall-heading">Pricing waterfall</h2>
        <div className="banner banner--blocked" role="alert">{waterfallState.message}</div>
      </section>
    );
  }

  const view = waterfallState.view;
  return (
    <>
      <section className="hero hero--admin" aria-labelledby="waterfall-title">
        <p className="eyebrow">Pricing · PII-22-S05</p>
        <h2 id="waterfall-title">Pricing waterfall for run {runId}</h2>
        <p>
          Inspect base grid selection, final price ledger steps, rounding trace references, missing-price blockers, audit ids,
          and replay hashes from backend-owned evidence. The workbench displays facts only and does not calculate prices.
        </p>
      </section>

      <section className="panel" aria-labelledby="waterfall-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Backend-owned waterfall</p>
            <h2 id="waterfall-heading">Waterfall evidence</h2>
          </div>
          <DiagnosticsDetails items={[`Support reference: ${view.uiTraceId}`, `Evidence hash: ${view.evidenceHash}`]} />
        </div>

        <div className="banner banner--blocked" role="alert">
          <strong>{businessFacingText(view.status)}</strong>
          <span>{businessFacingText(view.fallbackReason)}</span>
          <span>Restricted values visible: {view.restrictedValuesVisible ? 'yes' : 'no'}</span>
        </div>

        <dl className="status-grid">
          <dt>Base selection</dt><dd>{businessFacingText(view.baseSelection.selectionId)}</dd>
          <dt>Grid version</dt><dd>{businessFacingText(view.baseSelection.gridVersionRef)}</dd>
          <dt>Selected note rate</dt><dd>{waterfallValueText(view.baseSelection.selectedNoteRate)}</dd>
          <dt>Base price</dt><dd>{waterfallValueText(view.baseSelection.basePrice)}</dd>
          <dt>Final price</dt><dd>{businessFacingText(view.finalPrice.finalPriceId)}</dd>
          <dt>Rounded final price</dt><dd>{waterfallValueText(view.finalPrice.roundedFinalPrice)}</dd>
          <dt>Result hash</dt><dd>{businessFacingText(view.resultHash)}</dd>
          <dt>Replay hash</dt><dd>{businessFacingText(view.replayHash)}</dd>
        </dl>

        <ChipList label="Version refs" values={view.versionRefs.map(businessFacingText)} />
        <ChipList label="Audit refs" values={view.auditRefs.map(businessFacingText)} />
        <ChipList label="Rounding trace refs" values={view.finalPrice.roundingTraceRefs.map(businessFacingText)} />
      </section>

      <section className="panel" aria-labelledby="waterfall-ledger-heading">
        <h2 id="waterfall-ledger-heading">Final price ledger</h2>
        <div className="quote-table" role="table" aria-label="Pricing waterfall ledger">
          <div role="row" className="quote-table__row quote-table__row--head">
            <span role="columnheader">Step</span>
            <span role="columnheader">Input</span>
            <span role="columnheader">Operation</span>
            <span role="columnheader">Output</span>
            <span role="columnheader">Config</span>
            <span role="columnheader">Reason</span>
          </div>
          {view.finalPrice.ledger.map((row) => <WaterfallLedgerTableRow key={`${row.ordinal}-${row.step}`} row={row} />)}
        </div>
      </section>

      <section className="panel" aria-labelledby="waterfall-blockers-heading">
        <h2 id="waterfall-blockers-heading">Missing-price blockers and replay evidence</h2>
        <div className="offer-list" role="list" aria-label="Pricing waterfall blockers">
          {view.blockers.map((blocker) => (
            <article key={blocker.code} className="banner banner--blocked" role="listitem">
              <strong>{businessFacingText(blocker.code)}</strong>
              <span>{businessFacingText(blocker.message)}</span>
              <span>Source: {businessFacingText(blocker.sourceRef)}</span>
            </article>
          ))}
        </div>
        <ChipList label="Waterfall events" values={view.events.map(businessFacingText)} />
      </section>
    </>
  );
}

function WaterfallLedgerTableRow({ row }: { row: WaterfallLedgerRow }) {
  return (
    <div role="row" className="quote-table__row">
      <span role="cell">{row.ordinal}. {businessFacingText(row.step)}</span>
      <span role="cell">{waterfallValueText(row.inputValue)}</span>
      <span role="cell">{businessFacingText(row.operation)}</span>
      <span role="cell">{waterfallValueText(row.outputValue)}</span>
      <span role="cell">{businessFacingText(row.configRef)}</span>
      <span role="cell">{businessFacingText(row.reasonCode)}{row.roundingMode ? ` · ${businessFacingText(row.roundingMode)}` : ''}</span>
    </div>
  );
}

function waterfallValueText(value: { value: string | null; redacted: boolean; reason: string | null }) {
  if (value.redacted) return `Redacted: ${businessFacingText(value.reason)}`;
  return businessFacingText(value.value);
}

function EligibilityExplanationSection({ runId }: { runId: string }) {
  const [eligibilityState, setEligibilityState] = useState<EligibilityState>({ kind: 'loading' });

  useEffect(() => {
    let active = true;
    fetchEligibilityModule(tenantBoundaryPlaceholder, runId)
      .then((view) => {
        if (active) setEligibilityState({ kind: 'loaded', view });
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'Eligibility explanation is unavailable.';
        if (active) setEligibilityState({ kind: 'unreachable', message });
      });
    return () => {
      active = false;
    };
  }, [runId]);

  if (eligibilityState.kind === 'loading') {
    return <section className="panel" aria-labelledby="eligibility-heading"><h2 id="eligibility-heading">Eligibility explanation</h2><p role="status">Loading eligibility explanation...</p></section>;
  }

  if (eligibilityState.kind === 'unreachable') {
    return (
      <section className="panel" aria-labelledby="eligibility-heading">
        <h2 id="eligibility-heading">Eligibility explanation</h2>
        <div className="banner banner--blocked" role="alert">{eligibilityState.message}</div>
      </section>
    );
  }

  const view = eligibilityState.view;
  const failClosed = view.blockers.length > 0 || view.requiredNextFacts.length > 0;

  return (
    <>
      <section className="hero" aria-labelledby="eligibility-title">
        <p className="eyebrow">Borrower · BRW-S04</p>
        <h2 id="eligibility-title">Eligibility explanation for run {runId}</h2>
        <p>
          Review backend-owned eligibility decisions, reason codes, fact references, overlay references, cache freshness, and
          explanation text without local eligibility policy logic.
        </p>
      </section>

      <section className="panel" aria-labelledby="eligibility-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Eligibility module</p>
            <h2 id="eligibility-heading">Decision explanations</h2>
          </div>
          <DiagnosticsDetails items={[`Support reference: ${view.uiTraceId}`, `Quote option: ${view.quoteOptionId}`]} />
        </div>

        {view.fallbackReason ? (
          <div className="banner banner--blocked" role="alert">
            <strong>Backend explanation contract required</strong>
            <span>{businessFacingText(view.fallbackReason)}</span>
          </div>
        ) : null}

        {failClosed ? <EligibilityBlockers blockers={view.blockers} requiredNextFacts={view.requiredNextFacts} /> : null}

        <div className="eligibility-decision-grid" role="list" aria-label="Eligibility decisions">
          {view.decisions.map((decision) => <EligibilityDecisionCard key={decision.decisionId} decision={decision} />)}
        </div>
      </section>
    </>
  );
}

function EligibilityBlockers({ blockers, requiredNextFacts }: { blockers: EligibilityBlockerView[]; requiredNextFacts: string[] }) {
  return (
    <div className="banner banner--blocked" role="alert">
      <strong>Fail-closed blockers</strong>
      <span>Unknown or conflicting facts block eligibility review without defaulting values.</span>
      <ul>
        {blockers.map((blocker) => <li key={`${blocker.reasonCode}-${blocker.factRef}`}>{blocker.reasonCode}: {blocker.message}</li>)}
      </ul>
      <ChipList label="Required next facts" values={requiredNextFacts} />
    </div>
  );
}

function EligibilityDecisionCard({ decision }: { decision: EligibilityDecisionView }) {
  return (
    <article className="eligibility-decision-card" role="listitem" aria-label={`${decision.decision} eligibility decision`}>
      <div className="panel-heading-row">
        <div>
          <p className="eyebrow">{decision.decision}</p>
          <h3>{decision.decisionId}</h3>
        </div>
        <span className="trace-badge">{decision.cacheFreshness.status}</span>
      </div>
      <dl className="status-grid">
        <dt>Cache reference</dt><dd>{decision.cacheFreshness.cacheRef}</dd>
        <dt>Freshness indicator</dt><dd>{decision.cacheFreshness.indicatorText}</dd>
        <dt>Explanation</dt><dd>{decision.explanationText}</dd>
      </dl>
      <ChipList label="Reason codes" values={decision.reasonCodes} />
      <ChipList label="Input fact refs" values={decision.inputFactRefs} />
      <ChipList label="Overlay refs" values={decision.overlayRefs} />
      <ChipList label="Backend references" values={decision.references} />
    </article>
  );
}

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
        const message = error instanceof Error ? error.message : 'Offer comparison is unavailable.';
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

    const result = await selectOffer(
      tenantBoundaryPlaceholder,
      runId,
      selectedOffer.offerId,
      selectedOffer.sourceScenarioId,
      selectedOffer.scenarioVersion,
      selectedOffer.lockEligibilityRefs,
      selectedOffer.snapshotRefs,
      selectedOffer.auditIds,
    );
    if (result.status === 'SELECTED' && result.selectedOfferId && result.nextRoute) {
      sessionStorage.setItem(`${selectedOfferStoragePrefix}${runId}`, result.selectedOfferId);
      window.history.pushState({ runId, selectedOfferId: result.selectedOfferId, screenId: 'BRW-S04' }, '', result.nextRoute);
      setSelectionMessage(`Offer ${result.selectedOfferId} selected for lock workflow with ${valueText(result.lockEligibilityRef)} and ${valueText(result.snapshotRef)}.`);
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
          Compare available offer rows and inspect plain-language explanations before continuing. The UI does not calculate,
          replace, or infer pricing values.
        </p>
      </section>

      <section className="panel" aria-labelledby="offers-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Offer comparison</p>
            <h2 id="offers-heading">Offer comparison</h2>
          </div>
          <DiagnosticsDetails items={[`Support reference: ${comparison.uiTraceId}`]} />
        </div>

        {comparison.fallbackReason ? (
          <div className="banner banner--blocked" role="alert">
            <strong>Explanation data required</strong>
            <span>{businessFacingText(comparison.fallbackReason)}</span>
          </div>
        ) : null}
        <ChipList label="Required quote facts" values={(comparison.requiredFacts ?? []).map(businessFacingText)} />
        <ChipList label="Backend quote refs" values={(comparison.backendRefs ?? []).map(businessFacingText)} />

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
          <p role="status">No comparable offers are loaded. Selection is blocked until explanation data is available.</p>
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
                  <dt>Rank score</dt><dd>{valueText(offer.rankScore)}</dd>
                  <dt>Scenario version</dt><dd>{valueText(offer.scenarioVersion)}</dd>
                </dl>
                <ChipList label="Rationale" values={offer.rationaleChips} />
                <ChipList label="Scenario flags" values={offer.scenarioFlags} />
                <ChipList label="Configured service refs" values={(offer.upstreamRefs ?? []).map(businessFacingText)} />
                <ChipList label="Lock eligibility refs" values={offer.lockEligibilityRefs ?? []} />
                <ChipList label="Snapshot refs" values={offer.snapshotRefs ?? []} />
                <ChipList label="Audit ids" values={offer.auditIds ?? []} />
                <ChipList label="Explanation sections" values={offer.explanationSections ?? []} />
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
            <>
              <ul>{explanation.rationaleLines.map((line) => <li key={line}>{line}</li>)}</ul>
              <ChipList label="Explanation sections" values={(explanation.explanationSections ?? []).map(businessFacingText)} />
              <ChipList label="Explanation configured service refs" values={(explanation.upstreamRefs ?? []).map(businessFacingText)} />
              <ChipList label="Explanation snapshot refs" values={(explanation.snapshotRefs ?? []).map(businessFacingText)} />
              <ChipList label="Explanation audit ids" values={(explanation.auditIds ?? []).map(businessFacingText)} />
            </>
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
  if (!values.length) return <p className="field-help">No {label.toLowerCase()} provided.</p>;
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
  const match = pathname.match(/^\/quote\/([^/]+)\/(offers|lock|status|eligibility|pricing-waterfall)/);
  return match ? decodeURIComponent(match[1]) : null;
}

function screenFromPath(pathname: string): 'offers' | 'lock' | 'status' | 'eligibility' | 'waterfall' | null {
  const match = pathname.match(/^\/quote\/[^/]+\/(offers|lock|status|eligibility|pricing-waterfall)/);
  if (!match) return null;
  return match[1] === 'pricing-waterfall' ? 'waterfall' : (match[1] as 'offers' | 'lock' | 'status' | 'eligibility');
}

function screenLabel(screen: 'offers' | 'lock' | 'status' | 'eligibility' | 'waterfall' | null) {
  if (screen === 'waterfall') return 'Pricing Waterfall';
  if (screen === 'eligibility') return 'Eligibility Explanation';
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
    return <p role="status">Checking connection...</p>;
  }

  if (state.kind === 'unreachable') {
    return (
      <div role="status" className="status-card status-card--offline">
        <strong>Workbench service unreachable</strong>
        <span>{state.message}</span>
      </div>
    );
  }

  return (
    <div role="status" className="status-card status-card--online">
      <strong>Workbench service reachable</strong>
      <span>{state.health.ready ? 'Ready for local workflow checks' : 'Service is not ready'}</span>
      <span>Setup status: {serviceReadinessText(state.health.dependencyStatus)}</span>
    </div>
  );
}
