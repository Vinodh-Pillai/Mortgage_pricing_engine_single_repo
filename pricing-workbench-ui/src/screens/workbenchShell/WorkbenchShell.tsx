import type { ComponentType } from 'react';
import { fairLendingScreenModule } from '../fairLending';
import { scenarioAnalysisEvidenceTarget, scenarioAnalysisStateCoverage } from '../scenarioAnalysis';

export type WorkbenchScreenModule = {
  id: string;
  label: string;
  routePattern: string;
  breadcrumb: string;
  screenPackage: string;
  dataBoundary: string;
  stateCoverage: string[];
  personaVisibility?: string[];
  dependencyStatus?: string;
  adapterStatus?: string;
  evidenceTarget: string;
  match: (pathname: string) => boolean;
  Component?: ComponentType;
};

type RegistryCopy = {
  pathLabel: string;
  screenArea: string;
  sourceLabel: string;
  stateLabels: string[];
  evidenceLabel: string;
};

const defaultPersonaVisibility = ['loan officer', 'pricing analyst', 'operations lead', 'governance reviewer'];
const requiredVisualStateContract = ['loading', 'needs attention', 'ready'];
const configuredFallbackStatus = 'Visible with clear setup status; unavailable connected services show business-readable attention messages instead of hiding the capability.';

function uniqueValues(values: string[]) {
  return Array.from(new Set(values));
}

function personaVisibilityFor(screen: WorkbenchScreenModule) {
  return screen.personaVisibility?.length ? screen.personaVisibility : defaultPersonaVisibility;
}

function visualStateContractFor(screen: WorkbenchScreenModule) {
  return uniqueValues([...requiredVisualStateContract, ...screen.stateCoverage]);
}

function adapterStatusFor(screen: WorkbenchScreenModule) {
  return screen.adapterStatus ?? (screen.dataBoundary.startsWith('lib/api/') ? 'Connected work area is ready for setup review.' : 'Workbench screen is ready for setup review.');
}

function dependencyStatusFor(screen: WorkbenchScreenModule) {
  return screen.dependencyStatus ?? configuredFallbackStatus;
}

function registryCopyFor(screen: WorkbenchScreenModule): RegistryCopy {
  return {
    pathLabel: screen.routePattern.replace(':runId', 'a quote').replace(':optionId', 'a selected offer'),
    screenArea: `${screen.label} work area`,
    sourceLabel: `Guides ${screen.label.toLowerCase()} activity`,
    stateLabels: visualStateContractFor(screen).map((state) => state.replace(/load-state|loading/i, 'loading').replace(/blocked|unavailable-contract|blocked-evidence/i, 'needs attention').replace(/audit-evidence|replay-evidence|export-evidence|recommendation-evidence|floor-evidence|evidence-panels/i, 'review records').replace(/backend-recalculation/i, 'connected recalculation').replace(/dlq/i, 'exception queue').replace(/feed-adapters?/i, 'investor delivery connections').replace(/sftp-adapters?/i, 'partner file delivery').replace(/[._/-]+/g, ' ')),
    evidenceLabel: screen.evidenceTarget.includes('/quality/') ? 'Quality readiness review' : 'Product readiness review',
  };
}

export const workbenchModules: WorkbenchScreenModule[] = [
  {
    id: 'quote-intake',
    label: 'Pipeline',
    routePattern: '/pipeline',
    breadcrumb: 'Pipeline',
    screenPackage: 'screens/quoteIntake',
    dataBoundary: 'scenario-service draft APIs and quote-service launch APIs',
    stateCoverage: ['loading', 'empty', 'blocked', 'needs-attention', 'ready'],
    personaVisibility: ['loan-officer', 'borrower', 'pricing-analyst', 'admin'],
    evidenceTarget: '.local-harness/evidence/PII-25-S05/quote-intake.json',
    match: (pathname) => pathname === '/' || pathname.startsWith('/pipeline') || pathname.startsWith('/quote/start'),
  },
  {
    id: 'quote-offers',
    label: 'Offer comparison',
    routePattern: '/quote/:runId/offers',
    breadcrumb: 'Compare Offers',
    screenPackage: 'screens/quoteOffers',
    dataBoundary: 'lib/api/offers',
    stateCoverage: ['load-state', 'empty', 'blocked', 'ready'],
    evidenceTarget: '.local-harness/evidence/PII-22-S21/quote-offers.json',
    match: (pathname) => /^\/quote\/[^/]+\/offers\/?$/.test(pathname),
  },
  {
    id: 'quote-detail',
    label: 'Quote detail waterfall',
    routePattern: '/quote/:runId/offers/:optionId',
    breadcrumb: 'Quote Detail',
    screenPackage: 'screens/quoteDetail',
    dataBoundary: 'lib/api/offers.fetchQuoteDetail',
    stateCoverage: ['load-state', 'summary', 'ranking', 'waterfall', 'redacted', 'compliance', 'audit-replay'],
    evidenceTarget: '.local-harness/evidence/PII-22-S23/quote-detail-waterfall.json',
    match: (pathname) => /^\/quote\/[^/]+\/offers\/[^/]+/.test(pathname),
  },
  {
    id: 'pricing-waterfall',
    label: 'Pricing waterfall',
    routePattern: '/quote/:runId/pricing-waterfall',
    breadcrumb: 'Pricing Waterfall',
    screenPackage: 'screens/pricingWaterfall',
    dataBoundary: 'lib/api/quoteRuns.fetchPricingWaterfall',
    stateCoverage: ['load-state', 'blocked', 'redacted', 'ready', 'replay-evidence'],
    evidenceTarget: '.local-harness/evidence/PII-22-S05/pricing-waterfall.json',
    match: (pathname) => /^\/quote\/[^/]+\/pricing-waterfall/.test(pathname),
  },
  {
    id: 'quote-journey',
    label: 'Quote journey map',
    routePattern: '/quote/:runId/journey',
    breadcrumb: 'Quote Journey Map',
    screenPackage: 'screens/quoteJourneyMap',
    dataBoundary: 'lib/api/quoteRuns.fetchQuoteJourneyMap',
    stateCoverage: ['load-state', 'blocked', 'unavailable-contract', 'service-nodes', 'freshness', 'drilldown-refs'],
    evidenceTarget: '.local-harness/evidence/PII-22-S19/quote-journey-map.json',
    match: (pathname) => /^\/quote\/[^/]+\/journey/.test(pathname),
  },
  {
    id: 'scenario-analysis',
    label: 'Scenario analysis',
    routePattern: '/quote/:runId/what-if',
    breadcrumb: 'Scenario Analysis',
    screenPackage: 'screens/scenarioAnalysis',
    dataBoundary: 'lib/api/scenarioAnalysis',
    stateCoverage: [...scenarioAnalysisStateCoverage],
    evidenceTarget: scenarioAnalysisEvidenceTarget,
    match: (pathname) => /^\/quote\/[^/]+\/what-if/.test(pathname),
  },
  {
    id: 'quote-lock',
    label: 'Lock workflow',
    routePattern: '/quote/:runId/lock',
    breadcrumb: 'Lock Terms',
    screenPackage: 'screens/quoteLock',
    dataBoundary: 'lib/api/locks',
    stateCoverage: ['load-state', 'blocked', 'ready'],
    evidenceTarget: '.local-harness/evidence/PII-22-S21/quote-lock.json',
    match: (pathname) => /^\/quote\/[^/]+\/lock/.test(pathname),
  },
  {
    id: 'adjustment-evidence',
    label: 'Adjustment evidence',
    routePattern: '/pricing/adjustments',
    breadcrumb: 'Adjustment Evidence',
    screenPackage: 'screens/adjustmentEvidence',
    dataBoundary: 'lib/api/adjustments',
    stateCoverage: ['load-state', 'blocked', 'ids', 'fact-refs', 'conflicts', 'compensation-hooks', 'summaries'],
    evidenceTarget: '.local-harness/evidence/PII-24-S15/adjustment-evidence.json',
    match: (pathname) => pathname.startsWith('/pricing/adjustments'),
  },
  {
    id: 'margin-profitability',
    label: 'Margin profitability',
    routePattern: '/pricing/margins',
    breadcrumb: 'Margin Profitability',
    screenPackage: 'screens/marginProfitability',
    dataBoundary: 'lib/api/marginProfitability',
    stateCoverage: ['load-state', 'blocked', 'redacted', 'floor-evidence', 'approval', 'replay-evidence'],
    evidenceTarget: '.local-harness/evidence/PII-24-S16/margin-profitability.json',
    match: (pathname) => pathname.startsWith('/pricing/margins'),
  },
  {
    id: 'exception-concessions',
    label: 'Exception concessions',
    routePattern: '/exceptions/concessions',
    breadcrumb: 'Exception Concessions',
    screenPackage: 'screens/exceptionConcessions',
    dataBoundary: 'lib/api/exceptionConcessions',
    stateCoverage: ['load-state', 'concession-request', 'eligibility-exception', 'authority-matrix', 'manual-price-guard', 'risk-events', 'history-replay-export'],
    evidenceTarget: '.local-harness/evidence/PII-24-S17/exception-concessions.json',
    match: (pathname) => pathname.startsWith('/exceptions/concessions'),
  },
  {
    id: 'quote-eligibility',
    label: 'Eligibility explanation',
    routePattern: '/quote/:runId/eligibility',
    breadcrumb: 'Eligibility Explanation',
    screenPackage: 'screens/quoteEligibility',
    dataBoundary: 'lib/api/eligibility',
    stateCoverage: ['load-state', 'blocked', 'eligible', 'ineligible', 'conditional', 'explanation', 'cache-health'],
    evidenceTarget: '.local-harness/evidence/PII-22-S04/quote-eligibility.json',
    match: (pathname) => /^\/quote\/[^/]+\/eligibility/.test(pathname),
  },
  {
    id: 'partner-quotes',
    label: 'Partner quotes',
    routePattern: '/partners/quotes',
    breadcrumb: 'Partner Quotes',
    screenPackage: 'screens/partnerQuotes',
    dataBoundary: 'lib/api/partnerQuotes',
    stateCoverage: ['load-state', 'empty', 'blocked', 'ready'],
    evidenceTarget: '.local-harness/evidence/PII-24-S21/partner-quotes.json',
    match: (pathname) => pathname.startsWith('/partners/quotes'),
  },
  {
    id: 'partner-transport',
    label: 'Partner integrations',
    routePattern: '/partners/integrations',
    breadcrumb: 'Partner Integrations',
    screenPackage: 'screens/partnerIntegrations',
    dataBoundary: 'lib/api/partnerTransport',
    stateCoverage: ['load-state', 'quote-requests', 'webhook-delivery', 'retries', 'dlq', 'feed-adapters', 'sftp-adapters', 'health', 'blocked'],
    evidenceTarget: '.local-harness/evidence/PII-22-S12/partner-integration-workbench.json',
    match: (pathname) => pathname.startsWith('/partners/integrations') || pathname.startsWith('/partners/webhooks') || pathname.startsWith('/partners/admin/safety') || pathname.startsWith('/partners/alerts'),
  },
  {
    id: 'rate-feed-ops',
    label: 'Rate feed operations',
    routePattern: '/ops/rate-feeds',
    breadcrumb: 'Rate Feed Operations',
    screenPackage: 'screens/rateFeedOps',
    dataBoundary: 'lib/api/rateFeedOps',
    stateCoverage: ['load-state', 'workflow-steps', 'row-blockers', 'replay-evidence'],
    evidenceTarget: '.local-harness/evidence/PII-24-S18/rate-feed-ops.json',
    match: (pathname) => pathname.startsWith('/ops/rate-feeds'),
  },
  {
    id: 'performance-dashboard',
    label: 'Performance dashboard',
    routePattern: '/ops/performance',
    breadcrumb: 'Performance Dashboard',
    screenPackage: 'screens/observabilityPerformance',
    dataBoundary: 'lib/api/observabilityPerformance',
    stateCoverage: ['load-state', 'service-groups', 'freshness', 'blocked-evidence', 'recovery-owner'],
    evidenceTarget: '.local-harness/evidence/PII-24-S20/performance-dashboard.json',
    match: (pathname) => pathname.startsWith('/ops/performance'),
  },
  {
    id: 'ops-cases',
    label: 'Operations cases',
    routePattern: '/ops/dashboard',
    breadcrumb: 'Operations Cases',
    screenPackage: 'screens/opsCases',
    dataBoundary: 'lib/api/opsCases',
    stateCoverage: ['load-state', 'empty', 'blocked', 'ready'],
    evidenceTarget: '.local-harness/evidence/PII-24-S19/ops-cases.json',
    match: (pathname) => pathname.startsWith('/ops/dashboard') || pathname.startsWith('/ops/queues') || pathname.startsWith('/ops/cases') || pathname.startsWith('/ops/escalations'),
  },
  fairLendingScreenModule,
  {
    id: 'compliance-evidence',
    label: 'Compliance evidence',
    routePattern: '/compliance/evidence',
    breadcrumb: 'Compliance Evidence',
    screenPackage: 'screens/complianceEvidence',
    dataBoundary: 'lib/api/complianceEvidence',
    stateCoverage: ['load-state', 'empty', 'blocked', 'ready'],
    evidenceTarget: '.local-harness/evidence/PII-24-S28/compliance-evidence-registry.json',
    match: (pathname) => (pathname.startsWith('/compliance') && !pathname.startsWith('/compliance/fair-lending')) || pathname.startsWith('/privacy/requests') || pathname.startsWith('/security/events'),
  },
  {
    id: 'quality-dashboard',
    label: 'Quality guardrails',
    routePattern: '/quality/validation',
    breadcrumb: 'Quality Guardrails',
    screenPackage: 'screens/qualityDashboard',
    dataBoundary: 'lib/api/quality',
    stateCoverage: ['load-state', 'blocked', 'ready'],
    evidenceTarget: '.local-harness/evidence/PII-22-S21/quality-dashboard.json',
    match: (pathname) => pathname.startsWith('/quality/'),
  },
  {
    id: 'tenant-onboarding',
    label: 'Tenant Onboarding',
    routePattern: '/tenant/onboarding',
    breadcrumb: 'Tenant Onboarding',
    screenPackage: 'screens/tenant',
    dataBoundary: 'tenant-service tenant onboarding APIs',
    stateCoverage: ['loading', 'empty', 'blocked', 'needs-attention', 'ready'],
    personaVisibility: ['admin', 'operations-lead'],
    dependencyStatus: 'Tenant-service metadata is required for full launch. The screen shows blocked and attention states when refs are unavailable.',
    adapterStatus: 'Local UI renders progressive sections and evidence refs without inventing tenant values.',
    evidenceTarget: '.local-harness/evidence/PII-25-S04/tenant-onboarding.json',
    match: (pathname) => pathname.startsWith('/tenant/onboarding') || pathname.startsWith('/admin/tenants/new'),
  },
  {
    id: 'product-management',
    label: 'Product Management',
    routePattern: '/admin/products/catalog',
    breadcrumb: 'Product Management',
    screenPackage: 'screens/product',
    dataBoundary: 'product-service catalog APIs',
    stateCoverage: ['loading', 'empty', 'blocked', 'needs-attention', 'ready'],
    personaVisibility: ['admin', 'pricing-analyst', 'loan-officer'],
    dependencyStatus: 'Product-service catalog and investor eligibility refs govern editable state.',
    adapterStatus: 'Screen is locally available and keeps pricing rules as backend-owned references.',
    evidenceTarget: '.local-harness/evidence/PII-25-S04/product-management.json',
    match: (pathname) => pathname.startsWith('/admin/products/catalog') || pathname.startsWith('/admin/products/new'),
  },
  {
    id: 'rate-sheet-intake',
    label: 'Rate Sheet Intake',
    routePattern: '/pricing/rate-sheets',
    breadcrumb: 'Rate Sheet Intake',
    screenPackage: 'screens/ratesheet',
    dataBoundary: 'rate-sheet-service intake APIs',
    stateCoverage: ['loading', 'empty', 'blocked', 'needs-attention', 'ready'],
    personaVisibility: ['pricing-analyst', 'admin', 'operations-lead'],
    dependencyStatus: 'Rate sheet service grid and validation contracts control publish readiness.',
    adapterStatus: 'Upload, validation, remediation, and publish states are represented with local evidence capture.',
    evidenceTarget: '.local-harness/evidence/PII-25-S04/rate-sheet-intake.json',
    match: (pathname) => pathname.startsWith('/pricing/rate-sheets'),
  },
  {
    id: 'rate-feed-pipeline',
    label: 'Rate Feed Rule Book Pipeline',
    routePattern: '/admin/ratefeed/pipeline',
    breadcrumb: 'Rate Feed Pipeline',
    screenPackage: 'screens/rateFeedPipeline',
    dataBoundary: 'lib/api/rateFeedPipeline',
    stateCoverage: ['loading', 'blocked', 'pipeline-table', 'governance-history', 'sample-simulation'],
    personaVisibility: ['admin', 'pricing-analyst', 'operations-lead'],
    dependencyStatus: 'Rate-feed, governance, and adjustment service events control publish and cache-invalidation state.',
    adapterStatus: 'The monitor shows local fallback data when the pipeline API is unavailable and uses backend rows when connected.',
    evidenceTarget: '.local-harness/evidence/PII-32-S01/pipeline-monitor.json',
    match: (pathname) => pathname.startsWith('/admin/ratefeed/pipeline'),
  },
  {
    id: 'pricing-analysis',
    label: 'Pricing Analysis',
    routePattern: '/pricing/analysis',
    breadcrumb: 'Pricing Analysis',
    screenPackage: 'screens/pricing',
    dataBoundary: 'pricing-service analysis APIs',
    stateCoverage: ['loading', 'empty', 'blocked', 'needs-attention', 'ready'],
    personaVisibility: ['pricing-analyst', 'loan-officer', 'admin'],
    dependencyStatus: 'Pricing waterfall and margin refs are backend-owned and may block export readiness.',
    adapterStatus: 'Read-only analysis screen renders evidence refs and comparison surfaces without local pricing decisions.',
    evidenceTarget: '.local-harness/evidence/PII-25-S04/pricing-analysis.json',
    match: (pathname) => pathname.startsWith('/pricing/analysis'),
  },
  {
    id: 'lock-management',
    label: 'Lock Management',
    routePattern: '/locks',
    breadcrumb: 'Lock Management',
    screenPackage: 'screens/locks',
    dataBoundary: 'lock-service management APIs',
    stateCoverage: ['loading', 'empty', 'blocked', 'needs-attention', 'ready'],
    personaVisibility: ['operations-lead', 'loan-officer', 'admin', 'partner-manager'],
    dependencyStatus: 'Lock-service expiration and investor delivery refs govern blocked and attention states.',
    adapterStatus: 'Screen renders lock statuses, bulk action controls, and evidence capture without changing backend state.',
    evidenceTarget: '.local-harness/evidence/PII-25-S04/lock-management.json',
    match: (pathname) => pathname === '/locks' || pathname.startsWith('/locks/'),
  },
  {
    id: 'admin-governance',
    label: 'Governance lifecycle',
    routePattern: '/admin/governance',
    breadcrumb: 'Governance Lifecycle',
    screenPackage: 'screens/adminGovernance',
    dataBoundary: 'lib/api/adminGovernance',
    stateCoverage: ['load-state', 'descriptors', 'pending-review', 'dynamic-rule-evidence', 'blocked', 'ready'],
    evidenceTarget: '.local-harness/evidence/PII-24-S24/admin-governance-lifecycle.json',
    match: (pathname) => pathname.startsWith('/admin/'),
  },
  {
    id: 'custom-rules',
    label: 'Custom rules workbench',
    routePattern: '/custom-rules',
    breadcrumb: 'Custom Rules Workbench',
    screenPackage: 'screens/customRules',
    dataBoundary: 'lib/api/customRules',
    stateCoverage: ['load-state', 'blocked', 'empty', 'field-capture', 'decision-blockers', 'evidence-panels', 'ready'],
    evidenceTarget: '.local-harness/evidence/PII-21-S06/custom-rules-workbench-route-shell.json',
    match: (pathname) => pathname === '/custom-rules' || pathname.startsWith('/custom-rules/'),
  },
  {
    id: 'tenant-platform',
    label: 'Tenant platform coverage',
    routePattern: '/platform/tenant-context',
    breadcrumb: 'Tenant Platform Coverage',
    screenPackage: 'screens/tenantPlatform',
    dataBoundary: 'lib/api/tenantPlatform',
    stateCoverage: ['load-state', 'blocked', 'trace-refs', 'readiness', 'audit-evidence'],
    evidenceTarget: '.local-harness/evidence/PII-22-S08/tenant-platform.json',
    match: (pathname) => pathname.startsWith('/platform/tenant-context'),
  },
  {
    id: 'audit-replay',
    label: 'Audit replay evidence',
    routePattern: '/audit/replay',
    breadcrumb: 'Audit Replay',
    screenPackage: 'screens/auditReplayWorkbench',
    dataBoundary: 'lib/api/auditReplay',
    stateCoverage: ['load-state', 'audit-records', 'hash-integrity', 'replay-diffs', 'retention-legal-hold', 'export-evidence'],
    evidenceTarget: '.local-harness/evidence/PII-22-S10/audit-replay-workbench.json',
    match: (pathname) => pathname.startsWith('/audit/replay'),
  },
  {
    id: 'drift-monitoring',
    label: 'Drift Monitoring',
    routePattern: '/advisory/ml/drift',
    breadcrumb: 'Drift Monitoring',
    screenPackage: 'screens/driftMonitoring',
    dataBoundary: 'ml-advisory-service drift APIs',
    stateCoverage: ['load-state', 'blocked', 'ready'],
    evidenceTarget: '.local-harness/evidence/PII-24-S27/drift-monitoring.json',
    match: (pathname) => pathname.startsWith('/advisory/ml/drift'),
  },
  {
    id: 'model-version-governance',
    label: 'Model version governance',
    routePattern: '/advisory/ml/governance',
    breadcrumb: 'Model Version Governance',
    screenPackage: 'screens/modelVersionGovernance',
    dataBoundary: 'lib/api/modelVersionGovernance',
    stateCoverage: ['load-state', 'blocked', 'ready'],
    evidenceTarget: '.local-harness/evidence/PII-24-S26/model-version-governance.json',
    match: (pathname) => pathname.startsWith('/advisory/ml/governance'),
  },
  {
    id: 'ml-advisory-insights',
    label: 'ML advisory insights',
    routePattern: '/advisory/ml',
    breadcrumb: 'ML Advisory Insights',
    screenPackage: 'screens/mlAdvisoryInsights',
    dataBoundary: 'lib/api/mlAdvisoryInsights',
    stateCoverage: ['load-state', 'recommendation-evidence', 'model-version-governance', 'advisory-unavailable'],
    evidenceTarget: '.local-harness/evidence/PII-24-S25/ml-advisory-insights.json',
    match: (pathname) => pathname.startsWith('/advisory/ml'),
  },
  {
    id: 'service-modules',
    label: 'Feature screen registry',
    routePattern: '/service-modules',
    breadcrumb: 'Feature Screen Registry',
    screenPackage: 'screens/serviceModules',
    dataBoundary: 'lib/api/uiHealth',
    stateCoverage: ['empty', 'blocked', 'ready'],
    dependencyStatus: 'Local registry screen is available now; each connected module keeps its own setup status visible.',
    adapterStatus: 'Registry uses local setup details plus workbench availability status.',
    evidenceTarget: '.local-harness/evidence/PII-22-S24/feature-navigation-screen-registry.json',
    match: (pathname) => pathname.startsWith('/service-modules'),
  },
];

export function resolveWorkbenchModule(pathname: string) {
  return workbenchModules.find((screen) => screen.match(pathname)) ?? workbenchModules[0];
}

export function WorkbenchModuleRail({ activeModuleId }: { activeModuleId: string }) {
  return (
    <section className="module-rail" aria-labelledby="module-shell-heading">
      <div className="module-rail__copy">
        <p className="eyebrow">Screen registry</p>
        <h2 id="module-shell-heading">Modular route shell</h2>
        <p>
          Each workbench behavior has a visible path, intended users, and expected screen states.
          The registry avoids internal file names and keeps operational setup details out of the business navigation view.
        </p>
      </div>
      <div className="module-rail__grid" role="list" aria-label="Workbench screen modules">
        {workbenchModules.map((screen) => (
          <article
            key={screen.id}
            className={screen.id === activeModuleId ? 'module-card module-card--active' : 'module-card'}
            role="listitem"
            aria-current={screen.id === activeModuleId ? 'page' : undefined}
          >
            {(() => {
              const copy = registryCopyFor(screen);
              return <>
                <p className="module-card__route">{copy.pathLabel}</p>
            <strong className="module-card__title">{screen.label}</strong>
            <dl>
              <dt>Screen purpose</dt>
              <dd>{copy.screenArea}</dd>
              <dt>Connected work area</dt>
              <dd>{copy.sourceLabel}</dd>
              <dt>Next review step</dt>
              <dd>{copy.evidenceLabel}</dd>
            </dl>
            <ul className="chip-list" aria-label={`${screen.label} role and persona visibility`}>
              {personaVisibilityFor(screen).map((persona) => <li key={persona}>{persona}</li>)}
            </ul>
            <ul className="chip-list" aria-label={`${screen.label} loading error blocked state coverage`}>
              {uniqueValues(copy.stateLabels).map((state) => <li key={state}>{state}</li>)}
            </ul>
              </>;
            })()}
          </article>
        ))}
      </div>
    </section>
  );
}
