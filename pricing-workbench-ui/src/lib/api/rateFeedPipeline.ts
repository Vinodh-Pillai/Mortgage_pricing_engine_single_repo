export type PipelineGovernanceStage = {
  stage: string;
  status: string;
  at?: string;
  actorRef?: string;
  evidenceRef?: string;
};

export type PipelineSampleSimulation = {
  factsSummary: string;
  expectedAdjustment: string;
  actualAdjustment: string;
  status: string;
};

export type RateFeedPipelineRow = {
  sheetId: string;
  ruleBookId: string;
  rateSheet: string;
  investor: string;
  status: string;
  ruleCount: number;
  lastAction: string;
  gridHash: string;
  sourceRowCount: number;
  warningCount: number;
  dimensionsUsed: string[];
  governanceHistory: PipelineGovernanceStage[];
  sampleSimulation: PipelineSampleSimulation;
};

export type RateFeedPipelineView = {
  pipelines: RateFeedPipelineRow[];
  count: number;
  generatedAt: string;
};

const fallbackView: RateFeedPipelineView = {
  count: 3,
  generatedAt: 'local-preview',
  pipelines: [
    {
      sheetId: 'fnma-2026-06',
      ruleBookId: 'rulebook-fnma-2026-06',
      rateSheet: 'FNMA_LLPA_2026_06',
      investor: 'FNMA',
      status: 'PUBLISHED',
      ruleCount: 1247,
      lastAction: 'Approved by pricing-admin; cache invalidated',
      gridHash: 'sha256:preview-fnma-2026-06',
      sourceRowCount: 2847,
      warningCount: 3,
      dimensionsUsed: ['ficoBandKey', 'ltvBandKey', 'loanPurpose', 'propertyType', 'occupancy', 'units', 'state'],
      governanceHistory: [
        { stage: 'DRAFT', status: 'COMPLETED', actorRef: 'rate-feed-service' },
        { stage: 'SIMULATE', status: 'PASSED', actorRef: 'governance-service' },
        { stage: 'APPROVE', status: 'APPROVED', actorRef: 'pricing-admin' },
        { stage: 'PUBLISH', status: 'PUBLISHED', actorRef: 'governance-service', evidenceRef: 'RuleBookPublished.v1' },
      ],
      sampleSimulation: { factsSummary: 'FICO=720-739, LTV=80.01-85, PURCHASE, SFR, PRIMARY, 1', expectedAdjustment: '+75 bps', actualAdjustment: '+75 bps', status: 'MATCH' },
    },
    {
      sheetId: 'fhlmc-2026-06',
      ruleBookId: 'rulebook-fhlmc-2026-06',
      rateSheet: 'FHLMC_LLPA_2026_06',
      investor: 'FHLMC',
      status: 'SIMULATING',
      ruleCount: 1189,
      lastAction: 'Simulating sample scenarios',
      gridHash: 'sha256:preview-fhlmc-2026-06',
      sourceRowCount: 2761,
      warningCount: 1,
      dimensionsUsed: ['ficoBandKey', 'ltvBandKey', 'loanPurpose', 'propertyType', 'occupancy', 'units'],
      governanceHistory: [{ stage: 'DRAFT', status: 'COMPLETED' }, { stage: 'SIMULATE', status: 'RUNNING' }],
      sampleSimulation: { factsSummary: 'FICO=720-739, LTV=80.01-85, RATE_TERM_REFI', expectedAdjustment: 'pending', actualAdjustment: 'pending', status: 'PENDING' },
    },
    {
      sheetId: 'fnma-2026-05',
      ruleBookId: 'rulebook-fnma-2026-05',
      rateSheet: 'FNMA_LLPA_2026_05',
      investor: 'FNMA',
      status: 'SUPERSEDED',
      ruleCount: 1247,
      lastAction: 'Replaced by FNMA_LLPA_2026_06',
      gridHash: 'sha256:preview-fnma-2026-05',
      sourceRowCount: 2847,
      warningCount: 0,
      dimensionsUsed: ['ficoBandKey', 'ltvBandKey', 'loanPurpose', 'propertyType', 'occupancy', 'units', 'state'],
      governanceHistory: [{ stage: 'PUBLISH', status: 'SUPERSEDED' }],
      sampleSimulation: { factsSummary: 'Superseded rule book', expectedAdjustment: 'n/a', actualAdjustment: 'n/a', status: 'ARCHIVED' },
    },
  ],
};

export async function fetchRateFeedPipeline(tenantId = 'ui-preview-tenant', fetchImpl: typeof fetch = fetch): Promise<RateFeedPipelineView> {
  try {
    const response = await fetchImpl(`/api/v1/tenants/${encodeURIComponent(tenantId)}/ratefeed/pipeline`, { headers: { Accept: 'application/json' } });
    if (!response.ok) return fallbackView;
    return (await response.json()) as RateFeedPipelineView;
  } catch {
    return fallbackView;
  }
}
