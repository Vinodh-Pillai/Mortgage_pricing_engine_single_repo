export type EvidenceState = 'loading' | 'empty' | 'blocked' | 'needs-attention' | 'ready';

export type EvidenceTheme = 'dark' | 'light';

export interface EvidenceViewport {
  width: number;
  height: number;
  breakpoint: string;
}

export interface ScreenEvidence {
  screenId: string;
  storyId: string;
  timestamp: string;
  state: EvidenceState;
  dataRefs: string[];
  screenshotRef: string;
  blockers: string[];
  uiTraceId: string;
  viewport: EvidenceViewport;
  theme: EvidenceTheme;
  routeParams: Record<string, string>;
}

export interface EvidenceManifestEntry {
  screenId: string;
  state: EvidenceState;
  file: string;
  screenshotRef: string;
  timestamp: string;
}

export interface EvidenceManifest {
  storyId: string;
  capturedAt: string;
  evidence: EvidenceManifestEntry[];
  summary: {
    totalScreens: number;
    statesCovered: EvidenceState[];
    missingStates: EvidenceState[];
  };
}

export interface ValidationResult {
  passed: boolean;
  missing: string[];
  errors: string[];
}

export const ALL_EVIDENCE_STATES: EvidenceState[] = ['loading', 'empty', 'blocked', 'needs-attention', 'ready'];

export function breakpointForWidth(width: number): string {
  if (width < 640) return 'mobile';
  if (width < 1024) return 'tablet';
  return 'desktop';
}

export function createViewport(width = 0, height = 0): EvidenceViewport {
  return { width, height, breakpoint: breakpointForWidth(width) };
}
