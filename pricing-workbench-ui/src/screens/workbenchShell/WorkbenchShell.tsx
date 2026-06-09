import type { ComponentType } from 'react';

export type WorkbenchScreenModule = {
  id: string;
  label: string;
  routePattern: string;
  breadcrumb: string;
  screenPackage: string;
  dataBoundary: string;
  stateCoverage: string[];
  evidenceTarget: string;
  match: (pathname: string) => boolean;
  Component?: ComponentType;
};

export const workbenchModules: WorkbenchScreenModule[] = [
  {
    id: 'shell',
    label: 'Workbench shell',
    routePattern: '/quote/start',
    breadcrumb: 'Start Quote',
    screenPackage: 'screens/workbenchShell',
    dataBoundary: 'lib/api/uiHealth',
    stateCoverage: ['empty', 'load-state', 'blocked', 'ready'],
    evidenceTarget: '.local-harness/evidence/PII-22-S21/shell-route-registry.json',
    match: (pathname) => pathname === '/' || pathname.startsWith('/quote/start'),
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
    match: (pathname) => /^\/quote\/[^/]+\/offers/.test(pathname),
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
    evidenceTarget: '.local-harness/evidence/PII-22-S21/partner-quotes.json',
    match: (pathname) => pathname.startsWith('/partners/quotes'),
  },
  {
    id: 'partner-transport',
    label: 'Partner connections',
    routePattern: '/partners/webhooks',
    breadcrumb: 'Partner Connections',
    screenPackage: 'screens/partnerTransport',
    dataBoundary: 'lib/api/partnerTransport',
    stateCoverage: ['load-state', 'blocked', 'ready'],
    evidenceTarget: '.local-harness/evidence/PII-22-S21/partner-transport.json',
    match: (pathname) => pathname.startsWith('/partners/webhooks') || pathname.startsWith('/partners/admin/safety') || pathname.startsWith('/partners/alerts'),
  },
  {
    id: 'rate-feed-ops',
    label: 'Rate feed operations',
    routePattern: '/ops/rate-feeds',
    breadcrumb: 'Rate Feed Operations',
    screenPackage: 'screens/rateFeedOps',
    dataBoundary: 'lib/api/rateFeedOps',
    stateCoverage: ['load-state', 'workflow-steps', 'row-blockers', 'replay-evidence'],
    evidenceTarget: '.local-harness/evidence/PII-22-S03/rate-feed-ops.json',
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
    evidenceTarget: '.local-harness/evidence/PII-22-S09/performance-dashboard.json',
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
    evidenceTarget: '.local-harness/evidence/PII-22-S21/ops-cases.json',
    match: (pathname) => pathname.startsWith('/ops/dashboard') || pathname.startsWith('/ops/queues') || pathname.startsWith('/ops/cases') || pathname.startsWith('/ops/escalations'),
  },
  {
    id: 'compliance-evidence',
    label: 'Compliance evidence',
    routePattern: '/compliance/evidence',
    breadcrumb: 'Compliance Evidence',
    screenPackage: 'screens/complianceEvidence',
    dataBoundary: 'lib/api/complianceEvidence',
    stateCoverage: ['load-state', 'empty', 'blocked', 'ready'],
    evidenceTarget: '.local-harness/evidence/PII-22-S21/compliance-evidence.json',
    match: (pathname) => pathname.startsWith('/compliance') || pathname.startsWith('/privacy/requests') || pathname.startsWith('/security/events'),
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
    id: 'product-catalog-manager',
    label: 'Product catalog manager',
    routePattern: '/admin/products/catalog',
    breadcrumb: 'Product Catalog Manager',
    screenPackage: 'screens/productCatalogManager',
    dataBoundary: 'lib/api/products',
    stateCoverage: ['load-state', 'blocked', 'lifecycle', 'audit-evidence'],
    evidenceTarget: '.local-harness/evidence/PII-22-S02/product-catalog-manager.json',
    match: (pathname) => pathname.startsWith('/admin/products/catalog'),
  },
  {
    id: 'admin-governance',
    label: 'Governance lifecycle',
    routePattern: '/admin/governance',
    breadcrumb: 'Governance Lifecycle',
    screenPackage: 'screens/adminGovernance',
    dataBoundary: 'lib/api/adminGovernance',
    stateCoverage: ['load-state', 'descriptors', 'pending-review', 'dynamic-rule-evidence', 'blocked', 'ready'],
    evidenceTarget: '.local-harness/evidence/PII-22-S11/admin-governance-lifecycle.json',
    match: (pathname) => pathname.startsWith('/admin/'),
  },
  {
    id: 'custom-rules',
    label: 'Custom rule evidence',
    routePattern: '/custom-rules/evidence',
    breadcrumb: 'Custom Rule Evidence',
    screenPackage: 'screens/customRules',
    dataBoundary: 'lib/api/customRules',
    stateCoverage: ['load-state', 'blocked', 'ready'],
    evidenceTarget: '.local-harness/evidence/PII-22-S21/custom-rules.json',
    match: (pathname) => pathname.startsWith('/custom-rules/'),
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
    id: 'service-modules',
    label: 'Service modules',
    routePattern: '/service-modules',
    breadcrumb: 'Service Modules',
    screenPackage: 'screens/serviceModules',
    dataBoundary: 'lib/api/uiHealth',
    stateCoverage: ['empty', 'blocked', 'ready'],
    evidenceTarget: '.local-harness/evidence/PII-22-S21/service-modules.json',
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
          Each workbench behavior now has an explicit screen package, data boundary, state coverage, and story evidence target.
          External visual references are unavailable until copied into project-relative evidence, so this shell uses repo-local requirements.
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
            <p className="module-card__route">{screen.routePattern}</p>
            <strong className="module-card__title">{screen.label}</strong>
            <dl>
              <dt>Screen package</dt>
              <dd>{screen.screenPackage}</dd>
              <dt>Data boundary</dt>
              <dd>{screen.dataBoundary}</dd>
              <dt>Evidence target</dt>
              <dd>{screen.evidenceTarget}</dd>
            </dl>
            <ul className="chip-list" aria-label={`${screen.label} state coverage`}>
              {screen.stateCoverage.map((state) => <li key={state}>{state}</li>)}
            </ul>
          </article>
        ))}
      </div>
    </section>
  );
}
