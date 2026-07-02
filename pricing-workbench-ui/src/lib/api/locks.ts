export type LockWorkflowView = {
  runId: string;
  selectedOfferId: string | null;
  status: 'READY' | 'BLOCKED' | string;
  lockDisabled: boolean;
  blockers: string[];
  blockerDetails?: LockBlockerView[];
  disclosureText: string;
  nextAction: string;
  uiTraceId: string;
  events: string[];
  dependencyStatus: string;
  selectedQuoteRefs?: string[];
  freshnessChecks?: LockLifecycleCheck[];
  requiredEvidence?: string[];
  stateTransitions?: LockStateTransition[];
  auditGroups?: LockAuditGroup[];
};

export type LockLifecycleCheck = {
  label: string;
  status: string;
  sourceRef: string;
  remediation: string;
};

export type LockBlockerView = {
  code: string;
  message: string;
  remediation: string;
};

export type LockStateTransition = {
  fromState: string;
  toState: string;
  eventId: string;
  status: string;
};

export type LockAuditGroup = {
  eventId: string;
  label: string;
  evidenceRefs: string[];
  replayHash: string;
  exportRef: string;
};

export type LockManagementRecord = {
  lockId: string;
  runId: string;
  borrowerRef: string;
  status: string;
  expiresAt: string | null;
  investorDeliveryStatus: string;
  auditRefs: string[];
  blockers: string[];
  availableActions: LockManagementAction[];
  actionBlockers?: Partial<Record<LockManagementAction, string>>;
  expiryStatus?: string;
};

export type LockManagementAction = 'read' | 'detail' | 'approve' | 'extend' | 'relock' | 'cancel' | 'deliver';

export type LockManagementView = {
  tenantContext: string;
  dependencyStatus: string;
  locks: LockManagementRecord[];
  uiTraceId: string;
  events: string[];
  pendingCount?: number;
  expiringCount?: number;
};

export type LockManagementActionResult = {
  status: 'ACCEPTED' | 'BLOCKED' | string;
  message: string;
  auditRef?: string | null;
  blockers?: string[];
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
  auditGroups?: LockAuditGroup[];
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

export async function fetchLockManagement(
  tenantId: string,
  fetchImpl: typeof fetch = fetch,
): Promise<LockManagementView> {
  const response = await fetchImpl(`/api/v1/tenants/${encodeURIComponent(tenantId)}/locks`, {
    headers: traceHeaders,
  });
  if (response.status === 404) throw new Error('Lock management contract is not available from lock-service.');
  if (!response.ok) throw new Error('Lock management records are temporarily unavailable.');
  return normalizeLockManagementView(await response.json(), tenantId);
}

export async function requestLockManagementAction(
  tenantId: string,
  lockId: string,
  action: LockManagementAction,
  fetchImpl: typeof fetch = fetch,
): Promise<LockManagementActionResult> {
  const response = await fetchImpl(`/api/v1/tenants/${encodeURIComponent(tenantId)}/locks/${encodeURIComponent(lockId)}/actions/${encodeURIComponent(action)}`, {
    method: 'POST',
    headers: {
      ...traceHeaders,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ action }),
  });
  if (response.status === 404) return { status: 'BLOCKED', message: `Lock-service does not expose the ${action} action contract for this lock.`, blockers: ['missing-lock-action-contract'] };
  if (!response.ok) throw new Error(`Lock ${action} action is temporarily unavailable.`);
  return (await response.json()) as LockManagementActionResult;
}

function normalizeLockManagementView(raw: unknown, tenantId: string): LockManagementView {
  const record = objectRecord(raw);
  const rawLocks = arrayValue(record.locks) ?? arrayValue(record.records) ?? arrayValue(record.items) ?? [];
  return {
    tenantContext: stringValue(record.tenantContext ?? record.tenantId) || tenantId,
    dependencyStatus: stringValue(record.dependencyStatus ?? record.status) || 'READY',
    uiTraceId: stringValue(record.uiTraceId) || 'lock-management-live-ui',
    events: stringArray(record.events),
    pendingCount: numberValue(record.pendingCount),
    expiringCount: numberValue(record.expiringCount),
    locks: rawLocks.map(normalizeLockRecord).filter((lock): lock is LockManagementRecord => Boolean(lock)),
  };
}

function normalizeLockRecord(raw: unknown): LockManagementRecord | null {
  const record = objectRecord(raw);
  const lockId = stringValue(record.lockId ?? record.id ?? record.lockRef);
  if (!lockId) return null;
  return {
    lockId,
    runId: stringValue(record.runId ?? record.quoteRunId ?? record.quoteId) || 'run-ref-unavailable',
    borrowerRef: stringValue(record.borrowerRef ?? record.borrowerName ?? record.loanNumber) || 'borrower-ref-unavailable',
    status: stringValue(record.status ?? record.lockStatus) || 'UNKNOWN',
    expiresAt: stringValue(record.expiresAt ?? record.expirationTime ?? record.lockExpiration) || null,
    investorDeliveryStatus: stringValue(record.investorDeliveryStatus ?? record.deliveryStatus) || 'not supplied',
    auditRefs: stringArray(record.auditRefs ?? record.evidenceRefs),
    blockers: stringArray(record.blockers),
    availableActions: stringArray(record.availableActions ?? record.actions).filter(isLockAction),
    actionBlockers: actionBlockerRecord(record.actionBlockers),
    expiryStatus: stringValue(record.expiryStatus) || undefined,
  };
}

function isLockAction(value: string): value is LockManagementAction {
  return ['read', 'detail', 'approve', 'extend', 'relock', 'cancel', 'deliver'].includes(value);
}

function actionBlockerRecord(value: unknown): Partial<Record<LockManagementAction, string>> {
  const record = objectRecord(value);
  return Object.fromEntries(Object.entries(record).filter(([key]) => isLockAction(key)).map(([key, blocker]) => [key, stringValue(blocker)])) as Partial<Record<LockManagementAction, string>>;
}

function objectRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function arrayValue(value: unknown): unknown[] | null {
  return Array.isArray(value) ? value : null;
}

function stringValue(value: unknown) {
  if (value === null || value === undefined) return '';
  return String(value);
}

function numberValue(value: unknown): number | undefined {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) return parsed;
  }
  return undefined;
}

function stringArray(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return value.map(stringValue).filter(Boolean);
}
