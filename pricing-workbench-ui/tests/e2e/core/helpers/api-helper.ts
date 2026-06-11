import { APIRequestContext, request } from '@playwright/test';
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

export const apiHelper = new ApiHelper();