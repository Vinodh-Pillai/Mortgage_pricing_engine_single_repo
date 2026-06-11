export type ScreenVisualState = 'loading' | 'empty' | 'blocked' | 'needs-attention' | 'ready';

export interface ScreenEvidence {
  screenId: string;
  timestamp: string;
  state: ScreenVisualState;
  dataRefs: string[];
  screenshotRef?: string;
  blockers: string[];
}

export interface ScreenProps {
  tenantId: string;
  runId?: string;
  optionId?: string;
  caseId?: string;
  partnerId?: string;
  uiTraceId: string;
  onEvidenceCapture: (evidence: ScreenEvidence) => void;
  routeParams?: Record<string, string>;
}

export interface ValidationResult {
  valid: boolean;
  blockers: string[];
}
