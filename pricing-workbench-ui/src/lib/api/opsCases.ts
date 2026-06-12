export type OpsCaseSummary = {
  caseId: string;
  priority: string;
  ageLabel: string;
  slaState: string;
  owner: string;
  status: string;
  contextSummary: string;
  queue?: string;
  createdAtRef?: string;
  updatedAtRef?: string;
  sourceRefs?: string[];
};

export type OpsCaseTimelineEvent = {
  eventId: string;
  eventType: string;
  summary: string;
  timestampRef?: string;
  actorRef?: string;
  evidenceRefs?: string[];
};

export type OpsCaseMetric = {
  metricId: string;
  label: string;
  value: string;
  sourceRef: string;
  status?: string;
};

export type OpsCaseChart = {
  chartId: string;
  label: string;
  segments: { label: string; value: string; sourceRef: string }[];
};

export type OpsCaseQueueSummary = {
  queueId: string;
  label: string;
  caseCount: string;
  avgAgeRef: string;
  slaBreachCountRef: string;
  oldestCaseRef: string;
  ownerRef: string;
  priorityRefs: string[];
};

export type OpsEscalationSummary = {
  escalationId: string;
  caseId: string;
  escalationLevel: string;
  escalatedTo: string;
  escalatedAtRef: string;
  slaBreachTimeRef: string;
  reason: string;
  status: string;
  auditRefs: string[];
};

export type OpsCaseEvidencePacket = {
  packetId: string;
  packetType: string;
  sourceRef: string;
  downloadRef: string;
};

export type OpsCaseListView = {
  tenantContext: string;
  dependencyStatus?: string;
  dashboardMetrics?: OpsCaseMetric[];
  charts?: OpsCaseChart[];
  queues?: OpsCaseQueueSummary[];
  cases: OpsCaseSummary[];
  escalations?: OpsEscalationSummary[];
  filters?: {
    queues: string[];
    priorities: string[];
    slaStates: string[];
    owners: string[];
    statuses: string[];
  };
  actionsDisabled?: boolean;
  fallbackReason?: string;
  uiTraceId: string;
  events: string[];
};

export type OpsCaseDetail = OpsCaseSummary & {
  tenantContext: string;
  timeline: OpsCaseTimelineEvent[];
  evidencePacketIds: string[];
  evidencePackets?: OpsCaseEvidencePacket[];
  assignmentRefs?: string[];
  noteRefs?: string[];
  statusRefs?: string[];
  escalationRefs?: string[];
  uiTraceId: string;
  events: string[];
};

export type OpsCaseActionResult = {
  caseId: string;
  owner?: string | null;
  status: string;
  message?: string;
  immutableSummary?: string;
  escalationContextPreserved?: boolean;
  downstreamExecuted?: boolean;
  uiTraceId: string;
  events: string[];
};

const opsHeaders = {
  Accept: 'application/json',
  'X-Ui-Trace-Id': 'ops-s06-local-trace',
  'X-Tenant-Context': 'ui-preview-tenant',
};

export async function fetchOpsCases(fetchImpl: typeof fetch = fetch): Promise<OpsCaseListView> {
  const response = await fetchImpl('/api/v1/ops/cases', { headers: opsHeaders });
  if (response.status >= 500) throw new Error('BFF operations case boundary is temporarily unavailable.');
  return (await response.json()) as OpsCaseListView;
}

export async function fetchOpsCaseDetail(caseId: string, fetchImpl: typeof fetch = fetch): Promise<OpsCaseDetail> {
  const response = await fetchImpl(`/api/v1/ops/cases/${encodeURIComponent(caseId)}`, { headers: opsHeaders });
  if (response.status >= 500) throw new Error('BFF operations case detail boundary is temporarily unavailable.');
  return (await response.json()) as OpsCaseDetail;
}

export async function assignOpsCase(
  caseId: string,
  owner: string,
  fetchImpl: typeof fetch = fetch,
): Promise<OpsCaseActionResult> {
  const response = await fetchImpl(`/api/v1/ops/cases/${encodeURIComponent(caseId)}/assign`, {
    method: 'POST',
    headers: { ...opsHeaders, 'Content-Type': 'application/json' },
    body: JSON.stringify({ owner }),
  });
  return (await response.json()) as OpsCaseActionResult;
}

export async function addOpsCaseNote(
  caseId: string,
  note: string,
  fetchImpl: typeof fetch = fetch,
): Promise<OpsCaseActionResult> {
  const response = await fetchImpl(`/api/v1/ops/cases/${encodeURIComponent(caseId)}/notes`, {
    method: 'POST',
    headers: { ...opsHeaders, 'Content-Type': 'application/json' },
    body: JSON.stringify({ note }),
  });
  return (await response.json()) as OpsCaseActionResult;
}

export async function updateOpsCaseStatus(
  caseId: string,
  status: string,
  reason: string,
  resolutionCode: string,
  fetchImpl: typeof fetch = fetch,
): Promise<OpsCaseActionResult> {
  const response = await fetchImpl(`/api/v1/ops/cases/${encodeURIComponent(caseId)}/status`, {
    method: 'POST',
    headers: { ...opsHeaders, 'Content-Type': 'application/json' },
    body: JSON.stringify({ status, reason, resolutionCode, actor: 'pricing-workbench-ui' }),
  });
  return (await response.json()) as OpsCaseActionResult;
}
