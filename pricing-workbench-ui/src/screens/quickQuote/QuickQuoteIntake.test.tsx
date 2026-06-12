import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import QuickQuoteIntake from './QuickQuoteIntake';
import type { BorrowerIntake, MetadataState } from '../../lib/api/quoteRuns';

const baseIntake: BorrowerIntake = {
  quoteIntent: '',
  channel: '',
  scenarioName: '',
  externalLoanId: '',
  sourceSystem: 'PRICING_WORKBENCH',
  borrowerName: '',
  borrowerRole: 'PRIMARY',
  coBorrowerName: '',
  coBorrowerRole: 'CO_BORROWER',
  contactEmail: '',
  creditStatus: 'AVAILABLE',
  creditScore: '',
  creditScoreSource: 'TRI_MERGE',
  creditReportDate: '',
  creditReadiness: '',
  loanPurpose: '',
  loanAmount: '',
  purchasePriceOrValue: '',
  downPaymentOrEquity: '',
  subordinateFinancingAmount: '0',
  helocDrawnAmount: '0',
  helocLimitAmount: '0',
  lienPosition: 'FIRST',
  termMonths: '360',
  amortizationType: 'FIXED',
  requestedLockPeriodDays: '30',
  propertyState: '',
  propertyCounty: '',
  propertyZip: '',
  propertyType: 'SINGLE_FAMILY',
  occupancyType: 'PRIMARY_RESIDENCE',
  unitCount: '1',
  purchasePrice: '',
  appraisedValue: '',
  condoProjectType: '',
  manufacturedHomeFlag: 'false',
  monthlyIncome: '',
  incomeType: 'W2',
  employmentType: 'SALARIED',
  monthlyDebt: '',
  suppliedDti: '',
  reserveMonths: '',
  incomeVerificationStatus: 'VERIFIED',
  assetVerificationStatus: 'VERIFIED',
  liquidAssets: '',
  reserves: '',
  productFamily: '',
  productPreference: '',
  quoteFilters: '',
  effectiveDate: '',
  actorId: '',
  clientContext: '',
};

const metadataState: MetadataState = {
  kind: 'loaded',
  metadata: {
    tenantContext: 'ui-preview-tenant',
    dependencyStatus: 'NO_UPSTREAMS_CONFIGURED',
    fieldGroups: [],
    decisionControls: ['configuration-owned facts only'],
    validationIssues: [],
    auditPackageId: 'review-package',
    replayHashRef: 'review-ref',
    fallbackReason: 'FALLBACK_STATIC_DEPENDENCIES_UNAVAILABLE',
    uiTraceId: 'brw-s01-local-trace',
    quickQuoteState: {
      minimalFirstStepFields: ['quoteIntent', 'channel'],
      progressiveSectionOrder: ['identity', 'borrower', 'loan', 'property', 'income', 'launch'],
      quoteServiceRequiredFacts: ['borrowerName', 'loanAmount', 'propertyState'],
      backendOwnedFactSources: ['scenario-service', 'quote-service'],
      blockedByContracts: ['SLA contract required'],
      fallbackReason: 'NO_UPSTREAMS_CONFIGURED',
    },
  },
};

function renderQuickQuoteIntake(overrides: Partial<Parameters<typeof QuickQuoteIntake>[0]> = {}) {
  return render(
    <MemoryRouter>
      <QuickQuoteIntake
        intake={baseIntake}
        errors={{}}
        launchState={{ kind: 'idle' }}
        metadataState={metadataState}
        onChange={vi.fn()}
        onRetry={vi.fn()}
        onSubmit={vi.fn()}
        {...overrides}
      />
    </MemoryRouter>,
  );
}

afterEach(() => {
  cleanup();
  window.localStorage.clear();
});

describe('PII-24-S09 progressive quick quote intake', () => {
  it('exposes the accepted borrower field flow through accessible progressive intake controls', () => {
    const onChange = vi.fn();

    renderQuickQuoteIntake({ onChange });

    expect(screen.getByRole('heading', { name: /New prospect intake/i })).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: /Quote intake progress/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Identity\s*in progress/i })).toHaveAttribute('aria-current', 'step');
    expect(screen.getByRole('textbox', { name: /^Quote intent$/i })).toHaveAttribute('aria-invalid', 'false');
    expect(screen.getByRole('textbox', { name: /^Channel$/i })).toHaveAttribute('type', 'text');

    fireEvent.change(screen.getByRole('textbox', { name: /^Quote intent$/i }), { target: { value: 'Purchase quote' } });
    expect(onChange).toHaveBeenCalledWith('quoteIntent', 'Purchase quote');
  });

  it('keeps connected-service gaps visible without inventing pricing rules', () => {
    renderQuickQuoteIntake({
      errors: { quoteIntent: 'Quote intent is required.' },
      launchState: {
        kind: 'blocked',
        validation: { passed: false, status: 'BLOCKED', message: 'Setup needed before connected quote launch.', blockers: { quoteIntent: 'Quote intent is required.' } },
      },
    });

    expect(screen.getAllByRole('alert')[0]).toHaveTextContent(/Setup needed before connected quote launch/i);
    expect(screen.getByText('Quote intent is required.')).toHaveAttribute('role', 'alert');
    expect(screen.getByText(/Capture the borrower and loan facts needed to start a pricing run/i)).toBeInTheDocument();
  });
});
