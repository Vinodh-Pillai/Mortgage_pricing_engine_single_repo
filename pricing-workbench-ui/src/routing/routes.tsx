import { lazy, type ComponentType, type LazyExoticComponent } from 'react';
import { matchPath } from 'react-router-dom';
import { workbenchModules, type WorkbenchScreenModule } from '../screens/workbenchShell/WorkbenchShell';

export type RouteParams = Record<string, string>;

export type WorkbenchRouteDefinition = {
  id: string;
  sourceModuleId: string;
  path: string;
  label: string;
  breadcrumb: string;
  nestedUnderQuoteRun?: boolean;
  requiresAuth: boolean;
  lazyComponent: LazyExoticComponent<ComponentType<any>>;
};

const lazyScreen = (loader: () => Promise<{ default: ComponentType<any> }>) => lazy(loader);

export const routeComponentLoaders: Record<string, () => Promise<{ default: ComponentType<any> }>> = {
  login: () => import('../screens/auth/LoginScreen').then((module) => ({ default: module.LoginScreen })),
  'tenant-home': () => import('../screens/tenantHome').then((module) => ({ default: module.TenantHomeScreen })),
  shell: () => import('../screens/quoteIntake/QuoteIntakeScreen').then((module) => ({ default: module.QuoteIntakeScreen })),
  'quote-intake': () => import('../screens/quoteIntake/QuoteIntakeScreen').then((module) => ({ default: module.QuoteIntakeScreen })),
  'quote-offers': () => import('../screens/quoteOffers/QuoteOffersScreen'),
  'quote-detail': () => import('../screens/quoteDetail/QuoteDetailScreen'),
  'pricing-waterfall': () => import('../screens/pricingWaterfall/PricingWaterfallScreen'),
  'quote-journey': () => import('../screens/NotFoundScreen'),
  'scenario-analysis': () => import('../screens/scenarioAnalysis/ScenarioAnalysisWorkspace').then((module) => ({ default: module.ScenarioAnalysisWorkspaceScreen })),
  'scenario-fico-sensitivity': () => import('../screens/scenarioAnalysis/sensitivity/FicoSensitivity').then((module) => ({ default: module.FicoSensitivityScreen })),
  'scenario-ltv-sensitivity': () => import('../screens/scenarioAnalysis/sensitivity/LtvSensitivity').then((module) => ({ default: module.LtvSensitivityScreen })),
  'scenario-product-comparison': () => import('../screens/scenarioAnalysis/comparison/ProductComparison').then((module) => ({ default: module.ProductComparisonScreen })),
  'scenario-lock-period-comparison': () => import('../screens/scenarioAnalysis/comparison/LockPeriodComparison').then((module) => ({ default: module.LockPeriodComparisonScreen })),
  'quote-lock': () => import('../screens/quoteLock/LockWorkflow'),
  'quote-eligibility': () => import('../screens/quoteEligibility/QuoteEligibilityScreen'),
  'adjustment-evidence': () => import('../screens/adjustmentEvidence/AdjustmentEvidenceScreen'),
  'margin-profitability': () => import('../screens/marginProfitability').then((module) => ({ default: module.MarginProfitabilityScreen })),
  'exception-concessions': () => import('../screens/exceptionConcessions'),
  'partner-quotes': () => import('../screens/partnerQuotes/PartnerQuotesLayout'),
  'partner-transport': () => import('../screens/NotFoundScreen'),
  'rate-feed-ops': () => import('../screens/rateFeedOps').then((module) => ({ default: module.RateFeedOperationsScreen })),
  'rate-feed-pipeline': () => import('../screens/rateFeedPipeline').then((module) => ({ default: module.RateFeedPipelineScreen })),
  'performance-dashboard': () => import('../screens/observabilityPerformance').then((module) => ({ default: module.PerformanceDashboardScreen })),
  'ops-cases': () => import('../screens/opsCases').then((module) => ({ default: module.OpsCasesScreen })),
  'compliance-evidence': () => import('../screens/complianceEvidence').then((module) => ({ default: module.ComplianceEvidenceScreen })),
  'fair-lending-dashboard': () => import('../screens/fairLending').then((module) => ({ default: module.FairLendingDashboard })),
  'quality-dashboard': () => import('../screens/NotFoundScreen'),
  'tenant-onboarding': () => import('../screens/tenant/TenantOnboardingScreen').then((module) => ({ default: module.TenantOnboardingScreen })),
  'tenant-admin': () => import('../screens/tenantAdmin').then((module) => ({ default: module.TenantAdminScreen })),
  'product-catalog-manager': () => import('../screens/productCatalogManager').then((module) => ({ default: module.ProductCatalogManagerScreen })),
  'product-admin': () => import('../screens/productAdmin').then((module) => ({ default: module.ProductAdminScreen })),
  'product-management': () => import('../screens/product/ProductManagementScreen').then((module) => ({ default: module.ProductManagementScreen })),
  'rate-sheet-intake': () => import('../screens/ratesheet/RateSheetIntakeScreen').then((module) => ({ default: module.RateSheetIntakeScreen })),
  'pricing-analysis': () => import('../screens/pricing/PricingAnalysisScreen').then((module) => ({ default: module.PricingAnalysisScreen })),
  'lock-management': () => import('../screens/locks/LockManagementScreen').then((module) => ({ default: module.LockManagementScreen })),
  'admin-governance': () => import('../screens/adminGovernance').then((module) => ({ default: module.AdminGovernanceScreen })),
  'custom-rules': () => import('../screens/NotFoundScreen'),
  'tenant-platform': () => import('../screens/NotFoundScreen'),
  'audit-replay': () => import('../screens/NotFoundScreen'),
  'drift-monitoring': () => import('../screens/driftMonitoring').then((module) => ({ default: module.DriftMonitoringScreen })),
  'model-version-governance': () => import('../screens/modelVersionGovernance').then((module) => ({ default: module.ModelVersionGovernanceScreen })),
  'ml-advisory-insights': () => import('../screens/mlAdvisoryInsights').then((module) => ({ default: module.MlAdvisoryInsightsScreen })),
  'service-modules': () => import('../screens/NotFoundScreen'),
  'not-found': () => import('../screens/NotFoundScreen'),
};

function definitionForModule(module: WorkbenchScreenModule): WorkbenchRouteDefinition {
  return {
    id: module.id,
    sourceModuleId: module.id,
    path: module.routePattern,
    label: module.label,
    breadcrumb: module.breadcrumb,
    nestedUnderQuoteRun: module.routePattern.startsWith('/quote/:runId/'),
    requiresAuth: module.id !== 'login',
    lazyComponent: lazyScreen(routeComponentLoaders[module.id] ?? routeComponentLoaders['not-found']),
  };
}

const scenarioModule = workbenchModules.find((module) => module.id === 'scenario-analysis');
const tenantOnboardingModule = workbenchModules.find((module) => module.id === 'tenant-onboarding');
const productManagementModule = workbenchModules.find((module) => module.id === 'product-management');
const rateSheetModule = workbenchModules.find((module) => module.id === 'rate-sheet-intake');
const rateFeedPipelineModule = workbenchModules.find((module) => module.id === 'rate-feed-pipeline');
const pricingAnalysisModule = workbenchModules.find((module) => module.id === 'pricing-analysis');
const lockManagementModule = workbenchModules.find((module) => module.id === 'lock-management');

const scenarioChildRoutes: WorkbenchRouteDefinition[] = scenarioModule ? [
  { ...definitionForModule(scenarioModule), id: 'scenario-fico-sensitivity', path: '/quote/:runId/what-if/fico-sensitivity', label: 'FICO sensitivity', breadcrumb: 'FICO Sensitivity', lazyComponent: lazyScreen(routeComponentLoaders['scenario-fico-sensitivity']) },
  { ...definitionForModule(scenarioModule), id: 'scenario-ltv-sensitivity', path: '/quote/:runId/what-if/ltv-sensitivity', label: 'LTV sensitivity', breadcrumb: 'LTV Sensitivity', lazyComponent: lazyScreen(routeComponentLoaders['scenario-ltv-sensitivity']) },
  { ...definitionForModule(scenarioModule), id: 'scenario-product-comparison', path: '/quote/:runId/what-if/product-comparison', label: 'Product comparison', breadcrumb: 'Product Comparison', lazyComponent: lazyScreen(routeComponentLoaders['scenario-product-comparison']) },
  { ...definitionForModule(scenarioModule), id: 'scenario-lock-period-comparison', path: '/quote/:runId/what-if/lock-period-comparison', label: 'Lock period comparison', breadcrumb: 'Lock Period Comparison', lazyComponent: lazyScreen(routeComponentLoaders['scenario-lock-period-comparison']) },
] : [];

const functionalityChildRoutes: WorkbenchRouteDefinition[] = [
  ...(tenantOnboardingModule ? [{ ...definitionForModule(tenantOnboardingModule), id: 'tenant-onboarding-admin-new', path: '/admin/tenants/new', label: 'New Tenant', breadcrumb: 'New Tenant', lazyComponent: lazyScreen(routeComponentLoaders['tenant-onboarding']) }] : []),
  ...(productManagementModule ? [
    { ...definitionForModule(productManagementModule), id: 'product-management-detail', path: '/admin/products/catalog/:productCode', label: 'Product Detail', breadcrumb: 'Product Detail', lazyComponent: lazyScreen(routeComponentLoaders['product-management']) },
    { ...definitionForModule(productManagementModule), id: 'product-management-new', path: '/admin/products/new', label: 'New Product', breadcrumb: 'New Product', lazyComponent: lazyScreen(routeComponentLoaders['product-management']) },
  ] : []),
  ...(rateSheetModule ? [
    { ...definitionForModule(rateSheetModule), id: 'rate-sheet-intake-new', path: '/pricing/rate-sheets/new', label: 'New Rate Sheet', breadcrumb: 'New Rate Sheet', lazyComponent: lazyScreen(routeComponentLoaders['rate-sheet-intake']) },
    { ...definitionForModule(rateSheetModule), id: 'rate-sheet-intake-detail', path: '/pricing/rate-sheets/:id', label: 'Rate Sheet Detail', breadcrumb: 'Rate Sheet Detail', lazyComponent: lazyScreen(routeComponentLoaders['rate-sheet-intake']) },
  ] : []),
  ...(rateFeedPipelineModule ? [{ ...definitionForModule(rateFeedPipelineModule), id: 'rate-feed-pipeline-admin', path: '/admin/ratefeed/pipeline', label: 'Rate feed pipeline', breadcrumb: 'Rate Feed Pipeline', lazyComponent: lazyScreen(routeComponentLoaders['rate-feed-pipeline']) }] : []),
  ...(pricingAnalysisModule ? [{ ...definitionForModule(pricingAnalysisModule), id: 'pricing-analysis-run', path: '/pricing/analysis/:runId', label: 'Pricing Analysis Run', breadcrumb: 'Pricing Analysis Run', lazyComponent: lazyScreen(routeComponentLoaders['pricing-analysis']) }] : []),
  ...(lockManagementModule ? [{ ...definitionForModule(lockManagementModule), id: 'lock-management-detail', path: '/locks/:lockId', label: 'Lock Detail', breadcrumb: 'Lock Detail', lazyComponent: lazyScreen(routeComponentLoaders['lock-management']) }] : []),
];

export const appRouteDefinitions: WorkbenchRouteDefinition[] = [
  {
    id: 'login',
    sourceModuleId: 'login',
    path: '/login',
    label: 'Login',
    breadcrumb: 'Login',
    requiresAuth: false,
    lazyComponent: lazyScreen(routeComponentLoaders.login),
  },
  ...workbenchModules.map(definitionForModule),
  ...scenarioChildRoutes,
  ...functionalityChildRoutes,
  {
    id: 'not-found',
    sourceModuleId: 'not-found',
    path: '*',
    label: 'Not found',
    breadcrumb: 'Not found',
    requiresAuth: false,
    lazyComponent: lazyScreen(routeComponentLoaders['not-found']),
  },
];

export const quoteNestedRoutePaths = appRouteDefinitions
  .filter((route) => route.nestedUnderQuoteRun)
  .map((route) => route.path);

const rankedRoutes = [...appRouteDefinitions]
  .filter((route) => route.path !== '*')
  .sort((left, right) => right.path.split('/').length - left.path.split('/').length || right.path.length - left.path.length);

export function buildRoutePath(path: string, params: RouteParams = {}) {
  return path.replace(/:([A-Za-z0-9_]+)/g, (_, key: string) => encodeURIComponent(params[key] ?? `:${key}`));
}

export function findRouteDefinition(routeId: string) {
  return appRouteDefinitions.find((route) => route.id === routeId);
}

export function matchAppRoute(pathname: string) {
  return rankedRoutes.find((route) => matchPath({ path: route.path, end: true }, pathname)) ?? appRouteDefinitions.find((route) => route.id === 'not-found')!;
}

export function moduleForRoute(route: WorkbenchRouteDefinition) {
  return workbenchModules.find((module) => module.id === route.sourceModuleId) ?? {
    id: route.id,
    label: route.label,
    routePattern: route.path,
    breadcrumb: route.breadcrumb,
    screenPackage: 'routing',
    dataBoundary: 'routing',
    stateCoverage: [],
    evidenceTarget: '.local-harness/evidence/PII-25-S01/routing.json',
    match: (pathname: string) => route.path === '*' ? true : Boolean(matchPath({ path: route.path, end: true }, pathname)),
  } satisfies WorkbenchScreenModule;
}
