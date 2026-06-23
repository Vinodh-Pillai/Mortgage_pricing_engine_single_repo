import { type ReactNode, Suspense, lazy, useEffect, useState } from 'react';
import { Navigate, Route, Routes, useLocation, useNavigate, useParams } from 'react-router-dom';
import {
  fetchOfferComparison,
  fetchOfferExplanation,
  fetchQuoteDetail,
  selectOffer,
  type OfferComparisonView,
  type OfferExplanationView,
  type OfferSummary,
  type QuoteDetailView,
} from './lib/api/offers';
import {
  fetchPricingWaterfall,
  fetchQuoteJourneyMap,
  type PricingWaterfallView,
  type QuoteJourneyMapView,
  type QuoteJourneyNode,
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
  fetchPartnerChannelWorkbench,
  fetchPartnerIntegrationAlerts,
  fetchPartnerWebhookHealth,
  requestPartnerIntegrationAlertAction,
  requestPartnerWebhookEndpointTest,
  requestPartnerWebhookReplay,
  requestPartnerWebhookSafetyToggle,
  type PartnerChannelWorkbenchTab,
  type PartnerChannelWorkbenchView,
  type PartnerIntegrationAlert,
  type PartnerIntegrationAlertActionResult,
  type PartnerIntegrationAlertsView,
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
  fetchCustomRuleCalculationEvidence,
  fetchCustomRuleEvidence,
  type CalculationStepEvidence,
  type CustomFieldMetadata,
  type CustomRuleEvidenceView,
  type CustomRuleUiError,
  type RuleCalculationEvidenceRow,
  type RuleCalculationEvidenceView,
  type RuleEvidenceRow,
} from './lib/api/customRules';
import { partnerIntegrationEvidenceTarget, partnerIntegrationRoutes, partnerIntegrationStateCoverage } from './screens/partnerIntegrations';
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
import { WorkbenchModuleRail } from './screens/workbenchShell/WorkbenchShell';
import { DiagnosticsDetails } from './components/DiagnosticsDetails';
import { ThemeProvider, useTheme } from './design-system';
import { Shell } from './layout';
import { usePageActions } from './layout/PageActionsContext';
import { AuthProvider, useAuth } from './lib/auth/AuthContext';
import { RouteGuard } from './routing/RouteGuard';
import { useCurrentRoute } from './routing/hooks';

const LoginScreen = lazy(() => import('./screens/auth/LoginScreen').then((module) => ({ default: module.LoginScreen })));
const QuoteIntakeScreen = lazy(() => import('./screens/quoteIntake/QuoteIntakeScreen').then((module) => ({ default: module.QuoteIntakeScreen })));
const NotFoundScreen = lazy(() => import('./screens/NotFoundScreen'));
const AdjustmentEvidenceScreen = lazy(() => import('./screens/adjustmentEvidence/AdjustmentEvidenceScreen'));
const MarginProfitabilityScreen = lazy(() => import('./screens/marginProfitability').then((module) => ({ default: module.MarginProfitabilityScreen })));
const ExceptionConcessionsScreen = lazy(() => import('./screens/exceptionConcessions'));
const RateFeedOperationsScreen = lazy(() => import('./screens/rateFeedOps').then((module) => ({ default: module.RateFeedOperationsScreen })));
const RateFeedPipelineScreen = lazy(() => import('./screens/rateFeedPipeline').then((module) => ({ default: module.RateFeedPipelineScreen })));
const OpsCasesScreen = lazy(() => import('./screens/opsCases').then((module) => ({ default: module.OpsCasesScreen })));
const PerformanceDashboardScreen = lazy(() => import('./screens/observabilityPerformance').then((module) => ({ default: module.PerformanceDashboardScreen })));
const ComplianceEvidenceScreen = lazy(() => import('./screens/complianceEvidence').then((module) => ({ default: module.ComplianceEvidenceScreen })));
const FairLendingDashboard = lazy(() => import('./screens/fairLending').then((module) => ({ default: module.FairLendingDashboard })));
const ProductCatalogManagerScreen = lazy(() => import('./screens/productCatalogManager').then((module) => ({ default: module.ProductCatalogManagerScreen })));
const ProductAdminScreen = lazy(() => import('./screens/productAdmin').then((module) => ({ default: module.ProductAdminScreen })));
const AdminGovernanceScreen = lazy(() => import('./screens/adminGovernance').then((module) => ({ default: module.AdminGovernanceScreen })));
const RulesEngineScreen = lazy(() => import('./screens/rulesEngine/RulesEngineScreen'));
const PricingProfilesScreen = lazy(() => import('./screens/pricingProfiles/PricingProfilesScreen'));
const UserManagementScreen = lazy(() => import('./screens/userManagement/UserManagementScreen'));
const MlAdvisoryInsightsScreen = lazy(() => import('./screens/mlAdvisoryInsights').then((module) => ({ default: module.MlAdvisoryInsightsScreen })));
const ModelVersionGovernanceScreen = lazy(() => import('./screens/modelVersionGovernance').then((module) => ({ default: module.ModelVersionGovernanceScreen })));
const DriftMonitoringScreen = lazy(() => import('./screens/driftMonitoring').then((module) => ({ default: module.DriftMonitoringScreen })));
const ScenarioAnalysisWorkspaceScreen = lazy(() => import('./screens/scenarioAnalysis/ScenarioAnalysisWorkspace').then((module) => ({ default: module.ScenarioAnalysisWorkspaceScreen })));
const FicoSensitivityScreen = lazy(() => import('./screens/scenarioAnalysis/sensitivity/FicoSensitivity').then((module) => ({ default: module.FicoSensitivityScreen })));
const LtvSensitivityScreen = lazy(() => import('./screens/scenarioAnalysis/sensitivity/LtvSensitivity').then((module) => ({ default: module.LtvSensitivityScreen })));
const ProductComparisonScreen = lazy(() => import('./screens/scenarioAnalysis/comparison/ProductComparison').then((module) => ({ default: module.ProductComparisonScreen })));
const LockPeriodComparisonScreen = lazy(() => import('./screens/scenarioAnalysis/comparison/LockPeriodComparison').then((module) => ({ default: module.LockPeriodComparisonScreen })));
const TenantOnboardingScreen = lazy(() => import('./screens/tenant/TenantOnboardingScreen').then((module) => ({ default: module.TenantOnboardingScreen })));
const HomeScreen = lazy(() => import('./screens/home').then((module) => ({ default: module.HomeScreen })));
const TenantAdminScreen = lazy(() => import('./screens/tenantAdmin').then((module) => ({ default: module.TenantAdminScreen })));
const ProductManagementScreen = lazy(() => import('./screens/productManagement/ProductManagementScreen').then((module) => ({ default: module.ProductManagementScreen })));
const InvestorManagementScreen = lazy(() => import('./screens/investorManagement/InvestorManagementScreen').then((module) => ({ default: module.InvestorManagementScreen })));
const RateSheetIntakeScreen = lazy(() => import('./screens/ratesheet/RateSheetIntakeScreen').then((module) => ({ default: module.RateSheetIntakeScreen })));
const PricingAnalysisScreen = lazy(() => import('./screens/pricing/PricingAnalysisScreen').then((module) => ({ default: module.PricingAnalysisScreen })));
const LockManagementScreen = lazy(() => import('./screens/locks/LockManagementScreen').then((module) => ({ default: module.LockManagementScreen })));

const uiTraceId = 'brw-s01-local-trace';
const tenantBoundaryPlaceholder = 'ui-preview-tenant';
const partnerBoundaryPlaceholder = 'partner-preview';
const selectedOfferStoragePrefix = 'loanweft:selectedOfferId:';

export function App() {
  return (
    <ThemeProvider>
      <AppRoutes />
    </ThemeProvider>
  );
}

function AppRoutes() {
  const { resolvedTheme, setTheme } = useTheme();
  const location = useLocation();
  const currentRoute = useCurrentRoute();
  const activeModule = currentRoute.module;
  const activeRunId = currentRoute.params.runId ?? null;
  const isLoginRoute = location.pathname === '/login';
  const fullScreenWorkspace = isFullScreenWorkspacePath(location.pathname);

  return (
    <AuthProvider>
        {isLoginRoute ? (
          <Suspense fallback={<section className="panel"><p role="status">Loading route...</p></section>}>
            <Routes>
              <Route path="/login" element={<LoginScreen />} />
              <Route path="*" element={<Navigate to="/login" replace />} />
            </Routes>
          </Suspense>
        ) : (
          <Shell
          activeModuleId={activeModule.id}
          activeRunId={activeRunId}
          breadcrumb={activeModule.breadcrumb}
          fullScreenWorkspace={fullScreenWorkspace}
          notifications={[]}
          onThemeToggle={() => setTheme(resolvedTheme === 'dark' ? 'light' : 'dark')}
          theme={resolvedTheme}
          user={{ name: 'Pricing user', role: 'Pricing analyst' }}
        >
          <Suspense fallback={<section className="panel"><p role="status">Loading route...</p></section>}>
            <RouteGuard>
            <Routes>
              <Route path="/" element={<Navigate to="/home" replace />} />
              <Route path="/login" element={<LoginScreen />} />
              <Route path="/home" element={<HomeRoute />} />
              <Route path="/pipeline" element={<QuoteIntakeScreen tenantId={tenantBoundaryPlaceholder} />} />
              <Route path="/quote/start" element={<QuoteIntakeScreen tenantId={tenantBoundaryPlaceholder} mode="quickquote" />} />
              <Route path="/quote/:runId">
                <Route path="offers" element={<QuoteOffersRoute />} />
                <Route path="offers/:optionId" element={<QuoteDetailRoute />} />
                <Route path="pricing-waterfall" element={<PricingWaterfallRoute />} />
                <Route path="journey" element={<QuoteJourneyRoute />} />
                <Route path="what-if" element={<ScenarioAnalysisRoute />} />
                <Route path="what-if/fico-sensitivity" element={<FicoSensitivityRoute />} />
                <Route path="what-if/ltv-sensitivity" element={<LtvSensitivityRoute />} />
                <Route path="what-if/product-comparison" element={<ProductComparisonRoute />} />
                <Route path="what-if/lock-period-comparison" element={<LockPeriodComparisonRoute />} />
                <Route path="lock" element={<QuoteLockRoute />} />
                <Route path="status" element={<QuoteLockRoute />} />
                <Route path="eligibility" element={<EligibilityRoute />} />
                <Route path="*" element={<NotFoundRoute />} />
              </Route>
              <Route path="/pricing/adjustments" element={<AdjustmentEvidenceScreen tenantContext={tenantBoundaryPlaceholder} />} />
              <Route path="/pricing/margins" element={<MarginProfitabilityScreen tenantContext={tenantBoundaryPlaceholder} />} />
              <Route path="/exceptions/concessions" element={<ExceptionConcessionWorkbenchSection />} />
              <Route path="/partners/quotes/*" element={<PartnerQuoteLifecycleSection partnerId={partnerBoundaryPlaceholder} />} />
              <Route path="/partners/integrations/*" element={<PartnerTransportReliabilitySection partnerId={partnerBoundaryPlaceholder} />} />
              <Route path="/partners/webhooks/*" element={<PartnerTransportReliabilitySection partnerId={partnerBoundaryPlaceholder} />} />
              <Route path="/partners/admin/safety/*" element={<PartnerTransportReliabilitySection partnerId={partnerBoundaryPlaceholder} />} />
              <Route path="/partners/alerts/*" element={<PartnerTransportReliabilitySection partnerId={partnerBoundaryPlaceholder} />} />
              <Route path="/ops/rate-feeds" element={<RateFeedOperationsScreen tenantContext={tenantBoundaryPlaceholder} />} />
              <Route path="/admin/ratefeed/pipeline" element={<RateFeedPipelineScreen tenantId={tenantBoundaryPlaceholder} />} />
              <Route path="/ops/performance" element={<PerformanceDashboardScreen tenantContext={tenantBoundaryPlaceholder} />} />
              <Route path="/ops/dashboard" element={<OpsCasesScreen tenantContext={tenantBoundaryPlaceholder} />} />
              <Route path="/ops/cases/:caseId" element={<OperationsCaseTriageSection />} />
              <Route path="/compliance/evidence" element={<ComplianceEvidenceScreen tenantContext={tenantBoundaryPlaceholder} />} />
              <Route path="/compliance/fair-lending" element={<FairLendingDashboard tenantId="aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa" />} />
              <Route path="/quality/validation" element={<QualityGuardrailsSection />} />
              <Route path="/tenant/onboarding" element={<TenantOnboardingScreen />} />
              <Route path="/admin/tenants" element={<TenantAdminScreen />} />
              <Route path="/admin/users" element={<UserManagementScreen />} />
              <Route path="/admin/tenants/new" element={<TenantOnboardingScreen />} />
              <Route path="/admin/products" element={<ProductAdminScreen />} />
              <Route path="/admin/products/management" element={<ProductManagementScreen />} />
              <Route path="/admin/products/management/:productCode" element={<ProductManagementScreen />} />
              <Route path="/admin/products/catalog" element={<ProductManagementScreen />} />
              <Route path="/admin/products/catalog/:productCode" element={<ProductManagementScreen />} />
              <Route path="/admin/products/new" element={<ProductManagementScreen />} />
              <Route path="/admin/investors" element={<InvestorManagementScreen />} />
              <Route path="/pricing/rate-sheets" element={<RateSheetIntakeScreen />} />
              <Route path="/pricing/rate-sheets/new" element={<RateSheetIntakeScreen />} />
              <Route path="/pricing/rate-sheets/:id" element={<RateSheetIntakeScreen />} />
              <Route path="/pricing/profiles" element={<PricingProfilesScreen />} />
              <Route path="/pricing/analysis" element={<PricingAnalysisScreen />} />
              <Route path="/pricing/analysis/:runId" element={<PricingAnalysisScreen />} />
              <Route path="/locks" element={<LockManagementScreen />} />
              <Route path="/locks/:lockId" element={<LockManagementScreen />} />
              <Route path="/admin/governance" element={<AdminGovernanceScreen />} />
              <Route path="/rules-engine" element={<RulesEngineScreen />} />
              <Route path="/custom-rules" element={<CustomRuleEvidenceSection />} />
              <Route path="/platform/tenant-context" element={<TenantPlatformCoverageSection />} />
              <Route path="/audit/replay" element={<AuditReplayWorkbenchSection />} />
              <Route path="/advisory/ml/drift" element={<DriftMonitoringScreen />} />
              <Route path="/advisory/ml/governance" element={<ModelVersionGovernanceScreen />} />
              <Route path="/advisory/ml" element={<MlAdvisoryInsightsScreen />} />
              <Route path="/service-modules" element={<FeatureRegistryRoute />} />
              <Route path="*" element={<NotFoundRoute />} />
            </Routes>
            </RouteGuard>
          </Suspense>
          </Shell>
        )}
    </AuthProvider>
  );
}

function requiredRouteParam(value: string | undefined, fallback: string) {
  return value ? decodeURIComponent(value) : fallback;
}

function isFullScreenWorkspacePath(pathname: string) {
  return pathname === '/pipeline' || pathname === '/quote/start' || pathname.startsWith('/custom-rules');
}

function HomeRoute() {
  const { currentPersona, currentUser } = useAuth();
  return <HomeScreen userId={currentUser?.id ?? 'local-home-user'} role={currentPersona?.role ?? 'metadata-unavailable'} />;
}

function QuoteOffersRoute() {
  const { runId } = useParams();
  return <OfferComparisonSection runId={requiredRouteParam(runId, 'quote-run-required')} />;
}

function QuoteDetailRoute() {
  const { runId, optionId } = useParams();
  return <QuoteDetailSection runId={requiredRouteParam(runId, 'quote-run-required')} offerId={requiredRouteParam(optionId, 'quote-option-contract-required')} />;
}

function QuoteLockRoute() {
  const { runId } = useParams();
  return <LockLifecycleSection runId={requiredRouteParam(runId, 'quote-run-required')} />;
}

function EligibilityRoute() {
  const { runId } = useParams();
  return <EligibilityExplanationSection runId={requiredRouteParam(runId, 'quote-run-required')} />;
}

function PricingWaterfallRoute() {
  const { runId } = useParams();
  return <PricingWaterfallSection runId={requiredRouteParam(runId, 'quote-run-required')} />;
}

function QuoteJourneyRoute() {
  const { runId } = useParams();
  return <QuoteJourneyMapSection runId={requiredRouteParam(runId, 'quote-run-required')} />;
}

function ScenarioAnalysisRoute() {
  const { runId } = useParams();
  return <ScenarioAnalysisWorkspaceScreen runId={requiredRouteParam(runId, 'quote-run-required')} tenantContext={tenantBoundaryPlaceholder} />;
}

function FicoSensitivityRoute() {
  const { runId } = useParams();
  return <FicoSensitivityScreen runId={requiredRouteParam(runId, 'quote-run-required')} tenantContext={tenantBoundaryPlaceholder} />;
}

function LtvSensitivityRoute() {
  const { runId } = useParams();
  return <LtvSensitivityScreen runId={requiredRouteParam(runId, 'quote-run-required')} tenantContext={tenantBoundaryPlaceholder} />;
}

function ProductComparisonRoute() {
  const { runId } = useParams();
  return <ProductComparisonScreen runId={requiredRouteParam(runId, 'quote-run-required')} tenantContext={tenantBoundaryPlaceholder} />;
}

function LockPeriodComparisonRoute() {
  const { runId } = useParams();
  return <LockPeriodComparisonScreen runId={requiredRouteParam(runId, 'quote-run-required')} tenantContext={tenantBoundaryPlaceholder} />;
}

function FeatureRegistryRoute() {
  return (
    <WorkbenchModuleRail activeModuleId="service-modules" />
  );
}

function NotFoundRoute() {
  return <NotFoundScreen tenantId={tenantBoundaryPlaceholder} uiTraceId="pii-25-s01-not-found" onEvidenceCapture={() => undefined} />;
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

type PartnerChannelWorkbenchState =
  | { kind: 'loading' }
  | { kind: 'loaded'; view: PartnerChannelWorkbenchView }
  | { kind: 'unreachable'; message: string };

type PartnerIntegrationAlertsState =
  | { kind: 'loading' }
  | { kind: 'loaded'; view: PartnerIntegrationAlertsView }
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
  | { kind: 'loaded'; view: CustomRuleEvidenceView; calculationEvidence: RuleCalculationEvidenceView }
  | { kind: 'unreachable'; message: string };

const blockingDecisionQualities = new Set(['UNKNOWN', 'CONFLICTING']);
const estimatedDecisionQualities = new Set(['ESTIMATED']);

type AuditReplayWorkbenchState =
  | { kind: 'loading' }
  | { kind: 'loaded'; view: AuditReplayWorkbenchView }
  | { kind: 'unreachable'; message: string };

type TenantPlatformCoverageState =
  | { kind: 'loading' }
  | { kind: 'loaded'; view: TenantPlatformCoverageView }
  | { kind: 'unreachable'; message: string };

function CustomRuleEvidenceSection() {
  const [ruleState, setRuleState] = useState<CustomRuleEvidenceState>({ kind: 'loading' });

  useEffect(() => {
    let active = true;
    Promise.all([fetchCustomRuleEvidence(), fetchCustomRuleCalculationEvidence()])
      .then(([view, calculationEvidence]) => {
        if (active) setRuleState({ kind: 'loaded', view, calculationEvidence });
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
    return (
      <CustomRulesRouteShell>
        <section className="panel" aria-labelledby="custom-rules-heading">
          <h2 id="custom-rules-heading">Custom rule workbench loading state</h2>
          <p role="status">Loading custom rule evidence from pricing-bff...</p>
        </section>
      </CustomRulesRouteShell>
    );
  }

  if (ruleState.kind === 'unreachable') {
    return (
      <CustomRulesRouteShell>
        <section className="panel" aria-labelledby="custom-rules-heading">
          <h2 id="custom-rules-heading">Custom rule workbench blocked state</h2>
          <div className="banner banner--blocked" role="alert">
            <strong>Custom rules unavailable</strong>
            <span>{ruleState.message}</span>
            <span>Backend contracts are not available, so the shell is not rendering field capture, blockers, or evidence rows.</span>
          </div>
        </section>
      </CustomRulesRouteShell>
    );
  }

  const view = ruleState.view;
  const calculationEvidence = ruleState.calculationEvidence;
  const typedFactBlockers = view.fields.filter((field) => field.requiredForRules && blockingDecisionQualities.has(field.decisionQuality.toUpperCase()));
  const estimatedFacts = view.fields.filter((field) => estimatedDecisionQualities.has(field.decisionQuality.toUpperCase()));
  return (
    <CustomRulesRouteShell diagnostics={[`Support reference: ${view.uiTraceId}`, `Workspace ${view.tenantContext}`]} showPanelNav>
      <section className="hero" aria-labelledby="custom-rules-title">
        <p className="eyebrow">Custom rules Â· PII-21-S06</p>
        <h2 id="custom-rules-title">Custom field and calculation evidence</h2>
        <p>
          Review backend-supplied field metadata, decision-quality blockers, matched and skipped rules, and processing references.
          The workbench displays configured evidence only; it does not calculate prices or infer mortgage policy.
        </p>
      </section>

      <section className="panel" aria-labelledby="custom-rules-capture-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Field capture</p>
            <h2 id="custom-rules-capture-heading">Configured custom rule fields</h2>
          </div>
        </div>
        <p className="field-help">Fields are displayed only when pricing-bff supplies metadata. Inputs remain disabled until the configured save contract is available.</p>
        {view.fields.length === 0 ? (
          <div className="banner banner--info" role="status">No backend field metadata is available yet; the shell will not render placeholder custom-rule fields.</div>
        ) : (
          <div className="custom-rules-field-grid" role="group" aria-label="Custom rule field capture controls">
            {view.fields.map((field) => <CustomRuleCaptureField key={field.fieldId} field={field} />)}
          </div>
        )}
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

        {typedFactBlockers.length > 0 ? <TypedFactBlockerDetails fields={typedFactBlockers} rules={view.evidence.skippedRules} /> : null}
        {estimatedFacts.length > 0 ? <EstimatedFactWarning fields={estimatedFacts} /> : null}

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
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Backend rule ledger</p>
            <h2 id="calculation-evidence-heading">Calculation evidence panel</h2>
          </div>
          <DiagnosticsDetails items={[`Support reference: ${calculationEvidence.uiTraceId}`, `Quote ref: ${calculationEvidence.quoteId}`]} />
        </div>
        <div className={calculationEvidence.resultStatus.toLowerCase().includes('blocked') ? 'banner banner--blocked' : 'banner banner--info'} role={calculationEvidence.resultStatus.toLowerCase().includes('blocked') ? 'alert' : 'status'}>
          <strong>{businessFacingText(calculationEvidence.resultStatus)}</strong>
          <span>Setup status: {serviceReadinessText(calculationEvidence.dependencyStatus)}</span>
          <span>Blocked evidence rows keep backend refusal semantics and are not recalculated in the browser.</span>
          <RuleEvidenceErrors errors={calculationEvidence.errors} />
        </div>
        <dl className="status-grid">
          <dt>Quote evidence ref</dt><dd><code>{calculationEvidence.quoteId}</code></dd>
          <dt>Precision</dt><dd>{businessFacingText(view.evidence.precision)}</dd>
          <dt>Result status</dt><dd>{businessFacingText(calculationEvidence.resultStatus)}</dd>
        </dl>
        <CopyableRefList label="Review references" values={calculationEvidence.auditRefs} />
        <CopyableRefList label="Processing record references" values={calculationEvidence.replayHashRefs.length > 0 ? calculationEvidence.replayHashRefs : [view.evidence.replayHashRef]} />
        <ChipList label="Reason codes" values={calculationEvidence.reasonCodes.map(businessFacingText)} />
        <RuleEvidenceTable label="Matched rules" rows={calculationEvidence.matchedRules} />
        <RuleEvidenceTable label="Skipped rules" rows={calculationEvidence.skippedRules} />
        <RuleEvidenceTable label="Blocked rules" rows={calculationEvidence.blockedRules} blocked />
        <CalculationStepList steps={calculationEvidence.calculationSteps} />
      </section>

      <section className="panel" aria-labelledby="design-evidence-heading">
        <h2 id="design-evidence-heading">Design evidence status</h2>
        <div className="banner banner--blocked" role="alert">
          <strong>{businessFacingText(view.designEvidence.status)}</strong>
          <span>{view.designEvidence.blocker}</span>
          <ChipList label="Safe design evidence options" values={view.designEvidence.safeOptions} />
        </div>
      </section>
    </CustomRulesRouteShell>
  );
}

function CustomRulesRouteShell({ children, diagnostics = [], showPanelNav = false }: { children: ReactNode; diagnostics?: string[]; showPanelNav?: boolean }) {
  return (
    <div className="custom-rules-shell" aria-labelledby="custom-rules-shell-heading">
      <section className="module-rail custom-rules-route-shell" aria-labelledby="custom-rules-shell-heading">
        <div className="module-rail__copy">
          <p className="eyebrow">Custom rules workspace</p>
          <h2 id="custom-rules-shell-heading">Custom rules workbench</h2>
          <p>
            This screen composes field capture, decision attention items, and review panels through the existing workbench service connection.
            If required setup is unavailable, the screen asks for configuration instead of inventing fields or calculation records.
          </p>
        </div>
        {showPanelNav ? (
          <nav className="custom-rules-shell__nav" aria-label="Custom rules panel order">
            <a href="#custom-rules-capture-heading">Field capture</a>
            <a href="#custom-rules-heading">Decision attention items</a>
            <a href="#calculation-evidence-heading">Review panels</a>
            <a href="#design-evidence-heading">Design setup state</a>
          </nav>
        ) : null}
        {diagnostics.length > 0 ? <DiagnosticsDetails items={diagnostics} /> : null}
      </section>
      {children}
    </div>
  );
}

function CustomRuleCaptureField({ field }: { field: CustomFieldMetadata }) {
  const helpId = `${field.fieldId}-capture-help`;
  return (
    <div className="field-group custom-rules-field">
      <label htmlFor={`${field.fieldId}-capture`}>{field.label}</label>
      {field.dataType.toLowerCase() === 'textarea' ? (
        <textarea id={`${field.fieldId}-capture`} value="" disabled aria-describedby={helpId} readOnly />
      ) : (
        <input id={`${field.fieldId}-capture`} value="" disabled aria-describedby={helpId} readOnly />
      )}
      <p id={helpId} className="field-help">
        {field.helpText} Source: {businessFacingText(field.sourceRef)}. Decision quality: {businessFacingText(field.decisionQuality)}.
      </p>
      <ChipList label={`${field.fieldId} allowed values`} values={field.allowedValues.map(businessFacingText)} />
    </div>
  );
}

function TypedFactBlockerDetails({ fields, rules }: { fields: CustomFieldMetadata[]; rules: RuleEvidenceRow[] }) {
  return (
    <details className="blocker-details" open>
      <summary>Typed fact blocker details</summary>
      <div className="quote-table" role="table" aria-label="Typed fact blocker details">
        <div role="row" className="quote-table__row quote-table__row--head">
          <span role="columnheader">Field ID</span>
          <span role="columnheader">Decision quality</span>
          <span role="columnheader">Source refs</span>
          <span role="columnheader">Recovery owner</span>
          <span role="columnheader">Rule refs</span>
        </div>
        {fields.map((field) => (
          <div key={field.fieldId} role="row" className="quote-table__row">
            <span role="cell"><strong>{businessFacingText(field.fieldId)}</strong></span>
            <span role="cell">{businessFacingText(field.decisionQuality)}</span>
            <span role="cell"><ChipList label={`${field.fieldId} source references`} values={sourceReferencesForField(field).map(businessFacingText)} /></span>
            <span role="cell">{businessFacingText(recoveryOwnerForField(field))}</span>
            <span role="cell"><ChipList label={`${field.fieldId} blocking rules`} values={ruleRefsForField(field.fieldId, rules).map(businessFacingText)} /></span>
          </div>
        ))}
      </div>
    </details>
  );
}

function EstimatedFactWarning({ fields }: { fields: CustomFieldMetadata[] }) {
  return (
    <div className="banner banner--info" role="status">
      <strong>Estimated typed fact evidence</strong>
      <span>Estimated values stay labeled as ESTIMATED and are not displayed as confirmed facts.</span>
      <ChipList label="Estimated fields" values={fields.map((field) => businessFacingText(`${field.fieldId}: ${field.decisionQuality}`))} />
    </div>
  );
}

function sourceReferencesForField(field: CustomFieldMetadata) {
  const references = field.sourceRefs && field.sourceRefs.length > 0 ? field.sourceRefs : [field.sourceRef];
  return references.filter(Boolean);
}

function recoveryOwnerForField(field: CustomFieldMetadata) {
  return field.recoveryOwner || field.sourceRef;
}

function ruleRefsForField(fieldId: string, rules: RuleEvidenceRow[]) {
  const refs = rules.filter((rule) => rule.factRefs.includes(fieldId)).map((rule) => rule.ruleRef);
  return refs.length > 0 ? refs : ['No backend rule reference supplied'];
}

function RuleEvidenceErrors({ errors }: { errors: CustomRuleUiError[] }) {
  if (errors.length === 0) return null;
  return (
    <ul className="evidence-error-list" aria-label="Backend refusal semantics">
      {errors.map((error) => (
        <li key={`${error.code}-${error.sourceService}`}>
          <code>{error.code}</code> Â· <code>{error.sourceService}</code> Â· {error.message}
        </li>
      ))}
    </ul>
  );
}

function CopyableRefList({ label, values }: { label: string; values: string[] }) {
  if (values.length === 0) return null;
  return (
    <div className="copyable-ref-list" aria-label={label}>
      <strong>{label}</strong>
      <ul>
        {values.map((value) => <li key={value}><code>{value}</code></li>)}
      </ul>
    </div>
  );
}

function CalculationStepList({ steps }: { steps: CalculationStepEvidence[] }) {
  if (steps.length === 0) return null;
  return (
    <div className="module-rail__grid" role="list" aria-label="Calculation ledger steps">
      {steps.map((step) => (
        <article key={step.stepRef} className={step.status.toLowerCase().includes('blocked') ? 'module-card evidence-card--blocked' : 'module-card'} role="listitem">
          <p className="module-card__route"><code>{step.stepRef}</code></p>
          <strong className="module-card__title">{businessFacingText(step.status)}</strong>
          <p>{step.summary}</p>
          <dl>
            <dt>Source</dt><dd><code>{step.sourceService}</code></dd>
            <dt>Evidence ref</dt><dd><code>{step.evidenceRef}</code></dd>
            <dt>Ledger step ref</dt><dd><code>{step.ledgerStepRef ?? step.stepRef}</code></dd>
            <dt>Precision</dt><dd>{step.precision ?? 'Backend did not supply precision metadata'}</dd>
            <dt>Rounding</dt><dd>{step.rounding ?? 'Backend did not supply rounding metadata'}</dd>
            <dt>Units</dt><dd>{step.units ?? 'Backend did not supply units metadata'}</dd>
          </dl>
        </article>
      ))}
    </div>
  );
}

function ExceptionConcessionWorkbenchSection() {
  return <ExceptionConcessionsScreen tenantContext={tenantBoundaryPlaceholder} />;
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
        const message = error instanceof Error ? error.message : 'Review record workbench is unavailable.';
        if (active) setAuditState({ kind: 'unreachable', message });
      });
    return () => {
      active = false;
    };
  }, []);

  if (auditState.kind === 'loading') {
    return <section className="panel" aria-labelledby="audit-replay-heading"><h2 id="audit-replay-heading">Review record workbench</h2><p role="status">Loading review records...</p></section>;
  }

  if (auditState.kind === 'unreachable') {
    return (
      <section className="panel" aria-labelledby="audit-replay-heading">
        <h2 id="audit-replay-heading">Review record workbench</h2>
        <div className="banner banner--blocked" role="alert">{auditState.message}</div>
      </section>
    );
  }

  const view = auditState.view;
  return (
    <>
      <section className="hero" aria-labelledby="audit-replay-title">
        <p className="eyebrow">Review records Â· PII-22-S10</p>
        <h2 id="audit-replay-title">Review record workbench</h2>
        <p>
          Search review records, verify integrity, inspect quote and lock processing blockers, and keep evidence export decisions
          service-governed. The UI displays refs and states only; it does not infer pricing, retention, or compliance rules.
        </p>
      </section>

      <section className="panel" aria-labelledby="audit-replay-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Search and integrity</p>
            <h2 id="audit-replay-heading">Review record search results</h2>
          </div>
          <DiagnosticsDetails items={[`Support reference: ${view.uiTraceId}`, `Workspace ${view.tenantContext}`]} />
        </div>
        <div className="banner banner--blocked" role="alert">
          <strong>Review record setup required</strong>
          <span>{businessFacingText(view.fallbackReason)}</span>
          <span>Setup status: {serviceReadinessText(view.dependencyStatus)}</span>
          <ChipList label="Review record blockers" values={view.blockers.map(businessFacingText)} />
        </div>
        <div className="quote-table" role="table" aria-label="Review processing records">
          <div role="row" className="quote-table__row quote-table__row--head">
            <span role="columnheader">Event</span>
            <span role="columnheader">Subject</span>
            <span role="columnheader">Processing check</span>
            <span role="columnheader">Redaction and retention</span>
            <span role="columnheader">Export eligibility</span>
          </div>
          {view.records.map((record) => <AuditRecordEvidenceRow key={record.eventId} record={record} />)}
        </div>
      </section>

      <section className="panel" aria-labelledby="audit-replay-runs-heading">
        <h2 id="audit-replay-runs-heading">Quote and lock processing comparisons</h2>
        <div className="module-rail__grid" role="list" aria-label="Processing run summaries">
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
          <dt>Export package record</dt><dd>{businessFacingText(view.exportSummary.manifestHash)}</dd>
        </dl>
        <button type="button" disabled={!view.exportSummary.downloadEligible} aria-describedby="audit-export-blockers">Download evidence export</button>
        <div id="audit-export-blockers">
          <ChipList label="Export blockers" values={view.exportSummary.blockers.map(businessFacingText)} />
          <ChipList label="Review record events" values={view.events.map(businessFacingText)} />
        </div>
      </section>

      <section className="panel" aria-labelledby="audit-contract-heading">
        <h2 id="audit-contract-heading">Backend contract registry</h2>
        <div className="quote-table" role="table" aria-label="Review record contract registry">
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
      <span role="cell">{businessFacingText(record.subjectType)} Â· {businessFacingText(record.subjectId)}</span>
      <span role="cell">{businessFacingText(record.hashIntegrity)}<ChipList label={`${businessFacingText(record.eventId)} review references`} values={record.evidenceRefs.map(businessFacingText)} /></span>
      <span role="cell">{businessFacingText(record.redactionProfile)}<br />{businessFacingText(record.retentionState)}<br />Legal hold: {record.legalHold ? 'active' : 'not active'}</span>
      <span role="cell">{businessFacingText(record.exportEligibility)}</span>
    </div>
  );
}

function AuditReplayRunCard({ run }: { run: AuditReplayRunSummary }) {
  return (
    <article className="module-card module-card--light" role="listitem">
      <p className="module-card__route">{businessFacingText(run.status)}</p>
        <strong className="module-card__title">{businessFacingText(run.replayType)} processing review for {businessFacingText(run.subjectId)}</strong>
      <dl>
        <dt>Original processing record</dt><dd>{businessFacingText(run.originalHash)}</dd>
        <dt>Updated processing record</dt><dd>{businessFacingText(run.replayHash)}</dd>
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
        <p className="eyebrow">Platform Â· PII-22-S08</p>
        <h2 id="tenant-platform-title">Tenant platform coverage</h2>
        <p>
          Review tenant context propagation, cache scoping, rate limiting, review records, delivery packages, processing records, and readiness
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
          <dt>Support reference</dt><dd>{businessFacingText(view.trace.correlationIdRef)}</dd>
          <dt>Request safety record</dt><dd>{businessFacingText(view.trace.idempotencyKeyRef)}</dd>
          <dt>Delivery package</dt><dd>{businessFacingText(view.trace.eventEnvelopeRef)}</dd>
          <dt>Review reference</dt><dd>{businessFacingText(view.trace.auditRef)}</dd>
          <dt>Processing record</dt><dd>{businessFacingText(view.trace.replayHashRef)}</dd>
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

function RuleEvidenceTable({ label, rows, blocked = false }: { label: string; rows: (RuleEvidenceRow | RuleCalculationEvidenceRow)[]; blocked?: boolean }) {
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
        <div key={`${row.ruleRef}-${row.versionRef}-${row.reasonCode}`} role="row" className={blocked || row.outcome.toLowerCase().includes('blocked') ? 'quote-table__row quote-table__row--blocked' : 'quote-table__row'} aria-label={`${row.outcome} ${row.ruleRef}`}>
          <span role="cell"><code>{row.ruleRef}</code></span>
          <span role="cell"><code>{row.versionRef}</code></span>
          <span role="cell">{businessFacingText(row.outcome)}</span>
          <span role="cell">{businessFacingText(row.reasonCode)}</span>
          <div role="cell">
            <CopyableRefList label={`${row.ruleRef} fact references`} values={row.factRefs} />
            {'sourceService' in row ? <CopyableRefList label={`${row.ruleRef} review references`} values={row.auditRefs} /> : null}
            {'sourceService' in row ? <CopyableRefList label={`${row.ruleRef} processing record references`} values={row.replayHashRefs} /> : null}
          </div>
        </div>
      ))}
    </div>
  );
}

function businessFacingText(value: string | number | null | undefined) {
  if (value === null || value === undefined || value === '') return 'Not provided';
  return String(value)
    .replace(/NO_UPSTREAMS_CONFIGURED/gi, 'Connected services need setup')
    .replace(/ui-preview-tenant/gi, 'Product workspace')
    .replace(/payment[- ]ref[- ]required/gi, 'Payment not supplied')
    .replace(/apr[- ]ref[- ]required/gi, 'APR not supplied')
    .replace(/score[: -]*backend[- ]owned/gi, 'Score pending')
    .replace(/quote[- ]service\s+evidence/gi, 'quote review record')
    .replace(/FALLBACK_STATIC_DEPENDENCIES_UNAVAILABLE/gi, 'Setup details need review')
    .replace(/Support reference/gi, 'Support detail')
    .replace(/validation_result\.json|validation_trace\.jsonl|module_evidence_index\.json|blocker_register\.json/gi, 'review package item')
    .replace(/ui_trace_id|uiTraceId|trace id|trace refs?|correlation id/gi, 'support reference')
    .replace(/deployment disabled|deployment-disabled/gi, 'live action unavailable')
    .replace(/release readiness|release candidate readiness|release candidate/gi, 'business readiness')
    .replace(/contract conformance blocker/gi, 'setup review item')
    .replace(/evidence set completeness|completeness status/gi, 'review package completeness')
    .replace(/quality owner signature required|release owner signature required|owner signature required/gi, 'owner review required')
    .replace(/smoke check: configured result required|schema compatibility: blocked|config validation: pending|policy signatures: required/gi, 'setup review required')
    .replace(/adapter boundary|adapter status|adapter/gi, 'service connection')
    .replace(/backend[- ]owned/gi, 'authoritative')
    .replace(/backend/gi, 'connected service')
    .replace(/blockers?/gi, 'items needing attention')
    .replace(/blocked/gi, 'needs attention')
    .replace(/evidence/gi, 'review record')
    .replace(/audit/gi, 'review')
    .replace(/replay hash|replay/gi, 'review reference')
    .replace(/hash/gi, 'processing record')
    .replace(/integrity/gi, 'processing check')
    .replace(/rounding trace/gi, 'rounding review')
    .replace(/BFF/gi, 'workbench service')
    .replace(/Configured upstream/gi, 'Configured service')
    .replace(/upstream/gi, 'configured service')
    .replace(/downstream/gi, 'connected workflow')
    .replace(/SLA contract required/gi, 'Response target needs setup')
    .replace(/Awaiting configured SLA contract/gi, 'Response target needs setup')
    .replace(/SLA deadline supplied by configured privacy service/gi, 'Response target supplied by configured privacy service')
    .replace(/configuration/gi, 'setup')
    .replace(/configured/gi, 'available')
    .replace(/\bconfig\b/gi, 'setup')
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
    .replace(/[_:./-]+/g, ' ')
    .replace(/_/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function serviceReadinessText(value: string | null | undefined) {
  if (!value) return 'Not provided';
  return 'Setup needed before live service use.';
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
        <p className="eyebrow">Admin Â· AG-S01 / AG-S07</p>
        <h2 id="admin-title">Admin governance and readiness controls</h2>
        <p>
          Review guidance updates, flag conflicts, market guidance completeness, change approvals, business readiness,
          incidents, and review records in one guided board. Release actions stay disabled while configured governance
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
              <span role="cell">{descriptor.versionRef}<br />{businessFacingText(descriptor.decisionQualityRequirement)}<ChipList label={`${descriptor.stableId} validation messages`} values={descriptor.validationMessages.map(businessFacingText)} /></span>
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
          <strong>{view.pendingReview.reviewId} Â· {businessFacingText(view.pendingReview.state)}</strong>
          <span>Simulation, approval, publish, rollback, review, and consumer impact stay visible from connected service records.</span>
          <span>Review record: {businessFacingText(view.pendingReview.auditRef)}</span>
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
          <dt>Processing record</dt><dd>{businessFacingText(view.dynamicRuleEvidence.replayHashRef)}</dd>
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
              <h3>{decision.decisionId} Â· {decision.status}</h3>
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
            <span role="columnheader">Record</span>
            <span role="columnheader">Lineage</span>
          </div>
          {view.policies.map((policy) => (
            <div key={policy.versionId} role="row" className="quote-table__row">
              <span role="cell">{policy.versionId}</span>
              <span role="cell">{policy.status}</span>
              <span role="cell">{policy.owner}</span>
              <span role="cell">{businessFacingText(policy.hashSignature)}</span>
              <span role="cell">previous {policy.parentVersionId}</span>
            </div>
          ))}
        </div>
        {view.policies.map((policy) => <ChipList key={`${policy.versionId}-impacts`} label={`${policy.versionId} change impacts`} values={policy.diffImpacts.map(businessFacingText)} />)}
        {view.featureFlags.map((flag) => (
          <div key={flag.flagId} className="banner banner--blocked" role="alert">
            <strong>Feature flag activation blocked</strong>
            <span>{flag.flagId} Â· {flag.environmentTarget}</span>
            <span>{businessFacingText(flag.emergencyToggleGate)}</span>
            <ChipList label="Unresolved flags" values={flag.unresolvedFlags.map(businessFacingText)} />
          </div>
        ))}
        {view.marketRules.map((rule) => (
          <div key={rule.ruleId} className="banner banner--blocked" role="alert">
            <strong>Market guidance promotion blocked</strong>
            <span>{rule.ruleId} Â· {rule.stagingStatus}</span>
            <span>{businessFacingText(rule.completenessGate)}</span>
            <ChipList label="Missing market guidance fields" values={rule.missingRequiredFields.map(businessFacingText)} />
          </div>
        ))}
      </section>

      <section className="panel" aria-labelledby="change-requests-heading">
        <h2 id="change-requests-heading">Change requests and approval state machine</h2>
        {view.changeRequests.map((request) => (
          <article key={request.requestId} className="status-card status-card--offline" role="alert">
            <h3>{request.requestId} Â· {request.state}</h3>
            <p>{request.requestType} Â· {request.riskLevel} Â· owner {request.owner}</p>
            <button type="button" disabled={request.promotionDisabled}>Approve change request</button>
            <ChipList label="Required state sequence" values={request.requiredStateSequence.map(businessFacingText)} />
            <ChipList label="Change request blockers" values={request.blockers.map(businessFacingText)} />
          </article>
        ))}
      </section>

      <section className="panel" aria-labelledby="drift-incident-heading">
        <h2 id="drift-incident-heading">Change alerts, incidents, rollback, and review history</h2>
        {view.driftAlerts.map((alert) => (
          <div key={alert.alertId} className="banner banner--blocked" role="alert">
            <strong>{alert.alertId} Â· {alert.severity}</strong>
            <span>{businessFacingText(alert.summary)}</span>
            <span>Owner: {alert.owner} Â· Environment: {alert.environment}</span>
          </div>
        ))}
        <ul className="offer-list" aria-label="Admin incidents">
          {view.incidents.map((incident) => <AdminIncidentCard key={incident.incidentId} incident={incident} />)}
        </ul>
        <div className="quote-table" role="table" aria-label="Change review history">
          <div role="row" className="quote-table__row quote-table__row--head">
            <span role="columnheader">Record</span>
            <span role="columnheader">Actor</span>
            <span role="columnheader">Field</span>
            <span role="columnheader">Policy</span>
            <span role="columnheader">Review reference</span>
          </div>
          {view.overrideLedger.map((entry) => (
            <div key={entry.ledgerId} role="row" className="quote-table__row">
              <span role="cell">{entry.ledgerId}</span>
              <span role="cell">{entry.actor}</span>
              <span role="cell">{entry.fieldPath}</span>
              <span role="cell">{entry.policyRef}</span>
              <span role="cell">{businessFacingText(entry.auditRef)}</span>
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
      <h3>{incident.incidentId} Â· {incident.status}</h3>
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
        <p className="eyebrow">Quality Â· QL-S01 / QL-S07</p>
        <h2 id="quality-title">Quality guardrails dashboard</h2>
        <p>
          Review validation status, business readiness, fairness, incidents, review readiness, and setup checks.
          The UI reports configured review states and does not invent thresholds.
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
              <span role="cell">{stage.stageId} Â· {stage.label}</span>
              <span role="cell">{stage.status}</span>
              <span role="cell">{stage.timestampLabel}</span>
              <span role="cell">{view.validationRun.runId}</span>
              <span role="cell">Preserve evidence</span>
            </div>
          ))}
        </div>
        {view.validationRun.openBlockers.map((blocker) => (
          <article key={blocker.blockerId} className="status-card status-card--offline" role="alert">
            <h3>{blocker.severity} Â· {blocker.reasonClass}</h3>
            <p>{blocker.summary}</p>
            <p>Owner: {blocker.owner} Â· Status: {blocker.status}</p>
          </article>
        ))}
        <ChipList label="Evidence records" values={view.validationRun.evidencePaths.map(businessFacingText)} />
      </section>

      <section className="panel" aria-labelledby="readiness-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Readiness</p>
            <h2 id="readiness-heading">Setup readiness board</h2>
          </div>
          <span className="trace-badge">business readiness: {businessFacingText(view.readiness.readinessStatus)}</span>
        </div>
        <button type="button" disabled={view.readiness.deploymentDisabled} aria-disabled={view.readiness.deploymentDisabled}>
          Approve for rollout
        </button>
        {view.readiness.deploymentDisabled ? <p role="status">Live action is unavailable until business readiness items are resolved.</p> : null}
        <ChipList label="Readiness items needing attention" values={view.readiness.blockList.map(businessFacingText)} />
        <ChipList label="Owner reviews" values={view.readiness.signoffRefs.map(businessFacingText)} />
        <ChipList label="Setup checks" values={view.readiness.dependencyChecks.map(businessFacingText)} />
        <p>Review package completeness: {businessFacingText(view.readiness.evidenceSetCompleteness)}</p>
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
              <h3>{businessFacingText(metric.metricName)} Â· {metric.severity}</h3>
              <p>{metric.deviationLabel}</p>
            </li>
          ))}
        </ul>
      </section>

      <section className="panel" aria-labelledby="incident-replay-heading">
        <h2 id="incident-replay-heading">Incidents and processing review</h2>
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
          <strong>Processing review blocked</strong>
          <span>{view.replay.blockedReason}</span>
        </div>
        <ChipList label="Processing review modes" values={view.replay.replayModes} />
      </section>

      <section className="panel" aria-labelledby="contracts-heading">
        <h2 id="contracts-heading">Contract conformance and evidence export</h2>
        <ul className="offer-list" aria-label="Contract conformance checks">
          {view.contracts.map((contract) => (
            <li key={contract.contractId}>
              <h3>{contract.contractId} Â· {contract.status}</h3>
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
  const artifacts = view.artifacts ?? [];
  const decisions = view.decisions ?? [];
  const advisoryReviews = view.advisoryReviews ?? [];
  const fairLendingMonitoring = view.fairLendingMonitoring ?? [];
  const privacyRequests = view.privacyRequests ?? [];
  const securityEvents = view.securityEvents ?? [];
  const alerts = view.alerts ?? [];
  const retentionControls = view.retentionControls ?? [];
  const configurationGaps = view.configurationGaps ?? [];
  return (
    <>
      <section className="hero hero--compliance" aria-labelledby="compliance-title">
        <p className="eyebrow">Compliance Â· SEC-S01 / SEC-S07</p>
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
          {artifacts.map((artifact) => (
            <div key={artifact.artifactId} role="row" className="quote-table__row">
              <span role="cell">{artifact.artifactId} ({artifact.artifactType})</span>
              <span role="cell">{businessFacingText(artifact.policyVersion)} / {artifact.jurisdictionCode}</span>
              <span role="cell">{businessFacingText(artifact.continuityStatus)}</span>
              <span role="cell">{businessFacingText(artifact.retentionClass)}</span>
              <span role="cell">Available to support</span>
            </div>
          ))}
        </div>
        {artifacts.map((artifact) => (
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
          {decisions.map((decision) => (
            <article key={decision.decisionId} className="status-card status-card--offline">
              <h3>{businessFacingText(decision.reasonCode)}</h3>
              <p>{decision.humanText}</p>
              <p>Jurisdiction: {decision.jurisdictionCode}</p>
              <p>Disclosure record: {businessFacingText(decision.disclosureArtifactRef)}</p>
              <ChipList label="Reason categories" values={decision.reasonTiers.map(businessFacingText)} />
            </article>
          ))}
          {privacyRequests.map((request) => (
            <article key={request.requestId} className="status-card status-card--offline">
              <h3>Privacy request {request.requestId}</h3>
              <p>Borrower: {request.borrowerRef}</p>
              <p>Identity: {businessFacingText(request.identityStatus)}</p>
              <p>Response target: {businessFacingText(request.slaState)}</p>
              <p>Consent review history: {businessFacingText(request.consentAuditRef)}</p>
              <ChipList label="Privacy blockers" values={request.blockers.map(businessFacingText)} />
            </article>
          ))}
        </div>
      </section>

      <section className="panel" aria-labelledby="advisory-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Advisory findings</p>
            <h2 id="advisory-heading">Quote and portfolio compliance review</h2>
          </div>
          <DiagnosticsDetails items={configurationGaps.map(businessFacingText)} />
        </div>
        <div className="partner-detail-grid">
          {advisoryReviews.map((review) => (
            <article key={review.reviewId} className={review.blockedByConfiguration ? 'status-card status-card--offline' : 'status-card'} role={review.blockedByConfiguration ? 'alert' : undefined}>
              <h3>{businessFacingText(review.reviewType)} review Â· {businessFacingText(review.status)}</h3>
              <p>Subject: {businessFacingText(review.subjectRef)}</p>
              <p>Regulatory approval: {businessFacingText(review.regulatoryApprovalState)}</p>
              <p>Review snapshots: {review.auditSnapshotRefs.map(businessFacingText).join(', ')}</p>
              <p>Export refs: {review.exportRefs.map(businessFacingText).join(', ')}</p>
              <ChipList label="Reason codes" values={review.reasonCodes.map(businessFacingText)} />
              <ChipList label="Review snapshots" values={review.auditSnapshotRefs.map(businessFacingText)} />
              <ChipList label="Export refs" values={review.exportRefs.map(businessFacingText)} />
              <ChipList label="Blocked configuration gaps" values={review.configurationGaps.map(businessFacingText)} />
            </article>
          ))}
        </div>
      </section>

      <section className="panel" aria-labelledby="fair-lending-heading">
        <h2 id="fair-lending-heading">Fair-lending monitoring drilldown</h2>
        <div className="partner-detail-grid">
          {fairLendingMonitoring.map((drilldown) => (
            <article key={drilldown.drilldownId} className="status-card status-card--offline">
              <h3>{businessFacingText(drilldown.drilldownId)}</h3>
              <p>Redaction state: {businessFacingText(drilldown.redactionState)}</p>
              <p>Protected attributes: {drilldown.redacted ? 'Backend redacted' : 'Backend authorized'}</p>
              <ChipList label="Monitoring dimensions" values={drilldown.dimensions.map(businessFacingText)} />
              <ChipList label="Monitoring evidence refs" values={drilldown.evidenceRefs.map(businessFacingText)} />
              <ChipList label="Monitoring blockers" values={drilldown.blockers.map(businessFacingText)} />
            </article>
          ))}
        </div>
      </section>

      <section className="panel" aria-labelledby="security-alerts-heading">
        <h2 id="security-alerts-heading">Security events, alerts, and retention</h2>
        <div className="partner-detail-grid">
          {securityEvents.map((event) => (
            <article key={event.eventId} className="status-card status-card--offline">
              <h3>{event.category} Â· {event.severity}</h3>
              <p>Security record: {businessFacingText(event.logRecordId)}</p>
              <p>Owner: {event.owner}</p>
              <p>Acknowledged: {event.acknowledged ? 'yes' : 'no'}</p>
              <ChipList label="Security blockers" values={event.blockers.map(businessFacingText)} />
            </article>
          ))}
          {alerts.map((alert) => (
            <article key={alert.alertId} className="status-card status-card--offline">
              <h3>{alert.alertId}</h3>
              <p>{alert.severity} Â· {businessFacingText(alert.alertClass)} Â· {businessFacingText(alert.triggerType)}</p>
              <p>Alert destination: {businessFacingText(alert.routeTarget)}</p>
              <ChipList label="Alert blockers" values={alert.blockers.map(businessFacingText)} />
            </article>
          ))}
          {retentionControls.map((control) => (
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
  const { caseId } = useParams();
  const navigate = useNavigate();
  const [caseState, setCaseState] = useState<OpsCaseState>({ kind: 'loading' });
  const [selectedCaseId, setSelectedCaseId] = useState<string | null>(() => caseId ? decodeURIComponent(caseId) : null);
  const [detailState, setDetailState] = useState<OpsCaseDetailState>({ kind: 'idle' });
  const [owner, setOwner] = useState('');
  const [note, setNote] = useState('');
  const [escalationReason, setEscalationReason] = useState('');
  const [resolutionCode, setResolutionCode] = useState('');
  const [actionResult, setActionResult] = useState<OpsCaseActionResult | null>(null);

  useEffect(() => {
    if (caseId) setSelectedCaseId(decodeURIComponent(caseId));
  }, [caseId]);

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
    navigate(`/ops/cases/${encodeURIComponent(caseId)}`, { state: { caseId, screenId: 'OPS-S02' } });
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
        <p className="eyebrow">Operations Â· OPS-S01 / OPS-S02 / OPS-S03</p>
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
  const replayEvidenceRefs = Array.isArray(view.replayEvidence)
    ? view.replayEvidence
    : [...view.replayEvidence.versionRefs, ...view.replayEvidence.missingDependencyBlockers];
  return (
    <>
      <section className="hero hero--rate-feed" aria-labelledby="rate-feed-title">
        <p className="eyebrow">Rate operations Â· PII-22-S03</p>
        <h2 id="rate-feed-title">Rate feed operations</h2>
        <p>
          Track upload, parse, validation, activation, rejection, processing records, and cache evidence from configured rate feed
          facts. The workbench shows row blockers and source references without recalculating rates.
        </p>
      </section>

      <section className="panel" aria-labelledby="rate-feed-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Workflow cockpit</p>
            <h2 id="rate-feed-heading">Upload-to-processing workflow</h2>
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
              <dl>
                <dt>Source system</dt><dd>{businessFacingText(step.sourceBoundary)}</dd>
                <dt>Review reference</dt><dd>{businessFacingText(step.auditRef)}</dd>
                <dt>Processing record</dt><dd>{businessFacingText(step.resultHashRef)}</dd>
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
        <h2 id="rate-evidence-heading">Activation, cache invalidation, and processing evidence</h2>
        <button type="button" disabled={view.actionsDisabled} aria-describedby="rate-feed-action-blockers">Publish rate sheet</button>
        <button type="button" className="button-secondary" disabled={view.actionsDisabled}>Request processing review</button>
        <div id="rate-feed-action-blockers">
          <ChipList label="Processing and cache evidence" values={replayEvidenceRefs.map(businessFacingText)} />
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
        <p className="eyebrow">Observability Â· PII-22-S09</p>
        <h2 id="performance-title">Service performance cockpit</h2>
        <p>
          Group performance, cache, backpressure, and load-test evidence by service, workspace, support reference, and
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
        <dt>Support reference</dt><dd>{businessFacingText(group.correlationId)}</dd>
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
        <p className="eyebrow">Partner Â· CH-S01 / CH-S02 / CH-S03 / CH-S04</p>
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
  const [workbenchState, setWorkbenchState] = useState<PartnerChannelWorkbenchState>({ kind: 'loading' });
  const [alertsState, setAlertsState] = useState<PartnerIntegrationAlertsState>({ kind: 'loading' });
  const [selectedAttempt, setSelectedAttempt] = useState<PartnerWebhookDeliveryAttempt | null>(null);
  const [correlationId, setCorrelationId] = useState('');
  const [idempotencyConfirmed, setIdempotencyConfirmed] = useState(false);
  const [safetyConfirmed, setSafetyConfirmed] = useState(false);
  const [replayResult, setReplayResult] = useState<PartnerWebhookActionResult | null>(null);
  const [endpointTestResult, setEndpointTestResult] = useState<PartnerWebhookActionResult | null>(null);
  const [safetyResult, setSafetyResult] = useState<PartnerSafetyToggleResult | null>(null);
  const [alertActionResult, setAlertActionResult] = useState<PartnerIntegrationAlertActionResult | null>(null);

  useEffect(() => {
    let active = true;
    setWorkbenchState({ kind: 'loading' });
    fetchPartnerChannelWorkbench(partnerId)
      .then((view) => {
        if (active) setWorkbenchState({ kind: 'loaded', view });
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'Partner integration workbench is unavailable.';
        if (active) setWorkbenchState({ kind: 'unreachable', message });
      });
    return () => {
      active = false;
    };
  }, [partnerId]);

  useEffect(() => {
    let active = true;
    setAlertsState({ kind: 'loading' });
    fetchPartnerIntegrationAlerts(partnerId)
      .then((view) => {
        if (active) setAlertsState({ kind: 'loaded', view });
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'Partner integration alerts require a configured connected service contract.';
        if (active) setAlertsState({ kind: 'unreachable', message });
      });
    return () => {
      active = false;
    };
  }, [partnerId]);

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
    if (!selectedAttempt || !view.endpointTestAction.available) return;
    const result = await requestPartnerWebhookEndpointTest(partnerId, selectedAttempt.webhookId);
    setEndpointTestResult(result);
  }

  async function toggleSafety(route: string, webhookId: string, paused: boolean) {
    if (!safetyConfirmed) return;
    const result = await requestPartnerWebhookSafetyToggle(partnerId, webhookId, route, !paused, safetyConfirmed);
    setSafetyResult(result);
  }

  async function recordAlertAction(alert: PartnerIntegrationAlert, action: 'ACKNOWLEDGE' | 'INVESTIGATE' | 'RESOLVE') {
    const result = await requestPartnerIntegrationAlertAction(partnerId, alert.alertId, action);
    setAlertActionResult(result);
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
        <p className="eyebrow">Partner Â· integration-service</p>
        <h2 id="partner-transport-title">Partner Integrations</h2>
        <p>
          Inspect quote requests, message delivery, retry queues, exception queues, investor delivery connections, partner file delivery, service account access, and health through the approved workbench service only.
        </p>
        <DiagnosticsDetails items={[`Routes: ${partnerIntegrationRoutes.join(', ')}`, `States: ${partnerIntegrationStateCoverage.join(', ')}`, `Evidence target: ${partnerIntegrationEvidenceTarget}`]} />
      </section>

      <section className="panel" aria-label="Partner integration tabs">
        <div className="button-row" role="tablist" aria-label="Partner integration workbench tabs">
          <span role="tab" aria-selected="true">Webhook Health</span>
          <span role="tab" aria-selected="false">Channel Workbench</span>
          <span role="tab" aria-selected="false">Safety</span>
          <span role="tab" aria-selected="false">Alerts</span>
        </div>
      </section>

      <PartnerChannelWorkbenchPanel state={workbenchState} />

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
            <span role="columnheader">Webhook</span>
            <span role="columnheader">Event</span>
            <span role="columnheader">Route</span>
            <span role="columnheader">Status</span>
            <span role="columnheader">Root cause</span>
            <span role="columnheader">Last success</span>
            <span role="columnheader">Idempotency</span>
            <span role="columnheader">Masking</span>
            <span role="columnheader">Consent</span>
            <span role="columnheader">Action</span>
          </div>
          {view.deliveryAttempts.map((attempt) => (
            <div key={attempt.eventId} role="row" className={selectedAttempt?.eventId === attempt.eventId ? 'quote-table__row quote-table__row--selected' : 'quote-table__row'}>
              <span role="cell">{attempt.webhookId}</span>
              <span role="cell">{attempt.eventId}</span>
              <span role="cell">{attempt.route}</span>
              <span role="cell">{attempt.status}</span>
              <span role="cell">{businessFacingText(attempt.rootCauseCode)}</span>
              <span role="cell">{attempt.lastSuccessfulAt}</span>
              <span role="cell">{businessFacingText(attempt.idempotencyKeyState)}</span>
              <span role="cell">{businessFacingText(attempt.maskingIndicator)}</span>
              <span role="cell">{businessFacingText(attempt.consentIndicator)}</span>
              <span role="cell"><button type="button" onClick={() => setSelectedAttempt(attempt)}>Review controls</button></span>
            </div>
          ))}
        </div>
      </section>

      <section className="panel" aria-labelledby="webhook-controls-heading">
        <h2 id="webhook-controls-heading">Processing retry and endpoint controls</h2>
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
              <label htmlFor="webhook-correlation">Processing support reference</label>
              <input id="webhook-correlation" value={correlationId} onChange={(event) => setCorrelationId(event.target.value)} />
              <p className="field-help">{businessFacingText(view.replayAction.confirmationRequirement)}</p>
            </div>
            <label className="checkbox-row">
              <input type="checkbox" checked={idempotencyConfirmed} onChange={(event) => setIdempotencyConfirmed(event.target.checked)} />
              I confirm duplicate protection for this processing request.
            </label>
            <button type="button" disabled={replayDisabled} onClick={() => void requestReplay()}>Request processing retry</button>
            {replayDisabled ? <p role="status">{businessFacingText(view.replayAction.disabledReason)}</p> : null}
            <button type="button" className="button-secondary" disabled={!view.endpointTestAction.available} onClick={() => void requestEndpointTest()}>Show endpoint test guidance</button>
            {!view.endpointTestAction.available ? <p role="status">{businessFacingText(view.endpointTestAction.disabledReason)}</p> : null}
          </div>
        ) : <p role="status">No webhook events are available.</p>}
        {replayResult ? <TransportResultBanner result={replayResult} acceptedLabel="Webhook processing retry requested" blockedLabel="Webhook processing retry blocked" /> : null}
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
        {workbenchState.kind === 'loaded' ? <ServiceAccountBlockedPanel view={workbenchState.view} /> : null}
      </section>

      <PartnerIntegrationAlertsPanel state={alertsState} actionResult={alertActionResult} onAction={recordAlertAction} />
    </>
  );
}

function ServiceAccountBlockedPanel({ view }: { view: PartnerChannelWorkbenchView }) {
  return (
    <div className={view.serviceAccount.blocked ? 'banner banner--blocked' : 'banner banner--success'} role={view.serviceAccount.blocked ? 'alert' : 'status'}>
      <strong>{view.serviceAccount.blocked ? 'Service account blocked' : 'Service account ready'}</strong>
      <span>Missing capability: {view.serviceAccount.missingCapability}</span>
      <span>Recovery owner: {view.serviceAccount.recoveryOwner}</span>
      <span>Credential exposure: {view.serviceAccount.credentialExposure}</span>
    </div>
  );
}

function PartnerIntegrationAlertsPanel({ state, actionResult, onAction }: { state: PartnerIntegrationAlertsState; actionResult: PartnerIntegrationAlertActionResult | null; onAction: (alert: PartnerIntegrationAlert, action: 'ACKNOWLEDGE' | 'INVESTIGATE' | 'RESOLVE') => void }) {
  if (state.kind === 'loading') {
    return <section className="panel" aria-labelledby="partner-alerts-heading"><h2 id="partner-alerts-heading">Alerts</h2><p role="status">Loading partner integration alerts...</p></section>;
  }

  if (state.kind === 'unreachable') {
    return (
      <section className="panel" aria-labelledby="partner-alerts-heading">
        <h2 id="partner-alerts-heading">Alerts</h2>
        <div className="banner banner--blocked" role="alert">{state.message}</div>
        <p className="field-help">Backend alert rule thresholds and live alert actions remain blocked until the partner integration alert contract is configured.</p>
      </section>
    );
  }

  const view = state.view;
  return (
    <section className="panel" aria-labelledby="partner-alerts-heading">
      <div className="panel-heading-row">
        <div>
          <p className="eyebrow">Partner operations</p>
          <h2 id="partner-alerts-heading">Alerts</h2>
        </div>
        <DiagnosticsDetails items={[`Workspace ${view.tenantContext}`, `Support reference: ${view.uiTraceId}`, `Rules: ${businessFacingText(view.rulesStatus)}`]} />
      </div>
      <p className="field-help">{view.fallbackReason}</p>
      <dl className="status-grid">
        <dt>Dependency state</dt><dd>{businessFacingText(view.dependencyStatus)}</dd>
        <dt>Alert rules</dt><dd>{businessFacingText(view.rulesStatus)}</dd>
      </dl>
      <div className="quote-table" role="table" aria-label="Partner integration alerts">
        <div role="row" className="quote-table__row quote-table__row--head">
          <span role="columnheader">Alert</span>
          <span role="columnheader">Severity</span>
          <span role="columnheader">Class</span>
          <span role="columnheader">Trigger</span>
          <span role="columnheader">Route</span>
          <span role="columnheader">Action state</span>
          <span role="columnheader">Recovery owner</span>
          <span role="columnheader">Actions</span>
          <span role="columnheader">Blockers</span>
        </div>
        {view.alerts.map((alert) => (
          <div role="row" className="quote-table__row" key={alert.alertId}>
            <span role="cell">{alert.alertId}</span>
            <span role="cell">{alert.severity}</span>
            <span role="cell">{businessFacingText(alert.alertClass)}</span>
            <span role="cell">{businessFacingText(alert.triggerType)}</span>
            <span role="cell">{alert.routeTarget}</span>
            <span role="cell">{alert.acknowledged ? 'Acknowledged' : businessFacingText(alert.actionState)}</span>
            <span role="cell">{alert.recoveryOwner}</span>
            <span role="cell" className="button-row">
              <button type="button" onClick={() => onAction(alert, 'ACKNOWLEDGE')}>Acknowledge</button>
              <button type="button" className="button-secondary" onClick={() => onAction(alert, 'INVESTIGATE')}>Investigate</button>
              <button type="button" className="button-secondary" onClick={() => onAction(alert, 'RESOLVE')}>Resolve</button>
            </span>
            <span role="cell"><ChipList label={`${alert.alertId} blockers`} values={alert.blockers.map(businessFacingText)} /></span>
          </div>
        ))}
      </div>
      {actionResult ? (
        <div className={actionResult.status === 'RECORDED' ? 'banner banner--success' : 'banner banner--blocked'} role={actionResult.status === 'RECORDED' ? 'status' : 'alert'}>
          <strong>{actionResult.status === 'RECORDED' ? 'Alert action recorded' : 'Alert action blocked'}</strong>
          <span>{businessFacingText(actionResult.action)} for {actionResult.alertId}</span>
          <span>{actionResult.message}</span>
        </div>
      ) : null}
    </section>
  );
}

function PartnerChannelWorkbenchPanel({ state }: { state: PartnerChannelWorkbenchState }) {
  if (state.kind === 'loading') {
    return <section className="panel" aria-labelledby="partner-channel-heading"><h2 id="partner-channel-heading">Integration channel workbench</h2><p role="status">Loading integration channel state...</p></section>;
  }

  if (state.kind === 'unreachable') {
    return (
      <section className="panel" aria-labelledby="partner-channel-heading">
        <h2 id="partner-channel-heading">Integration channel workbench</h2>
        <div className="banner banner--blocked" role="alert">{state.message}</div>
      </section>
    );
  }

  const view = state.view;
  return (
    <section className="panel" aria-labelledby="partner-channel-heading">
      <div className="panel-heading-row">
        <div>
          <p className="eyebrow">Channel modules</p>
          <h2 id="partner-channel-heading">Integration channel workbench</h2>
        </div>
        <DiagnosticsDetails items={[`Workspace ${view.tenantContext}`, `Support reference: ${view.uiTraceId}`]} />
      </div>
      <p className="field-help">{view.fallbackReason}</p>
      <dl className="status-grid">
        <dt>Dependency state</dt><dd>{businessFacingText(view.dependencyStatus)}</dd>
        <dt>Missing service capability</dt><dd>{businessFacingText(view.serviceAccount.missingCapability)}</dd>
        <dt>Recovery owner</dt><dd>{view.serviceAccount.recoveryOwner}</dd>
        <dt>Credential exposure</dt><dd>{view.serviceAccount.credentialExposure}</dd>
      </dl>
      <div className="quote-table" role="table" aria-label="Integration channel tabs">
        <div role="row" className="quote-table__row quote-table__row--head">
          <span role="columnheader">Tab</span>
          <span role="columnheader">Route</span>
          <span role="columnheader">Status</span>
          <span role="columnheader">Recovery owner</span>
        </div>
        {view.tabs.map((tab) => (
          <div role="row" className="quote-table__row" key={tab.tabId}>
            <span role="cell">{tab.label}</span>
            <span role="cell">{tab.route}</span>
            <span role="cell">{businessFacingText(tab.status)}</span>
            <span role="cell">{tab.recoveryOwner}</span>
          </div>
        ))}
      </div>
      <div className="partner-detail-grid">
        {view.tabs.map((tab) => <PartnerChannelTabCard key={tab.tabId} tab={tab} />)}
      </div>
    </section>
  );
}

function PartnerChannelTabCard({ tab }: { tab: PartnerChannelWorkbenchTab }) {
  return (
    <article className="panel" aria-labelledby={`${tab.tabId}-heading`}>
      <h3 id={`${tab.tabId}-heading`}>{tab.label}</h3>
      <p className="field-help">{businessFacingText(tab.status)}</p>
      {tab.items.map((item) => (
        <dl key={item.itemId} className="status-grid">
          <dt>Item</dt><dd>{item.label}</dd>
          <dt>Retry state</dt><dd>{businessFacingText(item.retryState)}</dd>
          <dt>Exception queue reason</dt><dd>{businessFacingText(item.dlqReason)}</dd>
          <dt>Payload redaction</dt><dd>{businessFacingText(item.payloadRedactionState)}</dd>
          <dt>Review references</dt><dd>{item.auditRefs.join(', ')}</dd>
        </dl>
      ))}
    </article>
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
  const navigate = useNavigate();
  const { setPromotedActions } = usePageActions();
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
        navigate(result.statusRoute, { state: { runId, selectedOfferId, screenId: 'BRW-S05' } });
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

  useEffect(() => {
    if (lockState.kind !== 'loaded') {
      setPromotedActions(null);
      return () => setPromotedActions(null);
    }
    const workflow = lockState.workflow;
    const requestDisabled = workflow.lockDisabled || disclosuresAccepted;
    const headerConfirmDisabled = workflow.lockDisabled || !disclosuresAccepted || confirming;
    setPromotedActions({
      label: 'Lock workflow page actions',
      actions: (
        <div className="pm-actions" aria-label="Lock workflow actions">
          <button type="button" onClick={() => setDisclosuresAccepted(true)} disabled={requestDisabled}>Request lock</button>
          <button type="button" className="pm-primary" onClick={() => void submitLock()} disabled={headerConfirmDisabled}>{confirming ? 'Confirming lock...' : 'Confirm lock'}</button>
        </div>
      ),
    });
    return () => setPromotedActions(null);
  }, [lockState, disclosuresAccepted, confirming, setPromotedActions]);

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
        <p className="eyebrow">Borrower Â· BRW-S04</p>
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
            <div className="quote-table lock-table" role="table" aria-label="Lock items needing attention">
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
    <div className="lock-audit-groups" aria-label="Lock review evidence grouped by backend event ids">
      {groups.map((group) => (
        <article key={group.eventId} className="lock-audit-groups__card">
          <h4>{businessFacingText(group.label)}</h4>
          <dl>
            <dt>Event id</dt><dd>{group.eventId}</dd>
            <dt>Processing record</dt><dd>{businessFacingText(group.replayHash)}</dd>
            <dt>Export evidence</dt><dd>{businessFacingText(group.exportRef)}</dd>
          </dl>
          <ChipList label={`${group.eventId} evidence refs`} values={group.evidenceRefs.map(businessFacingText)} />
        </article>
      ))}
    </div>
  );
}

type OfferState =
  | { kind: 'loading' }
  | { kind: 'loaded'; comparison: OfferComparisonView }
  | { kind: 'unreachable'; message: string };

type QuoteDetailState =
  | { kind: 'loading' }
  | { kind: 'loaded'; detail: QuoteDetailView }
  | { kind: 'unreachable'; message: string };

type EligibilityState =
  | { kind: 'loading' }
  | { kind: 'loaded'; view: EligibilityModuleView }
  | { kind: 'unreachable'; message: string };

type PricingWaterfallState =
  | { kind: 'loading' }
  | { kind: 'loaded'; view: PricingWaterfallView }
  | { kind: 'unreachable'; message: string };

type QuoteJourneyMapState =
  | { kind: 'loading' }
  | { kind: 'loaded'; view: QuoteJourneyMapView }
  | { kind: 'unreachable'; message: string };

function QuoteJourneyMapSection({ runId }: { runId: string }) {
  const [journeyState, setJourneyState] = useState<QuoteJourneyMapState>({ kind: 'loading' });

  useEffect(() => {
    let active = true;
    fetchQuoteJourneyMap(tenantBoundaryPlaceholder, runId)
      .then((view) => {
        if (active) setJourneyState({ kind: 'loaded', view });
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'Quote journey map is unavailable.';
        if (active) setJourneyState({ kind: 'unreachable', message });
      });
    return () => {
      active = false;
    };
  }, [runId]);

  if (journeyState.kind === 'loading') {
    return <section className="panel" aria-labelledby="journey-heading"><h2 id="journey-heading">Quote journey map</h2><p role="status">Loading quote journey map...</p></section>;
  }

  if (journeyState.kind === 'unreachable') {
    return (
      <section className="panel" aria-labelledby="journey-heading">
        <h2 id="journey-heading">Quote journey map</h2>
        <div className="banner banner--blocked" role="alert">{journeyState.message}</div>
      </section>
    );
  }

  const view = journeyState.view;
  return (
    <>
      <section className="hero hero--admin" aria-labelledby="journey-title">
        <p className="eyebrow">Service UI Â· PII-22-S19</p>
        <h2 id="journey-title">Cross-service quote journey for run {runId}</h2>
        <p>
          Trace scenario facts through catalog, rate feed, eligibility, pricing, ranking, selection, lock, exception,
          compliance, review, and integration events using connected service references. Unavailable services stay visible as blocked nodes.
        </p>
      </section>

      <section className="panel" aria-labelledby="journey-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Journey boundary</p>
            <h2 id="journey-heading">Service node map</h2>
          </div>
          <DiagnosticsDetails items={[`Support reference: ${view.uiTraceId}`, `Dependency status: ${view.dependencyStatus}`]} />
        </div>

        <div className="banner banner--blocked" role="alert">
          <strong>{businessFacingText(view.status)}</strong>
          <span>{businessFacingText(view.fallbackReason)}</span>
        </div>
        <ChipList label="Journey blockers" values={view.blockers.map(businessFacingText)} />
        <ChipList label="Service contracts represented" values={view.serviceContracts.map(businessFacingText)} />

        <div className="offer-grid" role="list" aria-label="Quote journey service nodes">
          {view.nodes.map((node) => <QuoteJourneyNodeCard key={node.nodeId} node={node} />)}
        </div>
      </section>
    </>
  );
}

function QuoteJourneyNodeCard({ node }: { node: QuoteJourneyNode }) {
  const blocked = node.status.toLowerCase().includes('blocked') || node.status.toLowerCase().includes('unavailable');
  return (
    <article className={blocked ? 'offer-card offer-card--selected' : 'offer-card'} role="listitem" aria-label={`${node.label} ${node.status}`}>
      <div className="offer-card__header">
        <div>
          <p className="eyebrow">{businessFacingText(node.serviceName)}</p>
          <h3>{businessFacingText(node.label)}</h3>
        </div>
        <strong>{businessFacingText(node.status)}</strong>
      </div>
      <dl className="status-grid">
        <dt>Freshness</dt><dd>{businessFacingText(node.freshness.status)}</dd>
        <dt>Freshness ref</dt><dd>{businessFacingText(node.freshness.evidenceRef)}</dd>
        <dt>Processing record</dt><dd>{businessFacingText(node.replayHash)}</dd>
        <dt>Drilldown route</dt><dd><a href={journeyDrilldownHref(node)}>{node.drilldownRoute}</a></dd>
        <dt>Run ref</dt><dd>{businessFacingText(node.drilldownRefs.runId)}</dd>
        <dt>Scenario ref</dt><dd>{businessFacingText(node.drilldownRefs.scenarioRef)}</dd>
        <dt>Quote ref</dt><dd>{businessFacingText(node.drilldownRefs.quoteRef)}</dd>
        <dt>Lock ref</dt><dd>{businessFacingText(node.drilldownRefs.lockRef)}</dd>
        <dt>Support reference</dt><dd>{businessFacingText(node.drilldownRefs.correlationRef)}</dd>
      </dl>
      <p className="field-help">{businessFacingText(node.freshness.message)}</p>
      <ChipList label={`${node.label} evidence refs`} values={node.evidenceRefs.map(businessFacingText)} />
      <ChipList label={`${node.label} blockers`} values={node.blockers.map(businessFacingText)} />
      <ChipList label={`${node.label} downstream dependencies`} values={node.downstreamDependencies.map(businessFacingText)} />
    </article>
  );
}

function safeJourneyRoute(route: string) {
  return route.startsWith('/') && !route.startsWith('//') ? route : '#';
}

function journeyDrilldownHref(node: QuoteJourneyNode) {
  const route = safeJourneyRoute(node.drilldownRoute);
  if (route === '#') return route;
  const params = new URLSearchParams({
    runId: node.drilldownRefs.runId,
    scenarioRef: node.drilldownRefs.scenarioRef,
    quoteRef: node.drilldownRefs.quoteRef,
    lockRef: node.drilldownRefs.lockRef,
    correlationRef: node.drilldownRefs.correlationRef,
  });
  return `${route}?${params.toString()}`;
}

function PricingWaterfallSection({ runId }: { runId: string }) {
  const [waterfallState, setWaterfallState] = useState<PricingWaterfallState>({ kind: 'loading' });

  useEffect(() => {
    let active = true;
    fetchPricingWaterfall(tenantBoundaryPlaceholder, runId)
      .then((view) => {
        if (active) setWaterfallState({ kind: 'loaded', view });
      })
      .catch(() => {
        if (active) setWaterfallState({ kind: 'unreachable', message: 'Pricing waterfall is temporarily unavailable.' });
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

  const view: Partial<PricingWaterfallView> = waterfallState.view;
  const baseSelection = view.baseSelection ?? {} as PricingWaterfallView['baseSelection'];
  const finalPrice = view.finalPrice ?? { finalPriceId: undefined, roundedFinalPrice: { value: null, redacted: false, reason: null }, ledger: [], roundingTraceRefs: [] } as unknown as PricingWaterfallView['finalPrice'];
  return (
    <>
      <section className="hero hero--admin" aria-labelledby="waterfall-title">
        <p className="eyebrow">Pricing Â· PII-22-S05</p>
        <h2 id="waterfall-title">Pricing waterfall for run {runId}</h2>
        <p>
          Inspect base grid selection, final price ledger steps, rounding review references, missing-price blockers, support references,
          and processing records from connected service records. The workbench displays facts only and does not calculate prices.
        </p>
      </section>

      <section className="panel" aria-labelledby="waterfall-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Connected pricing records</p>
            <h2 id="waterfall-heading">Waterfall evidence</h2>
          </div>
          <DiagnosticsDetails items={[`Support detail: ${businessFacingText(view.uiTraceId)}`, `Review record: ${businessFacingText(view.evidenceHash)}`]} />
        </div>

        <div className="banner banner--blocked" role="alert">
          <strong>{businessFacingText(view.status)}</strong>
          <span>{businessFacingText(view.fallbackReason)}</span>
          <span>Restricted values visible: {view.restrictedValuesVisible ? 'yes' : 'no'}</span>
        </div>

        <dl className="status-grid">
          <dt>Base selection</dt><dd>{businessFacingText(baseSelection.selectionId)}</dd>
          <dt>Grid version</dt><dd>{businessFacingText(baseSelection.gridVersionRef)}</dd>
          <dt>Selected note rate</dt><dd>{waterfallValueText(baseSelection.selectedNoteRate)}</dd>
          <dt>Base price</dt><dd>{waterfallValueText(baseSelection.basePrice)}</dd>
          <dt>Final price</dt><dd>{businessFacingText(finalPrice.finalPriceId)}</dd>
          <dt>Rounded final price</dt><dd>{waterfallValueText(finalPrice.roundedFinalPrice)}</dd>
          <dt>Result record</dt><dd>{businessFacingText(view.resultHash)}</dd>
          <dt>Processing record</dt><dd>{businessFacingText(view.replayHash)}</dd>
        </dl>

        <ChipList label="Version records" values={(view.versionRefs ?? []).map(businessFacingText)} />
        <ChipList label="Review records" values={(view.auditRefs ?? []).map(businessFacingText)} />
        <ChipList label="Rounding review records" values={(finalPrice.roundingTraceRefs ?? []).map(businessFacingText)} />
      </section>

      <section className="panel" aria-labelledby="waterfall-ledger-heading">
        <h2 id="waterfall-ledger-heading">Final price ledger</h2>
        <div className="quote-table" role="table" aria-label="Pricing waterfall ledger">
          <div role="row" className="quote-table__row quote-table__row--head">
            <span role="columnheader">Step</span>
            <span role="columnheader">Input</span>
            <span role="columnheader">Operation</span>
            <span role="columnheader">Output</span>
            <span role="columnheader">Setup</span>
            <span role="columnheader">Reason</span>
          </div>
          {(finalPrice.ledger ?? []).map((row) => <WaterfallLedgerTableRow key={`${row.ordinal}-${row.step}`} row={row} />)}
        </div>
      </section>

      <section className="panel" aria-labelledby="waterfall-blockers-heading">
        <h2 id="waterfall-blockers-heading">Missing-price blockers and processing evidence</h2>
        <div className="offer-list" role="list" aria-label="Pricing waterfall blockers">
          {(view.blockers ?? []).map((blocker) => (
            <article key={blocker.code} className="banner banner--blocked" role="listitem">
              <strong>{businessFacingText(blocker.code)}</strong>
              <span>{businessFacingText(blocker.message)}</span>
              <span>Source: {businessFacingText(blocker.sourceRef)}</span>
            </article>
          ))}
        </div>
        <ChipList label="Waterfall events" values={(view.events ?? []).map(businessFacingText)} />
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
      <span role="cell">{businessFacingText(row.reasonCode)}{row.roundingMode ? ` Â· ${businessFacingText(row.roundingMode)}` : ''}</span>
    </div>
  );
}

function waterfallValueText(value: { value: string | null; redacted: boolean; reason: string | null } | undefined) {
  if (!value) return 'Not provided';
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
        <p className="eyebrow">Borrower Â· BRW-S04</p>
        <h2 id="eligibility-title">Eligibility explanation for run {runId}</h2>
        <p>
          Review connected eligibility decisions, reason codes, fact references, overlay references, cache freshness, and
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
  const navigate = useNavigate();
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
        const [firstSortOption] = comparison.sortOptions;
        if (firstSortOption) setSortKey(firstSortOption);
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
      navigate(result.nextRoute, { state: { runId, selectedOfferId: result.selectedOfferId, screenId: 'BRW-S04' } });
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
        <p className="eyebrow">Borrower Â· BRW-S02 / BRW-S03</p>
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
            {(comparison.sortOptions.length > 0 ? comparison.sortOptions : ['rank']).map((option) => <option key={option} value={option}>{option}</option>)}
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
          <div className="offer-grid" role="list" aria-label="Offer cards">
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
                <ChipList label="Service setup details" values={(offer.upstreamRefs ?? []).map(businessFacingText)} />
                <ChipList label="Lock eligibility refs" values={offer.lockEligibilityRefs ?? []} />
                <ChipList label="Snapshot refs" values={offer.snapshotRefs ?? []} />
                <ChipList label="Review references" values={(offer.auditIds ?? []).map(businessFacingText)} />
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
              <ChipList label="Explanation review references" values={(explanation.auditIds ?? []).map(businessFacingText)} />
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

function QuoteDetailSection({ runId, offerId }: { runId: string; offerId: string }) {
  const [detailState, setDetailState] = useState<QuoteDetailState>({ kind: 'loading' });

  useEffect(() => {
    let active = true;
    fetchQuoteDetail(tenantBoundaryPlaceholder, runId, offerId)
      .then((detail) => {
        if (active) setDetailState({ kind: 'loaded', detail });
      })
      .catch(() => {
        if (active) setDetailState({ kind: 'unreachable', message: 'Quote detail is temporarily unavailable.' });
      });
    return () => {
      active = false;
    };
  }, [runId, offerId]);

  if (detailState.kind === 'loading') {
    return <section className="panel" aria-labelledby="quote-detail-heading"><h2 id="quote-detail-heading">Quote detail</h2><p role="status">Loading quote detail evidence...</p></section>;
  }

  if (detailState.kind === 'unreachable') {
    return (
      <section className="panel" aria-labelledby="quote-detail-heading">
        <h2 id="quote-detail-heading">Quote detail</h2>
        <div className="banner banner--blocked" role="alert">{detailState.message}</div>
      </section>
    );
  }

  const detail: Partial<QuoteDetailView> = detailState.detail;
  const summary = detail.summary ?? {} as QuoteDetailView['summary'];
  const detailWaterfall = detail.waterfall ?? {} as QuoteDetailView['waterfall'];
  const detailBaseSelection = detailWaterfall.baseSelection ?? {} as QuoteDetailView['waterfall']['baseSelection'];
  const detailFinalPrice = detailWaterfall.finalPrice ?? { ledger: [], roundingTraceRefs: [], roundedFinalPrice: { value: null, redacted: false, reason: null } } as unknown as QuoteDetailView['waterfall']['finalPrice'];
  return (
    <>
      <section className="hero hero--admin" aria-labelledby="quote-detail-title">
        <p className="eyebrow">Quote detail Â· PII-22-S23</p>
        <h2 id="quote-detail-title">Quote detail for {businessFacingText(detail.offerId)}</h2>
        <p>
          Review card summary, ranking, pricing waterfall references, redactions, compliance flags, and review/processing evidence from
          configured service facts. The workbench does not calculate note rate, final price, margin, or adjustments.
        </p>
      </section>

      <section className="panel" aria-labelledby="quote-detail-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Connected quote details</p>
            <h2 id="quote-detail-heading">Quote detail review panels</h2>
          </div>
          <DiagnosticsDetails items={[`Support detail: ${businessFacingText(detail.uiTraceId)}`, `Review record: ${businessFacingText(detail.evidenceHash)}`]} />
        </div>
        <div className="banner banner--blocked" role="alert">
          <strong>{businessFacingText(detail.status)}</strong>
          <span>{businessFacingText(detail.fallbackReason)}</span>
        </div>
        <dl className="status-grid">
          <dt>Product</dt><dd>{valueText(summary.productLabel)}</dd>
          <dt>Rank</dt><dd>{valueText(summary.rank)}</dd>
          <dt>Rank score</dt><dd>{valueText(summary.rankScore)}</dd>
          <dt>Scenario version</dt><dd>{valueText(summary.scenarioVersion)}</dd>
          <dt>Processing record</dt><dd>{businessFacingText(detail.replayHash)}</dd>
          <dt>Waterfall record</dt><dd>{businessFacingText(detailWaterfall.evidenceHash)}</dd>
        </dl>
        <ChipList label="Quote detail review records" values={(detail.auditRefs ?? []).map(businessFacingText)} />
        <ChipList label="Quote detail compliance flags" values={(detail.complianceFlags ?? []).map(businessFacingText)} />
      </section>

      <section className="panel" aria-labelledby="quote-detail-panels-heading">
        <h2 id="quote-detail-panels-heading">Accessible detail panels</h2>
        <div className="module-rail__grid" role="list" aria-label="Quote detail review panels">
          {(detail.panels ?? []).map((panel) => {
            const panelLabel = businessFacingText(panel.label);
            return (
              <article key={panel.panelId} className="module-card" role="listitem" aria-label={`${panelLabel} panel`}>
                <p className="module-card__route">{businessFacingText(panel.status)}</p>
                <strong className="module-card__title">{panelLabel}</strong>
                <ChipList label={`${panelLabel} fields`} values={panel.fields.map(businessFacingText)} />
                <ChipList label={`${panelLabel} connected service references`} values={panel.backendRefs.map(businessFacingText)} />
                <ChipList label={`${panelLabel} items needing attention`} values={panel.blockers.map(businessFacingText)} />
              </article>
            );
          })}
        </div>
      </section>

      <section className="panel" aria-labelledby="quote-detail-redactions-heading">
        <h2 id="quote-detail-redactions-heading">Restricted and unavailable fields</h2>
        <div className="quote-table" role="table" aria-label="Quote detail redactions">
          <div role="row" className="quote-table__row quote-table__row--head">
            <span role="columnheader">Field</span>
            <span role="columnheader">State</span>
            <span role="columnheader">Why unavailable</span>
            <span role="columnheader">Review reference</span>
          </div>
          {(detail.redactions ?? []).map((redaction) => (
            <div key={`${redaction.fieldPath}-${redaction.auditRef}`} role="row" className="quote-table__row">
              <span role="cell">{businessFacingText(redaction.fieldPath)}</span>
              <span role="cell">{businessFacingText(redaction.state)}</span>
              <span role="cell">{businessFacingText(redaction.reason)}</span>
              <span role="cell">{businessFacingText(redaction.auditRef)}</span>
            </div>
          ))}
        </div>
      </section>

      <section className="panel" aria-labelledby="quote-detail-waterfall-heading">
        <h2 id="quote-detail-waterfall-heading">Waterfall sections and rounding review</h2>
        <dl className="status-grid">
          <dt>Base selection</dt><dd>{businessFacingText(detailBaseSelection.selectionId)}</dd>
          <dt>Selected note rate</dt><dd>{waterfallValueText(detailBaseSelection.selectedNoteRate)}</dd>
          <dt>Rounded final price</dt><dd>{waterfallValueText(detailFinalPrice.roundedFinalPrice)}</dd>
          <dt>Result record</dt><dd>{businessFacingText(detailWaterfall.resultHash)}</dd>
        </dl>
        <div className="quote-table" role="table" aria-label="Quote detail waterfall ledger">
          <div role="row" className="quote-table__row quote-table__row--head">
            <span role="columnheader">Step</span>
            <span role="columnheader">Input</span>
            <span role="columnheader">Operation</span>
            <span role="columnheader">Output</span>
            <span role="columnheader">Setup</span>
            <span role="columnheader">Reason</span>
          </div>
          {(detailFinalPrice.ledger ?? []).map((row) => <WaterfallLedgerTableRow key={`${row.ordinal}-${row.step}`} row={row} />)}
        </div>
        <ChipList label="Quote detail rounding review records" values={(detailFinalPrice.roundingTraceRefs ?? []).map(businessFacingText)} />
        <ChipList label="Quote detail events" values={(detail.events ?? []).map(businessFacingText)} />
      </section>
    </>
  );
}

function ChipList({ label, values }: { label: string; values: string[] }) {
  if (!values.length) return <p className="field-help">No {label.toLowerCase()} provided.</p>;
  return <ul className="chip-list" aria-label={label}>{values.map((value, index) => <li key={`${value}-${index}`}>{value}</li>)}</ul>;
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
  return businessFacingText(value);
}
