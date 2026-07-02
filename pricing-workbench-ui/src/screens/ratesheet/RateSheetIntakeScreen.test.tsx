import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { RateSheetIntakeScreen } from './RateSheetIntakeScreen';

afterEach(() => {
  cleanup();
  window.history.replaceState({}, '', '/');
});

describe('RateSheetIntakeScreen live rate-feed wiring', () => {
  it('blocks PDF/OCR before backend parser execution with the approved extractor message', async () => {
    window.history.replaceState({}, '', '/pricing/rate-sheets?tenantId=tenant-rate');
    render(<RateSheetIntakeScreen />);

    await selectFile('investor.pdf', 'application/pdf', 'pdf bytes');
    fireEvent.click(screen.getByRole('button', { name: /Upload and parse/i }));

    expect(await screen.findByText(/PDF\/OCR rate sheet intake requires an approved external document extractor\/OCR handoff/i)).toBeInTheDocument();
  });

  it('sends CSV parser metadata to the BFF rate-feed upload contract', async () => {
    window.history.replaceState({}, '', '/pricing/rate-sheets?tenantId=tenant-rate');
    const fetchMock = vi.fn(async () => ({
      ok: true,
      status: 201,
      json: async () => ({
        uploadId: 'sheet-1',
        status: 'VALIDATED',
        sourceHash: 'grid-hash',
        parsedRows: [{ rowId: 'row-1', rowNumber: 1, productRef: 'DSCR_PLUS', rateRef: 'noteRate:6.5:lockPeriod:30', status: 'VALIDATED', validationIssues: [] }],
        validationIssues: [],
        publishReady: true,
        auditRefs: ['rate-feed:sheet:sheet-1'],
        uiTraceId: 'rate-sheet-live-intake-ui',
      }),
    }));

    render(<RateSheetIntakeScreen />);
    await selectFile('rates.csv', 'text/csv', 'product,note_rate,lock_period,price\nDSCR_PLUS,6.5,30,101.25\n');
    fireEvent.change(screen.getByLabelText('Investor ID'), { target: { value: '00000000-0000-0000-0000-000000000201' } });
    fireEvent.change(screen.getByLabelText('Channel ID'), { target: { value: '00000000-0000-0000-0000-000000000202' } });
    fireEvent.change(screen.getByLabelText('Product code'), { target: { value: 'DSCR_PLUS' } });
    fireEvent.change(screen.getByLabelText('Effective at'), { target: { value: '2026-07-01T00:00:00Z' } });

    const { uploadRateSheetForParsing } = await import('../../lib/api/rateSheetIntake');
    await uploadRateSheetForParsing('tenant-rate', new File(['csv'], 'rates.csv', { type: 'text/csv' }), 'fnv1a-32:feed0001', {
      investorId: '00000000-0000-0000-0000-000000000201',
      channelId: '00000000-0000-0000-0000-000000000202',
      productCode: 'DSCR_PLUS',
      effectiveAt: '2026-07-01T00:00:00Z',
    }, fetchMock as unknown as typeof fetch);

    const [, init] = fetchMock.mock.calls[0] as unknown as Parameters<typeof fetch>;
    const form = init?.body as FormData;
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/tenants/tenant-rate/rate-sheets/uploads', expect.objectContaining({ method: 'POST' }));
    expect(form.get('investorId')).toBe('00000000-0000-0000-0000-000000000201');
    expect(form.get('channelId')).toBe('00000000-0000-0000-0000-000000000202');
    expect(form.get('productCode')).toBe('DSCR_PLUS');
    expect(form.get('effectiveAt')).toBe('2026-07-01T00:00:00Z');
  });

  it('passes parser validation result hash and version hash to the publish API contract', async () => {
    const fetchMock = vi.fn(async () => ({
      ok: true,
      status: 200,
      json: async () => ({ status: 'PUBLISHED', message: 'accepted', auditRefs: ['rate-feed:publish:sheet-1'] }),
    }));

    const { publishRateSheetUpload } = await import('../../lib/api/rateSheetIntake');
    await publishRateSheetUpload('tenant-rate', 'sheet-1', {
      expectedValidationResultHash: 'sha256:validation-job-result',
      expectedVersionHash: 'sha256:rate-sheet-version',
    }, fetchMock as unknown as typeof fetch);

    const [, init] = fetchMock.mock.calls[0] as unknown as Parameters<typeof fetch>;
    const body = JSON.parse(String(init?.body)) as Record<string, string>;
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/tenants/tenant-rate/rate-sheets/uploads/sheet-1/publish', expect.objectContaining({ method: 'POST' }));
    expect(body.expectedValidationResultHash).toBe('sha256:validation-job-result');
    expect(body.expectedVersionHash).toBe('sha256:rate-sheet-version');
  });
});

async function selectFile(name: string, type: string, content: string) {
  const file = new File([content], name, { type, lastModified: Date.parse('2026-07-01T00:00:00Z') });
  fireEvent.change(screen.getByLabelText('Rate sheet source file'), { target: { files: [file] } });
  await waitFor(() => expect(screen.getByText(/fnv1a-32:/i)).toBeInTheDocument());
}
