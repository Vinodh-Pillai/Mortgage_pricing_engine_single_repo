import { describe, expect, it } from 'vitest';
import { createViewport, type ScreenEvidence } from './types';
import { captureScreenshot } from './screenshot';
import { validateEvidence } from './harness';
import { evidenceFilePath, stableStringify, writeEvidence, type EvidenceFileAdapter } from './writer';

function memoryAdapter(): EvidenceFileAdapter & { files: Map<string, string> } {
  const files = new Map<string, string>();
  return {
    files,
    async ensureDir() {
      return undefined;
    },
    async readText(path: string) {
      return files.get(path) ?? null;
    },
    async writeText(path: string, content: string) {
      files.set(path, content);
    },
    async exists(path: string) {
      return files.has(path);
    },
  };
}

function evidenceFixture(overrides: Partial<ScreenEvidence> = {}): ScreenEvidence {
  return {
    screenId: 'quote-offers',
    storyId: 'PII-24-S05',
    timestamp: '2026-06-11T10:00:00.000Z',
    state: 'ready',
    dataRefs: ['audit-package:ap-1', 'replay:hash-1'],
    screenshotRef: '',
    blockers: [],
    uiTraceId: 'trace-1',
    viewport: createViewport(1440, 900),
    theme: 'light',
    routeParams: { runId: 'run-1' },
    ...overrides,
  };
}

describe('EvidenceWriterTest', () => {
  it('writesJsonAndManifest', async () => {
    const adapter = memoryAdapter();
    const evidence = evidenceFixture();
    const path = await writeEvidence('PII-24-S05', evidence, { adapter });

    expect(path).toBe('.local-harness/evidence/PII-24-S05/quote-offers-ready-20260611T100000Z.json');
    expect(adapter.files.get(path)).toBe(`${stableStringify(evidence)}\n`);
    expect(adapter.files.get('.local-harness/evidence/PII-24-S05/manifest.json')).toContain('"statesCovered": [');
  });

  it('createsDirectoryStructure', () => {
    const path = evidenceFilePath('PII-24-S05', evidenceFixture());
    expect(path).toMatch(/^\.local-harness\/evidence\/PII-24-S05\/quote-offers-ready-/);
  });
});

describe('ScreenshotTest', () => {
  it('html2canvasFallback', async () => {
    const result = await captureScreenshot(document.createElement('div'), 'quote-offers-ready.png', { mode: 'html2canvas' });
    expect(result).toEqual({ screenshotRef: '', blockers: ['html2canvas_unavailable'] });
  });

  it('playwrightCapturesElement', async () => {
    const result = await captureScreenshot(document.createElement('div'), 'quote-offers-ready.png', { mode: 'playwright' });
    expect(result.blockers).toContain('playwright_capture_requires_harness_host');
  });
});

describe('HarnessValidatorTest', () => {
  it('failsWhenStatesMissing', async () => {
    const adapter = memoryAdapter();
    await writeEvidence('PII-24-S05', evidenceFixture({ state: 'ready' }), { adapter });

    const result = await validateEvidence('PII-24-S05', { adapter, requiredScreenIds: ['quote-offers'] });

    expect(result.passed).toBe(false);
    expect(result.missing).toContain('quote-offers:loading');
  });

  it('passesWhenAllStatesCovered', async () => {
    const adapter = memoryAdapter();
    for (const state of ['loading', 'empty', 'blocked', 'needs-attention', 'ready'] as const) {
      await writeEvidence('PII-24-S05', evidenceFixture({ state, timestamp: `2026-06-11T10:00:0${state.length % 5}.000Z` }), { adapter });
    }

    const result = await validateEvidence('PII-24-S05', { adapter, requiredScreenIds: ['quote-offers'] });

    expect(result).toEqual({ passed: true, missing: [], errors: [] });
  });
});
