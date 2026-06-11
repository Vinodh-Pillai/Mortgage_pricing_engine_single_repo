import { ALL_EVIDENCE_STATES, type EvidenceManifest, type EvidenceManifestEntry, type EvidenceState, type ScreenEvidence } from './types';

export interface EvidenceFileAdapter {
  ensureDir(path: string): Promise<void>;
  readText(path: string): Promise<string | null>;
  writeText(path: string, content: string): Promise<void>;
  exists(path: string): Promise<boolean>;
}

export interface WriteEvidenceOptions {
  rootDir?: string;
  adapter: EvidenceFileAdapter;
  requiredStates?: EvidenceState[];
}

export const DEFAULT_EVIDENCE_ROOT = '.local-harness/evidence';
const manifestQueues = new Map<string, Promise<unknown>>();

export function evidenceDirectory(storyId: string, rootDir = DEFAULT_EVIDENCE_ROOT): string {
  return `${trimSlashes(rootDir)}/${safePathSegment(storyId)}`;
}

export function evidenceFileName(evidence: Pick<ScreenEvidence, 'screenId' | 'state' | 'timestamp'>): string {
  return `${safePathSegment(evidence.screenId)}-${evidence.state}-${timestampForFile(evidence.timestamp)}.json`;
}

export function evidenceFilePath(storyId: string, evidence: Pick<ScreenEvidence, 'screenId' | 'state' | 'timestamp'>, rootDir = DEFAULT_EVIDENCE_ROOT): string {
  return `${evidenceDirectory(storyId, rootDir)}/${evidenceFileName(evidence)}`;
}

export async function writeEvidence(storyId: string, evidence: ScreenEvidence, options: WriteEvidenceOptions): Promise<string> {
  if (storyId !== evidence.storyId) {
    throw new Error(`storyId mismatch: ${storyId} does not match evidence.storyId ${evidence.storyId}`);
  }

  const rootDir = options.rootDir ?? DEFAULT_EVIDENCE_ROOT;
  const dir = evidenceDirectory(storyId, rootDir);
  const fileName = evidenceFileName(evidence);
  const filePath = `${dir}/${fileName}`;
  await options.adapter.ensureDir(dir);
  await options.adapter.writeText(filePath, `${stableStringify(evidence)}\n`);
  await appendManifest(storyId, evidence, fileName, options);
  return filePath;
}

export async function appendManifest(storyId: string, evidence: ScreenEvidence, fileName: string, options: WriteEvidenceOptions): Promise<EvidenceManifest> {
  const rootDir = options.rootDir ?? DEFAULT_EVIDENCE_ROOT;
  const manifestPath = `${evidenceDirectory(storyId, rootDir)}/manifest.json`;
  return enqueueManifestUpdate(manifestPath, async () => {
    const existing = await readManifest(manifestPath, storyId, options.adapter);
    const nextEntry: EvidenceManifestEntry = {
      screenId: evidence.screenId,
      state: evidence.state,
      file: fileName,
      screenshotRef: evidence.screenshotRef,
      timestamp: evidence.timestamp,
    };
    const entries = [...existing.evidence, nextEntry].sort((left, right) => left.timestamp.localeCompare(right.timestamp) || left.screenId.localeCompare(right.screenId) || left.state.localeCompare(right.state));
    const manifest = createManifest(storyId, entries, options.requiredStates ?? ALL_EVIDENCE_STATES);
    await options.adapter.writeText(manifestPath, `${stableStringify(manifest)}\n`);
    return manifest;
  });
}

export function createManifest(storyId: string, evidence: EvidenceManifestEntry[], requiredStates: EvidenceState[] = ALL_EVIDENCE_STATES): EvidenceManifest {
  const statesCovered = unique(evidence.map((entry) => entry.state), ALL_EVIDENCE_STATES);
  return {
    storyId,
    capturedAt: evidence.length > 0 ? evidence[evidence.length - 1].timestamp : new Date(0).toISOString(),
    evidence,
    summary: {
      totalScreens: new Set(evidence.map((entry) => entry.screenId)).size,
      statesCovered,
      missingStates: requiredStates.filter((state) => !statesCovered.includes(state)),
    },
  };
}

export async function readManifest(path: string, storyId: string, adapter: EvidenceFileAdapter): Promise<EvidenceManifest> {
  const text = await adapter.readText(path);
  if (!text) return createManifest(storyId, []);
  const parsed = JSON.parse(text) as EvidenceManifest;
  if (parsed.storyId !== storyId) throw new Error(`manifest storyId mismatch: ${parsed.storyId}`);
  return parsed;
}

export function stableStringify(value: unknown): string {
  return JSON.stringify(sortKeys(value), null, 2);
}

function sortKeys(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(sortKeys);
  if (!value || typeof value !== 'object') return value;
  return Object.fromEntries(Object.entries(value as Record<string, unknown>).sort(([left], [right]) => left.localeCompare(right)).map(([key, child]) => [key, sortKeys(child)]));
}

function timestampForFile(timestamp: string): string {
  return timestamp.replace(/[-:]/g, '').replace(/\.\d{3}Z$/, 'Z');
}

function safePathSegment(value: string): string {
  const segment = value.trim().replace(/[^a-zA-Z0-9._-]/g, '-');
  if (!segment || segment === '.' || segment === '..') throw new Error(`invalid path segment: ${value}`);
  return segment;
}

function trimSlashes(value: string): string {
  return value.replace(/[\\/]+$/g, '');
}

function unique(states: EvidenceState[], order: EvidenceState[]): EvidenceState[] {
  const set = new Set(states);
  return order.filter((state) => set.has(state));
}

function enqueueManifestUpdate<T>(manifestPath: string, task: () => Promise<T>): Promise<T> {
  const previous = manifestQueues.get(manifestPath) ?? Promise.resolve();
  const next = previous.catch(() => undefined).then(task);
  manifestQueues.set(manifestPath, next.finally(() => {
    if (manifestQueues.get(manifestPath) === next) manifestQueues.delete(manifestPath);
  }));
  return next;
}
