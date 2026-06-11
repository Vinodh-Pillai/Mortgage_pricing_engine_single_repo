export type ScreenshotMode = 'playwright' | 'html2canvas' | 'none';

export interface ScreenshotResult {
  screenshotRef: string;
  blockers: string[];
}

export interface ScreenshotOptions {
  mode?: ScreenshotMode;
}

type Html2Canvas = (element: HTMLElement, options?: { scale?: number; backgroundColor?: string | null }) => Promise<{ toDataURL(type?: string): string }>;

export async function captureScreenshot(element: HTMLElement | null, path: string, options: ScreenshotOptions = {}): Promise<ScreenshotResult> {
  const mode = options.mode ?? screenshotModeFromEnvironment();
  if (mode === 'none') return { screenshotRef: '', blockers: ['screenshot_capture_disabled'] };
  if (!element) return { screenshotRef: '', blockers: ['screenshot_element_unavailable'] };

  await waitForStableRender();

  if (mode === 'html2canvas') {
    const html2canvas = (globalThis as unknown as { html2canvas?: Html2Canvas }).html2canvas;
    if (!html2canvas) return { screenshotRef: '', blockers: ['html2canvas_unavailable'] };
    await html2canvas(element, { scale: 2, backgroundColor: null });
    return { screenshotRef: path, blockers: [] };
  }

  return { screenshotRef: '', blockers: ['playwright_capture_requires_harness_host'] };
}

export function screenshotModeFromEnvironment(): ScreenshotMode {
  const configured = (globalThis as unknown as { EVIDENCE_SCREENSHOT_MODE?: ScreenshotMode }).EVIDENCE_SCREENSHOT_MODE;
  if (configured === 'playwright' || configured === 'html2canvas' || configured === 'none') return configured;
  return 'none';
}

async function waitForStableRender(): Promise<void> {
  if (typeof document !== 'undefined' && 'fonts' in document) {
    await (document as Document & { fonts: { ready: Promise<unknown> } }).fonts.ready.catch(() => undefined);
  }
  await new Promise((resolve) => setTimeout(resolve, 0));
}
