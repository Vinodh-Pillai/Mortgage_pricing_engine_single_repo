export type OpsCaseSummary = {
  caseId: string;
  priority: string;
  ageLabel: string;
  slaState: string;
  owner: string;
  status: string;
  contextSummary: string;
};

export type OpsCaseTimelineEvent = {
  eventId: string;
  eventType: string;
  summary: string;
};

export type OpsCaseListView = {
  tenantContext: string;
  cases: OpsCaseSummary[];
  uiTraceId: string;
  events: string[];
};

export type OpsCaseDetail = OpsCaseSummary & {
  tenantContext: string;
  timeline: OpsCaseTimelineEvent[];
  evidencePacketIds: string[];
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
