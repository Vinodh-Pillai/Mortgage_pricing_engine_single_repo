import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { tenantOnboardingScreenModule, TenantOnboardingScreen } from './tenant';
import { productManagementScreenModule, ProductManagementScreen } from './product';
import { rateSheetIntakeScreenModule, RateSheetIntakeScreen } from './ratesheet';
import { pricingAnalysisScreenModule, PricingAnalysisScreen } from './pricing';
import { lockManagementScreenModule, LockManagementScreen } from './locks';
import { buildNavigationTree, navigationGroups } from '../layout/navigation';
import { workbenchModules } from './workbenchShell/WorkbenchShell';
import { matchAppRoute } from '../routing/routes';

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

function json(body: unknown, status = 200) {
  return { ok: status >= 200 && status < 300, status, json: async () => body } as Response;
}

describe('PII-25-S04 functionality page modules', () => {
  it('registers ScreenModule contracts and routes for all five pages', () => {
    expect(tenantOnboardingScreenModule.evidenceTarget).toBe('.local-harness/evidence/PII-25-S04/tenant-onboarding.json');
    expect(productManagementScreenModule.stateCoverage).toEqual(expect.arrayContaining(['loading', 'empty', 'blocked', 'needs-attention', 'ready']));
    expect(rateSheetIntakeScreenModule.match('/pricing/rate-sheets/new')).toBe(true);
    expect(pricingAnalysisScreenModule.match('/pricing/analysis/run-001')).toBe(true);
    expect(lockManagementScreenModule.match('/locks/lock-001')).toBe(true);
    expect(matchAppRoute('/pricing/rate-sheets/new').sourceModuleId).toBe('rate-sheet-intake');
    expect(matchAppRoute('/locks/lock-001').sourceModuleId).toBe('lock-management');
  });

  it('adds navigation groups and persona-visible entries', () => {
    const items = buildNavigationTree(workbenchModules, 'run-test', 'pricing-analyst');
    expect(items.map((item) => item.id)).toEqual(expect.arrayContaining(['product-management', 'rate-sheet-intake', 'pricing-analysis']));
    expect(navigationGroups(items)).toEqual(expect.arrayContaining(['Products', 'Pricing']));
  });
});

describe('PII-25-S04 page behavior', () => {
  it('TenantOnboardingTest.rendersAllSections', async () => {
    const onEvidenceCapture = vi.fn();
    render(<TenantOnboardingScreen visualState="ready" onEvidenceCapture={onEvidenceCapture} />);
    expect(screen.getByRole('heading', { name: 'Tenant Onboarding' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Workspace Setup' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Identity Configuration' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Launch Checklist' })).toBeInTheDocument();
    await waitFor(() => expect(onEvidenceCapture).toHaveBeenCalledWith(expect.objectContaining({ screenId: 'tenant-onboarding', state: 'ready' })));
  });

  it('ProductManagementTest.tableSortsFilters', () => {
    render(<ProductManagementScreen />);
    const table = screen.getByRole('table', { name: /Product catalog records/i });
    expect(within(table).getByText('Purchase product draft')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText(/Filter records/i), { target: { value: 'Refinance' } });
    expect(within(table).getByText('Refinance product draft')).toBeInTheDocument();
    expect(within(table).queryByText('Purchase product draft')).not.toBeInTheDocument();
  });

  it('RateSheetIntakeTest.inspectsSelectedSourceAndKeepsPublishParserGated', async () => {
    const onEvidenceCapture = vi.fn();
    window.history.pushState({}, '', '?tenantId=tenant-live');
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = new URL(input.toString(), window.location.origin);
      expect(url.pathname).toBe('/api/v1/tenants/tenant-live/rate-sheets/uploads');
      expect((init?.body as FormData).get('sourceHash')).toMatch(/^fnv1a-32:/);
      expect((init?.body as FormData).get('sourceHash')).not.toBe('hash pending');
      return json({
      uploadId: 'upload-1',
      status: 'VALIDATION_BLOCKED',
      sourceHash: 'fnv1a-32:test',
      parsedRows: [{ rowId: 'row-1', rowNumber: 1, productRef: 'backend-product-ref', rateRef: 'backend-rate-ref', status: 'BLOCKED', validationIssues: [{ rowNumber: 1, column: 'rate', severity: 'BLOCKING', message: 'Rate value missing from backend parser output.' }] }],
      validationIssues: [{ rowNumber: 1, column: 'rate', severity: 'BLOCKING', message: 'Rate value missing from backend parser output.' }],
      publishReady: false,
      auditRefs: ['audit:upload-1'],
      uiTraceId: 'rate-sheet-test',
      });
    });
    vi.stubGlobal('fetch', fetchMock);
    render(<RateSheetIntakeScreen onEvidenceCapture={onEvidenceCapture} />);
    const file = new File(['workbook-bytes'], 'tenant-rates.xlsx', { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
    fireEvent.change(screen.getByLabelText(/Rate sheet source file/i), { target: { files: [file] } });
    expect(screen.getByRole('button', { name: /Upload and parse/i })).toBeDisabled();
    expect(await screen.findByText(/tenant-rates.xlsx · XLSX/i)).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText(/fnv1a-32:/i)).toBeInTheDocument());
    await waitFor(() => expect(screen.getByRole('button', { name: /Upload and parse/i })).toBeEnabled());
    fireEvent.click(screen.getByRole('button', { name: /Upload and parse/i }));
    expect(await screen.findByText(/1 parser-backed rows loaded/i)).toBeInTheDocument();
    expect(screen.getByText(/backend-product-ref/i)).toBeInTheDocument();
    expect(screen.getAllByText(/Rate value missing from backend parser output/i).length).toBeGreaterThan(0);
    expect(screen.getByRole('button', { name: /Publish/i })).toBeDisabled();
    await waitFor(() => expect(onEvidenceCapture).toHaveBeenCalledWith(expect.objectContaining({ screenId: 'rate-sheet-intake', state: 'needs-attention' })));
  });

  it('PricingAnalysisTest.rendersRunSpecificWaterfallRecords', async () => {
    window.history.pushState({}, '', '?tenantId=tenant-live');
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = new URL(input.toString(), window.location.origin);
      expect(url.pathname).toBe('/api/v1/tenants/tenant-live/quote-runs/run-live-1/pricing-waterfall');
      return json({
      tenantContext: 'tenant-live',
      runId: 'run-live-1',
      status: 'READY',
      restrictedValuesVisible: false,
      dependencyStatus: 'READY',
      baseSelection: { selectionId: 'selection-1', gridVersionRef: 'grid-v1', selectedNoteRate: { value: '6.5', redacted: false, reason: null }, basePrice: { value: '101.0', redacted: false, reason: null }, ledgerSteps: ['base'] },
      finalPrice: { finalPriceId: 'final-1', roundedFinalPrice: { value: '100.5', redacted: false, reason: null }, ledger: [{ ordinal: 1, section: 'Base Rate', step: 'Base selection', inputValue: { value: '6.5', redacted: false, reason: null }, operation: 'SELECT', outputValue: { value: '101.0', redacted: false, reason: null }, configRef: 'config:base', reasonCode: 'BASE', roundingMode: null }], adjustmentRefs: [], marginRefs: [], roundingTraceRefs: [] },
      blockers: [], versionRefs: ['version:1'], auditRefs: ['audit:1'], replayHash: 'replay:1', versionGraphHash: 'graph:1', resultHash: 'result:1', evidenceHash: 'evidence:1', uiTraceId: 'pricing-analysis-test', events: [], fallbackReason: '',
      });
    }));
    render(<PricingAnalysisScreen runId="run-live-1" />);
    expect(await screen.findByRole('heading', { name: /Pricing Analysis for run run-live-1/i })).toBeInTheDocument();
    expect(screen.getByRole('table', { name: /Pricing analysis records/i })).toHaveTextContent(/Base selection/i);
    expect(screen.getByText(/browser does not calculate prices/i)).toBeInTheDocument();
  });

  it('LockManagementTest.showsLiveLockRecordsAndEnabledActionsOnlyFromApi', async () => {
    window.history.pushState({}, '', '?tenantId=tenant-live');
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = new URL(input.toString(), window.location.origin);
      if (url.pathname.includes('/actions/detail')) {
        return json({ status: 'ACCEPTED', message: 'detail read', auditRef: 'audit:detail-read', blockers: [] });
      }
      expect(url.pathname).toBe('/api/v1/tenants/tenant-live/locks');
      return json({
      tenantContext: 'tenant-live',
      dependencyStatus: 'READY',
      uiTraceId: 'locks-test',
      events: ['lock:list'],
      locks: [
        { lockId: 'lock-001', runId: 'run-001', borrowerRef: 'borrower-ref-1', status: 'REQUESTED', expiresAt: '2026-07-01T00:00:00Z', investorDeliveryStatus: 'PENDING', auditRefs: ['audit:lock-001'], blockers: [], availableActions: ['read', 'detail'], actionBlockers: { extend: 'LOCK_EXTENSION_REQUIRED_FIELDS_NOT_SUPPLIED', relock: 'LOCK_RELOCK_REQUIRED_FIELDS_NOT_SUPPLIED', deliver: 'LOCK_INVESTOR_DELIVERY_ROUTE_NOT_EXPOSED_BY_LOCK_SERVICE' } },
      ],
      });
    });
    vi.stubGlobal('fetch', fetchMock);
    render(<LockManagementScreen />);
    const table = await screen.findByRole('table', { name: /Lock management records/i });
    expect(screen.getByRole('heading', { name: /^Lock Management$/i })).toBeInTheDocument();
    expect(table).toHaveTextContent(/lock-001/i);
    expect(screen.getByRole('button', { name: /Bulk Detail/i })).toBeDisabled();
    fireEvent.click(within(table).getByRole('checkbox', { name: /Select lock lock-001/i }));
    expect(screen.getByRole('button', { name: /Bulk Detail/i })).toBeEnabled();
    fireEvent.click(screen.getByRole('button', { name: /Bulk Detail/i }));
    expect(await screen.findByText(/Bulk detail recorded for 1 lock/i)).toBeInTheDocument();
    expect(within(table).getByRole('button', { name: /^Detail$/i })).toBeEnabled();
    expect(within(table).getByRole('button', { name: /Extend disabled/i })).toBeDisabled();
    expect(document.body).not.toHaveTextContent(/Local preview/i);
  });
});
