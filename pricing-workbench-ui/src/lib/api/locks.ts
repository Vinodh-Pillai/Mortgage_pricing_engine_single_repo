export type LockWorkflowView = {
  runId: string;
  selectedOfferId: string | null;
  status: 'READY' | 'BLOCKED' | string;
  lockDisabled: boolean;
  blockers: string[];
  disclosureText: string;
  nextAction: string;
  uiTraceId: string;
  events: string[];
  dependencyStatus: string;
};

export type LockConfirmationResult = {
  runId: string;
  selectedOfferId: string | null;
  status: 'CONFIRMED' | 'BLOCKED' | 'CONFLICT' | string;
  lockId: string | null;
  lockStatus: string | null;
  expiresAt: string | null;
  statusRoute: string | null;
  message: string;
  uiTraceId: string;
  events: string[];
  blockers: string[];
};

const traceHeaders = {
  Accept: 'application/json',
  'X-Ui-Trace-Id': 'brw-s04-local-trace',
};

export async function fetchLockWorkflow(
  tenantId: string,
  runId: string,
  selectedOfferId: string | null,
  fetchImpl: typeof fetch = fetch,
): Promise<LockWorkflowView> {
  const query = selectedOfferId ? `?selectedOfferId=${encodeURIComponent(selectedOfferId)}` : '';
  const response = await fetchImpl(`/api/v1/tenants/${encodeURIComponent(tenantId)}/quote-runs/${encodeURIComponent(runId)}/lock${query}`, {
    headers: traceHeaders,
  });
  if (response.status >= 500) throw new Error('BFF lock lifecycle boundary is temporarily unavailable.');
  return (await response.json()) as LockWorkflowView;
}

export async function confirmLock(
  tenantId: string,
  runId: string,
  selectedOfferId: string,
  disclosuresAccepted: boolean,
  fetchImpl: typeof fetch = fetch,
): Promise<LockConfirmationResult> {
  const response = await fetchImpl(`/api/v1/tenants/${encodeURIComponent(tenantId)}/quote-runs/${encodeURIComponent(runId)}/lock/confirm`, {
    method: 'POST',
    headers: {
      ...traceHeaders,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ selectedOfferId, disclosuresAccepted }),
  });
  if (response.status >= 500) throw new Error('BFF lock confirmation boundary is temporarily unavailable.');
  return (await response.json()) as LockConfirmationResult;
}
