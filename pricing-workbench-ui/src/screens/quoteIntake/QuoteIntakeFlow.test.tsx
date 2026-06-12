import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { QuoteIntakeFlow, initialQuoteIntake } from './QuoteIntakeFlow';
import type { BorrowerIntake, MetadataState, ScenarioIntakeField } from '../../lib/api/quoteRuns';

const metadataState: MetadataState = {
  kind: 'loaded',
  metadata: {
    tenantContext: 'ui-preview-tenant',
    dependencyStatus: 'READY',
    decisionControls: [],
    validationIssues: [],
    auditPackageId: 'audit',
    replayHashRef: 'replay',
    fallbackReason: '',
    uiTraceId: 'brw-s01-local-trace',
    quickQuoteState: {
      minimalFirstStepFields: ['quoteIntent', 'channel', 'scenarioName', 'externalLoanId'],
      progressiveSectionOrder: ['scenario-identity', 'borrower-credit', 'loan-structure', 'property', 'income-assets', 'preferences'],
      quoteServiceRequiredFacts: ['scenarioId', 'scenarioVersion'],
      backendOwnedFactSources: ['scenario-service', 'quote-service'],
      blockedByContracts: [],
      fallbackReason: '',
    },
    fieldGroups: [
      { groupId: 'scenario-identity', label: 'Scenario Identity', helpText: 'identity', fields: [field('quoteIntent', true), field('channel', true), field('scenarioName', false), field('externalLoanId', false)] },
      { groupId: 'borrower-credit', label: 'Borrower Credit', helpText: 'borrower', fields: [field('borrowerName', true), field('contactEmail', true, 'email')] },
      { groupId: 'loan-structure', label: 'Loan Structure', helpText: 'loan', fields: [field('loanAmount', false, 'number')] },
      { groupId: 'property', label: 'Property', helpText: 'property', fields: [field('propertyState', true), field('propertyZip', true)] },
      { groupId: 'income-assets', label: 'Income Assets', helpText: 'income', fields: [field('monthlyIncome', false, 'number')] },
      { groupId: 'preferences', label: 'Preferences', helpText: 'launch', fields: [field('productFamily', false), field('effectiveDate', false)] },
    ],
  },
};

afterEach(() => {
  cleanup();
  window.localStorage.clear();
  vi.unstubAllGlobals();
});

describe('QuoteIntakeFlowTest', () => {
  it('rendersOnlyOneStepAtATime', () => {
    render(<QuoteIntakeFlow metadataState={metadataState} />);
    expect(screen.getByText('Scenario Identity')).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: /Quote intent/i })).toBeInTheDocument();
    expect(screen.queryByRole('textbox', { name: /Borrower name/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('spinbutton', { name: /Loan amount/i })).not.toBeInTheDocument();
  });

  it('createsDraftOnStep1SubmitAndUpdatesDraftOnNextStep', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = input.toString();
      if (url.endsWith('/scenarios') && init?.method === 'POST') return json({ scenarioId: 'scenario-1', scenarioVersion: 1 });
      if (url.endsWith('/borrower-credit') && init?.method === 'PATCH') return json({ scenarioId: 'scenario-1', scenarioVersion: 2 });
      if (url.endsWith('/borrower-credit/validate')) return json({ passed: true, status: 'PASSED', message: 'ok', blockers: {} });
      return json({});
    });
    vi.stubGlobal('fetch', fetchMock);
    render(<QuoteIntakeFlow metadataState={metadataState} />);
    fireEvent.change(screen.getByRole('textbox', { name: /Quote intent/i }), { target: { value: 'Purchase' } });
    fireEvent.change(screen.getByRole('textbox', { name: /Channel/i }), { target: { value: 'Retail' } });
    fireEvent.click(screen.getByRole('button', { name: /Create draft and continue/i }));

    expect(await screen.findByText('Borrower & Credit')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/tenants/ui-preview-tenant/scenarios', expect.objectContaining({ method: 'POST' }));

    fireEvent.change(screen.getByRole('textbox', { name: /Borrower name/i }), { target: { value: 'Alex Borrower' } });
    fireEvent.change(screen.getByRole('textbox', { name: /Contact email/i }), { target: { value: 'alex@example.test' } });
    fireEvent.click(screen.getByRole('button', { name: /Save and continue/i }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/v1/tenants/ui-preview-tenant/scenarios/scenario-1/borrower-credit', expect.objectContaining({ method: 'PATCH' })));
  });

  it('validatesRequiredFieldsWithAriaAndKeyboardNavigation', async () => {
    render(<QuoteIntakeFlow metadataState={metadataState} />);
    fireEvent.keyDown(screen.getByRole('textbox', { name: /Quote intent/i }), { key: 'Enter' });
    expect(await screen.findByText('Quote Intent is required.')).toHaveAttribute('role', 'alert');
    expect(screen.getByRole('textbox', { name: /Quote intent/i })).toHaveAttribute('aria-describedby', expect.stringContaining('quoteIntent-error'));
    fireEvent.keyDown(screen.getByRole('textbox', { name: /Quote intent/i }), { key: 'Escape' });
    expect(screen.getByText('Scenario Identity')).toBeInTheDocument();
  });

  it('launchesQuoteRunOnStep6AndShowsBlockersWhenBlocked', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = input.toString();
      if (url.endsWith('/quote-runs') && init?.method === 'POST') return json({ status: 'BLOCKED', runId: null, nextRoute: null, validationSummary: { passed: false, status: 'BLOCKED', message: 'Missing quote setup.', blockers: { productFamily: 'Product family is required.' } }, uiTraceId: 'trace', events: [], fallbackMode: true, dependencyStatus: '', auditPackageId: null, replayHashRef: null, validationIssues: [], missingContractBlockers: ['QUOTE_SERVICE_CONTRACT_REQUIRED'] });
      return json({ scenarioId: 'scenario-1', scenarioVersion: 6, passed: true, status: 'PASSED', message: 'ok', blockers: {} });
    });
    vi.stubGlobal('fetch', fetchMock);
    render(<QuoteIntakeFlow metadataState={metadataState} intake={{ ...initialQuoteIntake, quoteIntent: 'Purchase', channel: 'Retail', borrowerName: 'Alex', contactEmail: 'alex@example.test', propertyState: 'CA', propertyZip: '90001' }} />);
    for (let i = 0; i < 5; i += 1) fireEvent.click(await screen.findByRole('button', { name: i === 0 ? /Create draft and continue/i : /Save and continue/i }));
    fireEvent.click(await screen.findByRole('button', { name: /Launch quote run/i }));
    expect(await screen.findByText('Missing quote setup.')).toBeInTheDocument();
  });
});

function field(fieldId: keyof BorrowerIntake, required: boolean, dataType: ScenarioIntakeField['dataType'] = 'text'): ScenarioIntakeField {
  return { fieldId, label: String(fieldId).replace(/[A-Z]/g, ' $&').replace(/^./, (c) => c.toUpperCase()), groupId: 'test', dataType, required, helpText: `${fieldId} help`, sourceRef: 'metadata', decisionQuality: 'VERIFIED', validationMessages: [] };
}

function json(body: unknown) {
  return { ok: true, status: 200, json: async () => body } as Response;
}
