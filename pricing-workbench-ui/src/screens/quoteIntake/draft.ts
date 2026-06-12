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

const draftStoragePrefix = 'wcpe:quoteIntakeDraft:';
const lastDraftStorageKey = 'wcpe:quoteIntakeDraft:last';

export async function createDraftScenario(
  tenantId: string,
  data: Partial<BorrowerIntake>,
  fetchImpl: typeof fetch = fetch,
): Promise<DraftScenario> {
  const response = await fetchImpl(`/api/v1/tenants/${encodeURIComponent(tenantId)}/scenarios`, {
    method: 'POST',
    headers: jsonHeaders(),
    body: JSON.stringify({ status: 'DRAFT_INCOMPLETE', data }),
  });
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
    headers: jsonHeaders(),
    body: JSON.stringify({ scenarioVersion: version, section, data }),
  });
  if (response.status === 409) throw new Error('Draft scenario version conflict. Reload the draft before continuing.');
  if (response.status >= 500) throw new Error('Draft scenario update is temporarily unavailable.');
  return normalizeDraft(await response.json(), version + 1, scenarioId);
}

export async function getDraftScenario(
  tenantId: string,
  scenarioId: string,
  fetchImpl: typeof fetch = fetch,
): Promise<DraftScenario> {
  const response = await fetchImpl(`/api/v1/tenants/${encodeURIComponent(tenantId)}/scenarios/${encodeURIComponent(scenarioId)}`, {
    headers: {
      Accept: 'application/json',
      'X-Ui-Trace-Id': quoteIntakeTraceId,
    },
  });
  if (response.status >= 500) throw new Error('Draft scenario resume is temporarily unavailable.');
  return normalizeDraft(await response.json(), 1, scenarioId);
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

function jsonHeaders() {
  return {
    Accept: 'application/json',
    'Content-Type': 'application/json',
    'X-Ui-Trace-Id': quoteIntakeTraceId,
  };
}

function normalizeDraft(payload: unknown, fallbackVersion: number, fallbackScenarioId?: string): DraftScenario {
  const draft = payload as Partial<DraftScenario> & { id?: string; version?: number; data?: Partial<BorrowerIntake> };
  return {
    scenarioId: draft.scenarioId ?? draft.id ?? fallbackScenarioId ?? 'draft-local-preview',
    scenarioVersion: draft.scenarioVersion ?? draft.version ?? fallbackVersion,
    status: draft.status,
    intake: draft.intake ?? draft.data,
  };
}
