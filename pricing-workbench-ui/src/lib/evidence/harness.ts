import { ALL_EVIDENCE_STATES, type EvidenceState, type ValidationResult } from './types';
import { DEFAULT_EVIDENCE_ROOT, evidenceDirectory, readManifest, type EvidenceFileAdapter } from './writer';

export interface ValidateEvidenceOptions {
  rootDir?: string;
  adapter: EvidenceFileAdapter;
  requiredStates?: EvidenceState[];
  requiredScreenIds?: string[];
}

export async function validateEvidence(storyId: string, options: ValidateEvidenceOptions): Promise<ValidationResult> {
  const rootDir = options.rootDir ?? DEFAULT_EVIDENCE_ROOT;
  const requiredStates = options.requiredStates ?? ALL_EVIDENCE_STATES;
  const manifestPath = `${evidenceDirectory(storyId, rootDir)}/manifest.json`;
  const errors: string[] = [];
  const missing: string[] = [];

  if (!(await options.adapter.exists(manifestPath))) {
    return { passed: false, missing: [`${storyId}:manifest.json`], errors: [] };
  }

  try {
    const manifest = await readManifest(manifestPath, storyId, options.adapter);
    const screenIds = options.requiredScreenIds?.length ? options.requiredScreenIds : Array.from(new Set(manifest.evidence.map((entry) => entry.screenId)));
    for (const screenId of screenIds) {
      for (const state of requiredStates) {
        if (!manifest.evidence.some((entry) => entry.screenId === screenId && entry.state === state)) missing.push(`${screenId}:${state}`);
      }
    }

    for (const entry of manifest.evidence) {
      const evidencePath = `${evidenceDirectory(storyId, rootDir)}/${entry.file}`;
      if (!(await options.adapter.exists(evidencePath))) errors.push(`missing evidence file: ${entry.file}`);
      if (entry.screenshotRef && !(await options.adapter.exists(`${evidenceDirectory(storyId, rootDir)}/${entry.screenshotRef}`))) errors.push(`missing screenshot: ${entry.screenshotRef}`);
    }
  } catch (error) {
    errors.push(error instanceof Error ? error.message : String(error));
  }

  return { passed: missing.length === 0 && errors.length === 0, missing, errors };
}
