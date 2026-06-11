import { useCallback, useMemo, useRef } from 'react';
import { captureScreenshot, type ScreenshotMode } from './screenshot';
import { createViewport, type EvidenceState, type EvidenceTheme, type ScreenEvidence } from './types';
import { evidenceFileName, writeEvidence, type EvidenceFileAdapter } from './writer';

export interface UseEvidenceCaptureOptions {
  uiTraceId?: string;
  routeParams?: Record<string, string>;
  theme?: EvidenceTheme;
  adapter?: EvidenceFileAdapter;
  screenshotMode?: ScreenshotMode;
  rootDir?: string;
}

export function useEvidenceCapture(screenId: string, storyId: string, options: UseEvidenceCaptureOptions = {}) {
  const elementRef = useRef<HTMLElement | null>(null);
  const uiTraceId = useMemo(() => options.uiTraceId ?? `ui-${screenId}-${storyId}`, [options.uiTraceId, screenId, storyId]);

  const capture = useCallback(async (state: EvidenceState, dataRefs: string[] = [], blockers: string[] = []) => {
    if (isProductionBuild()) return null;
    const timestamp = new Date().toISOString();
    const screenshotName = evidenceFileName({ screenId, state, timestamp }).replace(/\.json$/, '.png');
    const screenshot = await captureScreenshot(elementRef.current, screenshotName, { mode: options.screenshotMode });
    const evidence: ScreenEvidence = {
      screenId,
      storyId,
      timestamp,
      state,
      dataRefs,
      screenshotRef: screenshot.screenshotRef,
      blockers: [...blockers, ...screenshot.blockers],
      uiTraceId,
      viewport: currentViewport(),
      theme: options.theme ?? currentTheme(),
      routeParams: options.routeParams ?? currentRouteParams(),
    };

    if (!options.adapter) return evidence;
    await writeEvidence(storyId, evidence, { adapter: options.adapter, rootDir: options.rootDir });
    return evidence;
  }, [options.adapter, options.rootDir, options.routeParams, options.screenshotMode, options.theme, screenId, storyId, uiTraceId]);

  return { capture, elementRef };
}

function currentViewport() {
  if (typeof window === 'undefined') return createViewport();
  return createViewport(window.innerWidth, window.innerHeight);
}

function currentTheme(): EvidenceTheme {
  if (typeof document === 'undefined') return 'light';
  return document.documentElement.classList.contains('dark') ? 'dark' : 'light';
}

function currentRouteParams(): Record<string, string> {
  if (typeof window === 'undefined') return {};
  return Object.fromEntries(new URLSearchParams(window.location.search).entries());
}

function isProductionBuild(): boolean {
  return Boolean((import.meta as unknown as { env?: { PROD?: boolean } }).env?.PROD);
}
