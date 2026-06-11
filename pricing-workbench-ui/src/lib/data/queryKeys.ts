export const queryKeys = {
  quoteRuns: {
    all: ['quoteRuns'] as const,
    intakeMetadata: (tenantId: string) => [...queryKeys.quoteRuns.all, 'intake-metadata', tenantId] as const,
    launch: (tenantId: string) => [...queryKeys.quoteRuns.all, 'launch', tenantId] as const,
    status: (tenantId: string, runId: string) => [...queryKeys.quoteRuns.all, 'status', tenantId, runId] as const,
    offers: (tenantId: string, runId: string) => [...queryKeys.quoteRuns.all, 'offers', tenantId, runId] as const,
    offerDetail: (tenantId: string, runId: string, optionId: string) => [...queryKeys.quoteRuns.all, 'offers', tenantId, runId, optionId] as const,
    pricingWaterfall: (tenantId: string, runId: string) => [...queryKeys.quoteRuns.all, 'waterfall', tenantId, runId] as const,
    journeyMap: (tenantId: string, runId: string) => [...queryKeys.quoteRuns.all, 'journey', tenantId, runId] as const,
    eligibility: (tenantId: string, runId: string, optionId?: string) => [...queryKeys.quoteRuns.all, 'eligibility', tenantId, runId, optionId] as const,
    lock: (tenantId: string, runId: string, optionId?: string) => [...queryKeys.quoteRuns.all, 'lock', tenantId, runId, optionId] as const,
    whatIf: (tenantId: string, runId: string) => [...queryKeys.quoteRuns.all, 'what-if', tenantId, runId] as const,
    recalculate: (tenantId: string, runId: string) => [...queryKeys.quoteRuns.all, 'recalculate', tenantId, runId] as const,
  },
  partner: {
    all: ['partner'] as const,
    quotes: (partnerId: string, status?: string) => [...queryKeys.partner.all, 'quotes', partnerId, status] as const,
    quoteDetail: (partnerId: string, quoteId: string) => [...queryKeys.partner.all, 'quote-detail', partnerId, quoteId] as const,
    channelWorkbench: (partnerId: string) => [...queryKeys.partner.all, 'channel-workbench', partnerId] as const,
    webhookHealth: (partnerId: string) => [...queryKeys.partner.all, 'webhook-health', partnerId] as const,
  },
  ops: {
    all: ['ops'] as const,
    rateFeeds: () => [...queryKeys.ops.all, 'rate-feeds'] as const,
    performance: () => [...queryKeys.ops.all, 'performance'] as const,
    cases: () => [...queryKeys.ops.all, 'cases'] as const,
    caseDetail: (caseId: string) => [...queryKeys.ops.all, 'case', caseId] as const,
  },
  pricing: {
    all: ['pricing'] as const,
    adjustments: (tenantContext: string) => [...queryKeys.pricing.all, 'adjustments', tenantContext] as const,
    margins: (tenantContext: string) => [...queryKeys.pricing.all, 'margins', tenantContext] as const,
  },
  governance: {
    all: ['governance'] as const,
    admin: () => [...queryKeys.governance.all, 'admin'] as const,
    catalog: () => [...queryKeys.governance.all, 'catalog'] as const,
    customRules: () => [...queryKeys.governance.all, 'custom-rules'] as const,
    customRuleEvidence: (quoteId: string) => [...queryKeys.governance.all, 'custom-rules', 'evidence', quoteId] as const,
    auditReplay: () => [...queryKeys.governance.all, 'audit-replay'] as const,
  },
  compliance: {
    all: ['compliance'] as const,
    evidence: () => [...queryKeys.compliance.all, 'evidence'] as const,
  },
  quality: {
    all: ['quality'] as const,
    dashboard: () => [...queryKeys.quality.all, 'dashboard'] as const,
    evidenceExport: () => [...queryKeys.quality.all, 'evidence-export'] as const,
  },
  mlAdvisory: {
    all: ['ml-advisory'] as const,
    insights: () => [...queryKeys.mlAdvisory.all, 'insights'] as const,
  },
  tenantPlatform: {
    all: ['tenant-platform'] as const,
    coverage: () => [...queryKeys.tenantPlatform.all, 'coverage'] as const,
  },
} as const;

export type QueryKeyFactory = typeof queryKeys;

export function stableQueryKeyHash(queryKey: readonly unknown[]): string {
  return JSON.stringify(queryKey, (_key, value) => {
    if (!value || typeof value !== 'object' || Array.isArray(value)) return value;
    return Object.keys(value)
      .sort()
      .reduce<Record<string, unknown>>((sorted, key) => {
        sorted[key] = (value as Record<string, unknown>)[key];
        return sorted;
      }, {});
  });
}

export function queryKeyIncludesTenant(queryKey: readonly unknown[], tenantId: string): boolean {
  return queryKey.includes(tenantId);
}
