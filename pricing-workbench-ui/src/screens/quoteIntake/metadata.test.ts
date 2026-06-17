import { describe, expect, it } from 'vitest';
import { fallbackMetadata, fieldsForStep } from './metadata';

describe('MetadataTest', () => {
  it('mapsFieldGroupsToSteps', () => {
    const metadata = fallbackMetadata();
    expect(fieldsForStep(metadata, 1).map((field) => field.fieldId)).toEqual(expect.arrayContaining(['borrowerLastName', 'loanNumber', 'mortgageType']));
    expect(fieldsForStep(metadata, 2).map((field) => field.fieldId)).toEqual(expect.arrayContaining(['documentationType', 'totalBorrowerIncome', 'estimatedDti']));
    expect(fieldsForStep(metadata, 5).map((field) => field.fieldId)).toEqual(expect.arrayContaining(['prepaymentPenaltyTerm', 'vaFundingFeeExemptionType', 'creditEvent']));
  });

  it('requiresOnlyLoanPassStartFieldsInLoanBasics', () => {
    const metadata = fallbackMetadata();
    const fields = fieldsForStep(metadata, 1);
    expect(fields.filter((field) => field.required).map((field) => field.fieldId)).toEqual(['borrowerLastName', 'loanNumber', 'mortgageType']);
    expect(fields.find((field) => field.fieldId === 'channel')?.required).toBe(false);
  });

  it('ordersStepsPerProgressiveSectionOrder', () => {
    const metadata = fallbackMetadata();
    expect(metadata.quickQuoteState?.progressiveSectionOrder).toEqual(['scenario-identity', 'borrower-credit', 'loan-structure', 'property', 'income-assets']);
  });

  it('doesNotClassifySelectBackedFallbackFieldsAsNumeric', () => {
    const metadata = fallbackMetadata();
    const fields = [...fieldsForStep(metadata, 1), ...fieldsForStep(metadata, 2), ...fieldsForStep(metadata, 3), ...fieldsForStep(metadata, 4), ...fieldsForStep(metadata, 5)];
    expect(fields.find((field) => field.fieldId === 'channel')?.dataType).toBe('text');
    expect(fields.find((field) => field.fieldId === 'documentationType')?.dataType).toBe('text');
    expect(fields.find((field) => field.fieldId === 'mortgageType')?.dataType).toBe('text');
    expect(fields.find((field) => field.fieldId === 'selfEmployed')?.dataType).toBe('text');
  });
});
