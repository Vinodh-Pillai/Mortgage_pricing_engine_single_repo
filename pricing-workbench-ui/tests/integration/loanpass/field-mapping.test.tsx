import '@testing-library/jest-dom/vitest';
import { render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { borrowerIntakeFields, fieldsForStep } from '../../../src/screens/quoteIntake/metadata';
import { StepFields } from '../../../src/screens/quoteIntake/steps/StepFields';
import { initialQuoteIntake } from '../../../src/screens/quoteIntake/QuoteIntakeFlow';
import { loanPassQuoteIntakeFields, toLoanPassQuoteIntakePayload, type BorrowerIntake, type ScenarioIntakeField } from '../../../src/lib/api/quoteRuns';
import { loanPassField, loanPassMinimumIntake } from './loanpass-test-fixtures';

describe('PII-26-S17 LoanPass field mapping contract', () => {
  it('maps every approved BorrowerIntake field to the LoanPass launch payload with no legacy UI-only fields', () => {
    const approvedUiFields = [...borrowerIntakeFields].sort();
    const approvedLaunchFields = [...loanPassQuoteIntakeFields].sort();
    const initialIntakeFields = Object.keys(initialQuoteIntake).sort();

    expect(approvedLaunchFields).toEqual(approvedUiFields);
    expect(initialIntakeFields).toEqual(approvedLaunchFields);

    const payload = toLoanPassQuoteIntakePayload({
      ...loanPassMinimumIntake,
      scenarioName: 'legacy scenario name must not be posted',
      externalLoanId: 'legacy-external-id',
      borrowerName: 'legacy borrower',
      quoteIntent: 'legacy quote intent',
    } as BorrowerIntake);

    expect(Object.keys(payload).sort()).toEqual(approvedLaunchFields);
    expect(payload).toMatchObject({ borrowerLastName: 'Borrower', loanNumber: 'LP-1001', mortgageType: 'Conventional' });
    expect(payload).not.toHaveProperty('scenarioName');
    expect(payload).not.toHaveProperty('externalLoanId');
    expect(payload).not.toHaveProperty('borrowerName');
    expect(payload).not.toHaveProperty('quoteIntent');
  });

  it('keeps LoanPass minimum required start fields aligned with approved fallback metadata', () => {
    const firstStepFields = fieldsForStep(null, 1);
    const requiredFields = firstStepFields.filter((field) => field.required).map((field) => field.fieldId).sort();

    expect(requiredFields).toEqual(['borrowerLastName', 'loanNumber', 'mortgageType']);
    expect(firstStepFields.map((field) => field.fieldId)).toEqual(['borrowerLastName', 'loanNumber', 'mortgageType', 'channel']);
  });

  it('renders approved LoanPass dropdown values exactly from the current local UI field component', () => {
    const fields: ScenarioIntakeField[] = [
      loanPassField('channel'),
      loanPassField('mortgageType', true),
      loanPassField('propertyType'),
      loanPassField('occupancyType'),
      loanPassField('documentationType'),
      loanPassField('selfEmployed'),
    ];

    render(<StepFields fields={fields} intake={loanPassMinimumIntake} errors={{}} onChange={vi.fn()} />);

    expect(optionValues(screen.getByRole('combobox', { name: /^Channel$/i }))).toEqual(['', 'Retail', 'Wholesale', 'Correspondent', 'Consumer Direct']);
    expect(optionValues(screen.getByRole('combobox', { name: /^Mortgage Type/i }))).toEqual(['', 'Conventional', 'NonQM', 'FHA', 'VA', 'Jumbo', 'Home Equity']);
    expect(optionValues(screen.getByRole('combobox', { name: /^Property Type$/i }))).toEqual(['', 'Single Family', 'Condominium', 'Condotel', 'Two to Four Family', 'Manufactured Home', 'PUD', 'Multi-Family', 'Cooperative', 'Townhouse', 'Modular Home', 'Mixed-Use']);
    expect(optionValues(screen.getByRole('combobox', { name: /^Occupancy Type$/i }))).toEqual(['', 'Investment', 'Primary Residence', 'Second Home']);
    expect(optionValues(screen.getByRole('combobox', { name: /^Documentation Type$/i }))).toEqual(['', 'DSCR', 'Full Documentation', 'Bank Statements', '1099', 'Profit and Loss', 'WVOE Only', 'Asset Utilization', 'ATR-In-Full', 'K-1 Only']);
    expect(optionValues(screen.getByRole('combobox', { name: /^Self Employed$/i }))).toEqual(['', 'Yes', 'No']);
  });
});

function optionValues(select: HTMLElement) {
  return within(select).getAllByRole('option').map((option) => option.getAttribute('value'));
}
