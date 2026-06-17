import type { BorrowerIntake, IntakeValidation } from '../../lib/api/quoteRuns';
import { quoteIntakeTraceId, type QuoteIntakeSection } from './metadata';

export type DraftScenario = {
  scenarioId: string;
  scenarioVersion: number;
  status?: string;
  intake?: Partial<BorrowerIntake>;
};

export type DraftBackup = DraftScenario & {
  savedAt: string;
  currentStep: number;
  intake: Partial<BorrowerIntake>;
};

export type DraftScenarioLookup = {
  borrowerLastName: string;
  loanNumber: string;
};

const draftStoragePrefix = 'wcpe:quoteIntakeDraft:';
const lastDraftStorageKey = 'wcpe:quoteIntakeDraft:last';

export async function createDraftScenario(
  tenantId: string,
  data: Partial<BorrowerIntake>,
  fetchImpl: typeof fetch = fetch,
): Promise<DraftScenario> {
  const response = await fetchImpl(`/api/v1/tenants/${encodeURIComponent(tenantId)}/scenarios`, {
    method: 'POST',
    headers: jsonHeaders('create', data),
    body: JSON.stringify({ status: 'DRAFT_INCOMPLETE', data, initialFacts: data, externalLoanId: data.loanNumber }),
  });
  if (response.status === 404) throw new Error('Draft scenario creation endpoint is unavailable.');
  if (response.status >= 500) throw new Error('Draft scenario creation is temporarily unavailable.');
  return normalizeDraft(await response.json(), 1);
}

export async function updateDraftScenario(
  tenantId: string,
  scenarioId: string,
  version: number,
  section: QuoteIntakeSection,
  data: Partial<BorrowerIntake>,
  fetchImpl: typeof fetch = fetch,
): Promise<DraftScenario> {
  const response = await fetchImpl(`/api/v1/tenants/${encodeURIComponent(tenantId)}/scenarios/${encodeURIComponent(scenarioId)}/${encodeURIComponent(section)}`, {
    method: 'PATCH',
    headers: jsonHeaders('update', { scenarioId, section, version }),
    body: JSON.stringify({ scenarioVersion: version, section, data }),
  });
  if (response.status === 404) throw new Error('Saved draft scenario could not be found.');
  if (response.status === 409) throw new Error('Draft scenario version conflict. Reload the draft before continuing.');
  if (response.status >= 500) throw new Error('Draft scenario update is temporarily unavailable.');
  return normalizeDraft(await response.json(), version + 1, scenarioId);
}

export async function getDraftScenario(
  tenantId: string,
  scenarioIdOrLookup: string | DraftScenarioLookup,
  fetchImpl: typeof fetch = fetch,
): Promise<DraftScenario> {
  if (typeof scenarioIdOrLookup !== 'string') return getDraftScenarioByLookup(tenantId, scenarioIdOrLookup, fetchImpl);
  const response = await fetchImpl(`/api/v1/tenants/${encodeURIComponent(tenantId)}/scenarios/${encodeURIComponent(scenarioIdOrLookup)}`, {
    headers: {
      Accept: 'application/json',
      'X-Ui-Trace-Id': quoteIntakeTraceId,
    },
  });
  if (response.status === 404) throw new Error('Saved draft scenario could not be found.');
  if (response.status >= 500) throw new Error('Draft scenario resume is temporarily unavailable.');
  return normalizeDraft(await response.json(), 1, scenarioIdOrLookup);
}

async function getDraftScenarioByLookup(
  tenantId: string,
  lookup: DraftScenarioLookup,
  fetchImpl: typeof fetch,
): Promise<DraftScenario> {
  const params = new URLSearchParams({
    borrowerLastName: lookup.borrowerLastName,
    loanNumber: lookup.loanNumber,
    status: 'DRAFT_INCOMPLETE',
  });
  const response = await fetchImpl(`/api/v1/tenants/${encodeURIComponent(tenantId)}/scenarios?${params.toString()}`, {
    headers: {
      Accept: 'application/json',
      'X-Ui-Trace-Id': quoteIntakeTraceId,
    },
  });
  if (response.status === 404) throw new Error('No saved pipeline found.');
  if (response.status >= 500) throw new Error('Draft scenario lookup is temporarily unavailable.');
  const payload = await response.json();
  const candidate = firstDraftCandidate(payload);
  if (!candidate) throw new Error('No saved pipeline found.');
  return normalizeDraft(candidate, 1);
}

export async function validateDraftSection(
  tenantId: string,
  scenarioId: string,
  version: number,
  section: QuoteIntakeSection,
  fetchImpl: typeof fetch = fetch,
): Promise<IntakeValidation> {
  const response = await fetchImpl(`/api/v1/tenants/${encodeURIComponent(tenantId)}/scenarios/${encodeURIComponent(scenarioId)}/${encodeURIComponent(section)}/validate`, {
    method: 'POST',
    headers: jsonHeaders(),
    body: JSON.stringify({ scenarioVersion: version }),
  });
  if (response.status === 404) return { passed: true, status: 'PASSED', message: 'Server validation endpoint is not configured for this local preview.', blockers: {} };
  if (response.status >= 500) throw new Error('Draft scenario validation is temporarily unavailable.');
  return (await response.json()) as IntakeValidation;
}

export function saveDraftBackup(scenarioId: string, scenarioVersion: number, currentStep: number, intake: Partial<BorrowerIntake>) {
  if (typeof window === 'undefined') return;
  const backup: DraftBackup = { scenarioId, scenarioVersion, currentStep, intake, savedAt: new Date().toISOString(), status: 'DRAFT_INCOMPLETE' };
  window.localStorage.setItem(`${draftStoragePrefix}${scenarioId}`, JSON.stringify(backup));
  window.localStorage.setItem(lastDraftStorageKey, scenarioId);
}

export function loadDraftBackup(scenarioId?: string): DraftBackup | null {
  if (typeof window === 'undefined') return null;
  const resolvedId = scenarioId || window.localStorage.getItem(lastDraftStorageKey) || undefined;
  if (!resolvedId) return null;
  const raw = window.localStorage.getItem(`${draftStoragePrefix}${resolvedId}`);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as DraftBackup;
    if (!parsed.scenarioId || typeof parsed.scenarioVersion !== 'number') return null;
    return parsed;
  } catch {
    return null;
  }
}

function jsonHeaders(action = 'draft', seed?: unknown) {
  return {
    Accept: 'application/json',
    'Content-Type': 'application/json',
    'X-Ui-Trace-Id': quoteIntakeTraceId,
    'X-Correlation-Id': quoteIntakeTraceId,
    'Idempotency-Key': `${quoteIntakeTraceId}-${action}-${hashSeed(seed)}`,
  };
}

function firstDraftCandidate(payload: unknown): unknown | null {
  if (Array.isArray(payload)) return payload[0] ?? null;
  const wrapper = payload as { records?: unknown[]; items?: unknown[]; scenarios?: unknown[]; drafts?: unknown[]; data?: unknown[] | unknown };
  if (Array.isArray(wrapper.records)) return wrapper.records[0] ?? null;
  if (Array.isArray(wrapper.items)) return wrapper.items[0] ?? null;
  if (Array.isArray(wrapper.scenarios)) return wrapper.scenarios[0] ?? null;
  if (Array.isArray(wrapper.drafts)) return wrapper.drafts[0] ?? null;
  if (Array.isArray(wrapper.data)) return wrapper.data[0] ?? null;
  return hasDraftIdentity(payload) ? payload : null;
}

function hasDraftIdentity(payload: unknown) {
  const draft = payload as Partial<DraftScenario> & { id?: string };
  return Boolean(draft?.scenarioId ?? draft?.id);
}

function hashSeed(seed: unknown) {
  const text = JSON.stringify(seed ?? 'none');
  let hash = 0;
  for (let index = 0; index < text.length; index += 1) hash = (hash * 31 + text.charCodeAt(index)) >>> 0;
  return hash.toString(36);
}

function normalizeDraft(payload: unknown, fallbackVersion: number, fallbackScenarioId?: string): DraftScenario {
  const draft = payload as Partial<DraftScenario> & { id?: string; version?: number; data?: Partial<BorrowerIntake>; initialFacts?: Partial<BorrowerIntake>; facts?: Partial<BorrowerIntake>; derivedFields?: Partial<BorrowerIntake> };
  return {
    scenarioId: draft.scenarioId ?? draft.id ?? fallbackScenarioId ?? 'draft-local-preview',
    scenarioVersion: draft.scenarioVersion ?? draft.version ?? fallbackVersion,
    status: draft.status,
    intake: draft.intake ?? draft.data ?? draft.initialFacts ?? draft.facts ?? draft.derivedFields,
  };
}
