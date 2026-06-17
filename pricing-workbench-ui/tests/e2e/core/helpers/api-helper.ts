import { APIRequestContext, Page, request } from '@playwright/test';
import { PersonaRole, personas } from '../personas/personas';

export interface ApiResponse<T = any> {
  status: number;
  data: T;
  headers: Record<string, string>;
  traceId?: string;
}

export interface TestContext {
  persona: PersonaRole;
  tenantId: string;
  runId?: string;
  optionId?: string;
  traceId: string;
  apiContext: APIRequestContext;
}

export class ApiHelper {
  private apiContext: APIRequestContext;
  private baseURL: string;
  private defaultHeaders: Record<string, string>;

  constructor(baseURL: string = process.env.VITE_API_BASE || 'http://localhost:8080') {
    this.baseURL = baseURL;
    this.defaultHeaders = {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    };
  }

  async init(): Promise<void> {
    this.apiContext = await request.newContext({
      baseURL: this.baseURL,
      extraHTTPHeaders: this.defaultHeaders,
      timeout: 30000,
    });
  }

  async dispose(): Promise<void> {
    await this.apiContext?.dispose();
  }

  createContext(persona: PersonaRole): TestContext {
    const p = personas[persona];
    return {
      persona,
      tenantId: p.defaultTenant,
      traceId: `e2e-${persona}-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      apiContext: this.apiContext,
    };
  }

  async get<T>(ctx: TestContext, endpoint: string, params?: Record<string, string>): Promise<ApiResponse<T>> {
    const url = this.buildUrl(endpoint, params);
    const response = await this.apiContext.get(url, {
      headers: this.getTraceHeaders(ctx),
    });
    return this.processResponse<T>(response);
  }

  async post<T>(ctx: TestContext, endpoint: string, data: any): Promise<ApiResponse<T>> {
    const response = await this.apiContext.post(endpoint, {
      headers: this.getTraceHeaders(ctx),
      data,
    });
    return this.processResponse<T>(response);
  }

  async patch<T>(ctx: TestContext, endpoint: string, data: any): Promise<ApiResponse<T>> {
    const response = await this.apiContext.patch(endpoint, {
      headers: this.getTraceHeaders(ctx),
      data,
    });
    return this.processResponse<T>(response);
  }

  async put<T>(ctx: TestContext, endpoint: string, data: any): Promise<ApiResponse<T>> {
    const response = await this.apiContext.put(endpoint, {
      headers: this.getTraceHeaders(ctx),
      data,
    });
    return this.processResponse<T>(response);
  }

  async delete<T>(ctx: TestContext, endpoint: string): Promise<ApiResponse<T>> {
    const response = await this.apiContext.delete(endpoint, {
      headers: this.getTraceHeaders(ctx),
    });
    return this.processResponse<T>(response);
  }

  private buildUrl(endpoint: string, params?: Record<string, string>): string {
    if (!params) return endpoint;
    const searchParams = new URLSearchParams(params);
    return `${endpoint}?${searchParams.toString()}`;
  }

  private getTraceHeaders(ctx: TestContext): Record<string, string> {
    return {
      'X-Ui-Trace-Id': ctx.traceId,
      'X-Tenant-Context': ctx.tenantId,
      ...(ctx.runId && { 'X-Run-Id': ctx.runId }),
      ...(ctx.optionId && { 'X-Option-Id': ctx.optionId }),
    };
  }

  private async processResponse<T>(response: any): Promise<ApiResponse<T>> {
    const data = await response.json().catch(() => ({}));
    const traceId = response.headers()['x-ui-trace-id'] || response.headers()['x-trace-id'];
    return {
      status: response.status(),
      data,
      headers: response.headers(),
      traceId,
    };
  }

  // Quote Intake APIs
  async getIntakeMetadata(ctx: TestContext): Promise<ApiResponse> {
    return this.get(ctx, `/api/v1/tenants/${ctx.tenantId}/quote-runs/intake-metadata`);
  }

  async launchQuoteRun(ctx: TestContext, intake: any): Promise<ApiResponse> {
    return this.post(ctx, `/api/v1/tenants/${ctx.tenantId}/quote-runs`, intake);
  }

  async validateIntake(ctx: TestContext, intake: any): Promise<ApiResponse> {
    return this.post(ctx, `/api/v1/tenants/${ctx.tenantId}/quote-runs/intake/validate`, intake);
  }

  async getQuoteRunStatus(ctx: TestContext, runId: string): Promise<ApiResponse> {
    return this.get(ctx, `/api/v1/tenants/${ctx.tenantId}/quote-runs/${runId}/status`);
  }

  // Offer APIs
  async getOffers(ctx: TestContext, runId: string): Promise<ApiResponse> {
    return this.get(ctx, `/api/v1/tenants/${ctx.tenantId}/quote-runs/${runId}/offers`);
  }

  async getOfferExplanation(ctx: TestContext, runId: string, optionId: string): Promise<ApiResponse> {
    return this.get(ctx, `/api/v1/tenants/${ctx.tenantId}/quote-runs/${runId}/offers/${optionId}/explain`);
  }

  async getQuoteDetail(ctx: TestContext, runId: string, optionId: string): Promise<ApiResponse> {
    return this.get(ctx, `/api/v1/tenants/${ctx.tenantId}/quote-runs/${runId}/offers/${optionId}/detail`);
  }

  async selectOffer(ctx: TestContext, runId: string, optionId: string, selection: any): Promise<ApiResponse> {
    return this.post(ctx, `/api/v1/tenants/${ctx.tenantId}/quote-runs/${runId}/offers/${optionId}/select`, selection);
  }

  // Lock APIs
  async getLockWorkflow(ctx: TestContext, runId: string, optionId?: string): Promise<ApiResponse> {
    const params = optionId ? { selectedOfferId: optionId } : {};
    return this.get(ctx, `/api/v1/tenants/${ctx.tenantId}/quote-runs/${runId}/lock`, params);
  }

  async confirmLock(ctx: TestContext, runId: string, optionId: string, disclosuresAccepted: boolean): Promise<ApiResponse> {
    return this.post(ctx, `/api/v1/tenants/${ctx.tenantId}/quote-runs/${runId}/lock/confirm`, {
      selectedOfferId: optionId,
      disclosuresAccepted,
    });
  }

  // Pricing APIs
  async getPricingWaterfall(ctx: TestContext, runId: string): Promise<ApiResponse> {
    return this.get(ctx, `/api/v1/tenants/${ctx.tenantId}/quote-runs/${runId}/pricing-waterfall`);
  }

  async getQuoteJourney(ctx: TestContext, runId: string): Promise<ApiResponse> {
    return this.get(ctx, `/api/v1/tenants/${ctx.tenantId}/quote-runs/${runId}/journey`);
  }

  async getEligibility(ctx: TestContext, runId: string, optionId?: string): Promise<ApiResponse> {
    const params = optionId ? { optionId } : {};
    return this.get(ctx, `/api/v1/tenants/${ctx.tenantId}/quote-runs/${runId}/eligibility`, params);
  }

  // Scenario Analysis APIs
  async getScenarioAnalysisWorkspace(ctx: TestContext, runId: string): Promise<ApiResponse> {
    return this.get(ctx, `/api/v1/tenants/${ctx.tenantId}/quote-runs/${runId}/what-if/workspace`);
  }

  async recalculateScenario(ctx: TestContext, runId: string, request: any): Promise<ApiResponse> {
    return this.post(ctx, `/api/v1/tenants/${ctx.tenantId}/quote-runs/${runId}/what-if/recalculate`, request);
  }

  async getFicoSensitivity(ctx: TestContext, runId: string): Promise<ApiResponse> {
    return this.get(ctx, `/api/v1/tenants/${ctx.tenantId}/quote-runs/${runId}/what-if/fico-sensitivity`);
  }

  async getLtvSensitivity(ctx: TestContext, runId: string): Promise<ApiResponse> {
    return this.get(ctx, `/api/v1/tenants/${ctx.tenantId}/quote-runs/${runId}/what-if/ltv-sensitivity`);
  }

  async getProductComparison(ctx: TestContext, runId: string): Promise<ApiResponse> {
    return this.get(ctx, `/api/v1/tenants/${ctx.tenantId}/quote-runs/${runId}/what-if/product-comparison`);
  }

  async getLockPeriodComparison(ctx: TestContext, runId: string): Promise<ApiResponse> {
    return this.get(ctx, `/api/v1/tenants/${ctx.tenantId}/quote-runs/${runId}/what-if/lock-period-comparison`);
  }

  // Adjustment & Margin APIs
  async getAdjustmentEvidence(ctx: TestContext): Promise<ApiResponse> {
    return this.get(ctx, `/api/v1/adjustments/evidence`, { tenantContext: ctx.tenantId });
  }

  async getMarginProfitability(ctx: TestContext): Promise<ApiResponse> {
    return this.get(ctx, `/api/v1/margins/profitability`, { tenantContext: ctx.tenantId });
  }

  // Exception APIs
  async getExceptionWorkbench(ctx: TestContext): Promise<ApiResponse> {
    return this.get(ctx, `/api/v1/exceptions/concessions/workbench`, { tenantContext: ctx.tenantId });
  }

  // Partner APIs
  async getPartnerQuotes(ctx: TestContext, partnerId: string, status?: string): Promise<ApiResponse> {
    return this.get(ctx, `/api/v1/partners/${partnerId}/quotes`, status ? { status } : undefined);
  }

  async getPartnerQuoteDetail(ctx: TestContext, partnerId: string, quoteId: string): Promise<ApiResponse> {
    return this.get(ctx, `/api/v1/partners/${partnerId}/quotes/${quoteId}`);
  }

  async requestPartnerReprice(ctx: TestContext, partnerId: string, quoteId: string): Promise<ApiResponse> {
    return this.post(ctx, `/api/v1/partners/${partnerId}/quotes/${quoteId}/reprice`, { requestedBy: 'e2e-test' });
  }

  async getPartnerWebhookHealth(ctx: TestContext, partnerId: string): Promise<ApiResponse> {
    return this.get(ctx, `/api/v1/partners/${partnerId}/integrations/webhooks`);
  }

  async getPartnerChannelWorkbench(ctx: TestContext, partnerId: string): Promise<ApiResponse> {
    return this.get(ctx, `/api/v1/partners/${partnerId}/integrations/workbench`);
  }

  async replayPartnerWebhook(ctx: TestContext, partnerId: string, webhookId: string, eventId: string, correlationId: string, idempotencyConfirmed: boolean): Promise<ApiResponse> {
    return this.post(ctx, `/api/v1/partners/${partnerId}/integrations/webhooks/${webhookId}/replay`, {
      eventId,
      correlationId,
      idempotencyConfirmed,
    });
  }

  // Ops APIs
  async getRateFeedOps(ctx: TestContext): Promise<ApiResponse> {
    return this.get(ctx, '/api/v1/ops/rate-feeds');
  }

  async getPerformanceDashboard(ctx: TestContext): Promise<ApiResponse> {
    return this.get(ctx, '/api/v1/ops/performance');
  }

  async getOpsCases(ctx: TestContext): Promise<ApiResponse> {
    return this.get(ctx, '/api/v1/ops/cases');
  }

  async getOpsCaseDetail(ctx: TestContext, caseId: string): Promise<ApiResponse> {
    return this.get(ctx, `/api/v1/ops/cases/${caseId}`);
  }

  // Governance APIs
  async getAdminGovernance(ctx: TestContext): Promise<ApiResponse> {
    return this.get(ctx, '/api/v1/admin/governance');
  }

  async getProductCatalogManager(ctx: TestContext): Promise<ApiResponse> {
    return this.get(ctx, '/api/v1/products/catalog/manager');
  }

  async createProductCatalogEntry(ctx: TestContext, product: any): Promise<ApiResponse> {
    return this.post(ctx, '/api/v1/products/catalog', product);
  }

  // Compliance APIs
  async getComplianceEvidence(ctx: TestContext): Promise<ApiResponse> {
    return this.get(ctx, '/api/v1/compliance/evidence');
  }

  // ML Advisory APIs
  async getMlAdvisoryInsights(ctx: TestContext): Promise<ApiResponse> {
    return this.get(ctx, '/api/v1/ml-advisory/insights');
  }

  async getModelGovernance(ctx: TestContext): Promise<ApiResponse> {
    return this.get(ctx, '/api/v1/ml-advisory/models');
  }

  async getDriftMonitoring(ctx: TestContext, type: 'feature' | 'prediction' | 'population'): Promise<ApiResponse> {
    return this.get(ctx, `/api/v1/ml-advisory/drift/${type}`);
  }

  async getDriftAlerts(ctx: TestContext): Promise<ApiResponse> {
    return this.get(ctx, '/api/v1/ml-advisory/drift/alerts');
  }

  async acknowledgeDriftAlert(ctx: TestContext, alertId: string): Promise<ApiResponse> {
    return this.post(ctx, `/api/v1/ml-advisory/drift/alerts/${alertId}/acknowledge`, {});
  }

  // Custom Rules APIs
  async getCustomRuleEvidence(ctx: TestContext): Promise<ApiResponse> {
    return this.get(ctx, '/api/v1/custom-rules/evidence');
  }

  async getCustomRuleCalculationEvidence(ctx: TestContext, quoteId: string): Promise<ApiResponse> {
    return this.get(ctx, `/api/ui/custom-rules/evidence?quoteId=${quoteId}`);
  }

  // Audit Replay APIs
  async getAuditReplayWorkbench(ctx: TestContext): Promise<ApiResponse> {
    return this.get(ctx, '/api/v1/audit-replay/workbench');
  }

  // Health Checks
  async healthCheck(): Promise<ApiResponse> {
    return this.get({} as TestContext, '/actuator/health');
  }
}

export async function mockPii25BackendApis(page: Page): Promise<void> {
  await page.route('**/api/v1/**', async (route) => route.fulfill({ json: { mocked: true, status: 'READY', records: [] } }));
  await page.route('**/api/ui/health', async (route) => route.fulfill({ json: { service: 'pricing-workbench-ui', status: 'AVAILABLE', ready: true, dependencyStatus: 'READY', dependencies: [] } }));
  await page.route('**/api/v1/tenants/*/quote-runs/intake-metadata', async (route) => route.fulfill({ json: pii25IntakeMetadata() }));
  await page.route('**/api/v1/tenants/*/scenarios', async (route) => route.fulfill({ json: { scenarioId: 'scenario-e2e-pii25', scenarioVersion: 1, status: 'DRAFT_INCOMPLETE' } }));
  await page.route('**/api/v1/tenants/*/scenarios/scenario-e2e-pii25', async (route) => route.fulfill({ json: pii25DraftScenario() }));
  await page.route('**/api/v1/tenants/*/scenarios/scenario-e2e-pii25/**', async (route) => route.fulfill({ json: { scenarioId: 'scenario-e2e-pii25', scenarioVersion: 2, status: 'DRAFT_INCOMPLETE' } }));
  await page.route('**/api/v1/tenants/*/scenarios/scenario-e2e-pii25/**/validate', async (route) => route.fulfill({ json: { passed: true, status: 'PASSED', message: 'Validated by mocked PII-25 scenario service.', blockers: {} } }));
  await page.route('**/api/v1/tenants/*/quote-runs', async (route) => route.fulfill({ json: { status: 'CREATED', runId: 'e2e-run', nextRoute: '/quote/e2e-run/offers', validationSummary: { passed: true, status: 'PASSED', message: 'Quote run launched from mocked backend.', blockers: {} }, uiTraceId: 'pii25-e2e', events: [{ eventType: 'QUOTE_RUN_CREATED' }], fallbackMode: false, dependencyStatus: 'READY', auditPackageId: 'audit-pii25', replayHashRef: 'replay-pii25', validationIssues: [], missingContractBlockers: [] } }));
  await page.route('**/api/v1/tenants/*/quote-runs/*/offers', async (route) => route.fulfill({ json: pii25OfferComparison(runIdFromUrl(route.request().url())) }));
  await page.route('**/api/v1/tenants/*/products/**', async (route) => route.fulfill({ json: { products: [{ id: 'product-alpha', status: 'ready' }], total: 1 } }));
  await page.route('**/api/v1/products/**', async (route) => route.fulfill({ json: { products: [{ id: 'product-alpha', status: 'ready' }], total: 1 } }));
  await page.route('**/api/v1/tenants/*/rate-sheets/**', async (route) => route.fulfill({ json: { rateSheets: [{ id: 'rs-001', status: 'ready' }], validation: { passed: true } } }));
  await page.route('**/api/v1/rate-sheets/**', async (route) => route.fulfill({ json: { rateSheets: [{ id: 'rs-001', status: 'ready' }], validation: { passed: true } } }));
  await page.route('**/api/v1/tenants/*/pricing/**', async (route) => route.fulfill({ json: { waterfall: [{ label: 'Base selection', value: 'service-owned' }], margins: [], comparisons: [] } }));
  await page.route('**/api/v1/pricing/**', async (route) => route.fulfill({ json: { waterfall: [{ label: 'Base selection', value: 'service-owned' }], margins: [], comparisons: [] } }));
  await page.route('**/api/v1/tenants/*/locks/**', async (route) => route.fulfill({ json: { locks: [{ id: 'lock-e2e', status: 'confirmed' }], detail: { id: 'lock-e2e', status: 'confirmed' } } }));
  await page.route('**/api/v1/locks/**', async (route) => route.fulfill({ json: { locks: [{ id: 'lock-e2e', status: 'confirmed' }], detail: { id: 'lock-e2e', status: 'confirmed' } } }));
}

function pii25Field(fieldId: string, required = false, dataType: 'text' | 'email' | 'textarea' | 'number' = 'text') {
  return { fieldId, label: fieldId.replace(/[A-Z]/g, ' $&').replace(/^./, (c) => c.toUpperCase()), groupId: 'pii25', dataType, required, helpText: `${fieldId} mocked help`, sourceRef: 'pii25-e2e-mock', decisionQuality: 'VERIFIED', validationMessages: [] };
}

function pii25IntakeMetadata() {
  return {
    tenantContext: 'ui-preview-tenant',
    dependencyStatus: 'READY',
    fieldGroups: [
      { groupId: 'scenario-identity', label: 'Scenario Identity', helpText: '', fields: [pii25Field('borrowerLastName', true), pii25Field('loanNumber', true), pii25Field('channel', true), pii25Field('quoteIntent'), pii25Field('scenarioName'), pii25Field('externalLoanId')] },
      { groupId: 'borrower-credit', label: 'Borrower Credit', helpText: '', fields: [pii25Field('borrowerName', true), pii25Field('contactEmail', true, 'email'), pii25Field('creditScore', false, 'number')] },
      { groupId: 'loan-structure', label: 'Loan Structure', helpText: '', fields: [pii25Field('loanPurpose', true), pii25Field('loanAmount', false, 'number'), pii25Field('purchasePriceOrValue', false, 'number')] },
      { groupId: 'property', label: 'Property', helpText: '', fields: [pii25Field('propertyState', true), pii25Field('propertyZip', true), pii25Field('propertyCounty')] },
      { groupId: 'income-assets', label: 'Income Assets', helpText: '', fields: [pii25Field('monthlyIncome', false, 'number'), pii25Field('monthlyDebt', false, 'number'), pii25Field('liquidAssets', false, 'number')] },
      { groupId: 'preferences', label: 'Preferences', helpText: '', fields: [pii25Field('productFamily'), pii25Field('productPreference'), pii25Field('effectiveDate')] },
    ],
    decisionControls: [],
    validationIssues: [],
    auditPackageId: 'audit-pii25',
    replayHashRef: 'replay-pii25',
    fallbackReason: '',
    uiTraceId: 'pii25-e2e',
    quickQuoteState: { minimalFirstStepFields: ['borrowerLastName', 'loanNumber', 'mortgageType'], progressiveSectionOrder: ['scenario-identity', 'borrower-credit', 'loan-structure', 'property', 'income-assets', 'preferences'], quoteServiceRequiredFacts: ['scenarioId'], backendOwnedFactSources: ['scenario-service', 'quote-service'], blockedByContracts: [], fallbackReason: '' },
  };
}

function pii25DraftScenario() {
  return {
    scenarioId: 'scenario-e2e-pii25',
    scenarioVersion: 3,
    status: 'DRAFT_INCOMPLETE',
    intake: {
      quoteIntent: 'Purchase',
      channel: 'Retail',
      borrowerName: 'Sarah Borrower',
      contactEmail: 'sarah.borrower@example.com',
      loanPurpose: 'Purchase',
      propertyState: 'CA',
      propertyZip: '90210',
    },
  };
}

function runIdFromUrl(url: string) {
  return new URL(url).pathname.match(/\/quote-runs\/([^/]+)\/offers/)?.[1] ?? 'e2e-run';
}

function pii25OfferComparison(runId: string) {
  return {
    runId,
    status: 'QUOTE_SERVICE_EVIDENCE_VISIBLE',
    offers: [
      {
        offerId: 'offer-e2e-primary',
        rank: 1,
        productLabel: 'Backend-ranked offer',
        payment: 'payment-ref-required',
        apr: 'apr-ref-required',
        confidence: 'score:backend-owned',
        rankScore: 'rank-score-ref-required',
        rationaleChips: ['Rank supplied by mocked quote-service evidence'],
        scenarioFlags: ['E2E_QUOTE_RUN_CREATED'],
        explanationStatus: 'AVAILABLE',
        sourceScenarioId: 'scenario-e2e-pii25',
        scenarioVersion: 2,
        upstreamRefs: ['quote-service:offers'],
        lockEligibilityRefs: ['lock-eligibility:pending:offer-e2e-primary'],
        snapshotRefs: [`snapshot:quote-service:run:${runId}`],
        auditIds: ['audit:quote-ready-required'],
        explanationSections: ['ranking', 'comparison', 'detail'],
      },
    ],
    sortOptions: ['rank', 'confidence', 'payment'],
    selectedOfferId: null,
    commitBlocked: false,
    fallbackReason: 'Mocked quote-service offer evidence is present for the E2E pipeline-to-offers path.',
    requiredFacts: ['scenarioVersion'],
    backendRefs: ['quote-service.ranking'],
    uiTraceId: 'pii25-e2e-offers',
    events: ['OfferListRendered'],
  };
}

export const apiHelper = new ApiHelper();
