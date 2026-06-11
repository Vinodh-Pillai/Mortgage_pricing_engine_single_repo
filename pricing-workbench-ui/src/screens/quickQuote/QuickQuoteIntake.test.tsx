import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
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

afterEach(() => {
  cleanup();
  window.localStorage.clear();
});

describe('PII-24-S09 progressive quick quote intake', () => {
  it('exposes the accepted borrower field flow through accessible progressive intake controls', () => {
    const onChange = vi.fn();

    render(
      <QuickQuoteIntake
        intake={baseIntake}
        errors={{}}
        launchState={{ kind: 'idle' }}
        metadataState={metadataState}
        onChange={onChange}
        onRetry={vi.fn()}
        onSubmit={vi.fn()}
      />,
    );

    expect(screen.getByRole('heading', { name: /Progressive quick quote intake/i })).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: /Quote intake progress/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Step 2: Borrower in progress/i })).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: /^Borrower name \*$/i })).toHaveAttribute('aria-invalid', 'false');
    expect(screen.getByRole('textbox', { name: /Contact email/i })).toHaveAttribute('type', 'email');

    fireEvent.click(screen.getByRole('button', { name: /Step 2: Borrower in progress/i }));
    expect(screen.getByText(/Active step: Step 2 Borrower/i)).toBeInTheDocument();

    fireEvent.change(screen.getByRole('textbox', { name: /^Borrower name \*$/i }), { target: { value: 'Alex Borrower' } });
    expect(onChange).toHaveBeenCalledWith('borrowerName', 'Alex Borrower');
  });

  it('keeps connected-service gaps visible without inventing pricing rules', () => {
    render(
      <QuickQuoteIntake
        intake={baseIntake}
        errors={{ borrowerName: 'Borrower name is required.' }}
        launchState={{ kind: 'idle' }}
        metadataState={metadataState}
        onChange={vi.fn()}
        onRetry={vi.fn()}
        onSubmit={vi.fn()}
      />,
    );

    expect(screen.getAllByRole('alert')[0]).toHaveTextContent(/Setup needed before connected quote launch/i);
    expect(screen.getByText('Borrower name is required.')).toHaveAttribute('role', 'alert');
    expect(screen.getByText(/does not infer rates, thresholds, or eligibility decisions/i)).toBeInTheDocument();
  });
});
