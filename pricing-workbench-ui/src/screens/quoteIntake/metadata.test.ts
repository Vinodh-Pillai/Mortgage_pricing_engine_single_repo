import { describe, expect, it } from 'vitest';
import { fallbackMetadata, fieldsForStep } from './metadata';

describe('MetadataTest', () => {
  it('mapsFieldGroupsToSteps', () => {
    const metadata = fallbackMetadata();
    expect(fieldsForStep(metadata, 1).map((field) => field.fieldId)).toEqual(expect.arrayContaining(['loanPurpose', 'loanAmount', 'propertyZip']));
    expect(fieldsForStep(metadata, 6).map((field) => field.fieldId)).toEqual(expect.arrayContaining(['productFamily', 'effectiveDate', 'quoteIntent', 'channel', 'scenarioName', 'externalLoanId']));
  });

  it('keepsTechnicalFieldsOptionalInPreferences', () => {
    const metadata = fallbackMetadata();
    const fields = fieldsForStep(metadata, 6).filter((field) => ['quoteIntent', 'channel', 'scenarioName', 'externalLoanId'].includes(field.fieldId));
    expect(fields).toHaveLength(4);
    expect(fields.every((field) => field.required === false)).toBe(true);
  });

  it('ordersStepsPerProgressiveSectionOrder', () => {
    const metadata = fallbackMetadata();
    expect(metadata.quickQuoteState?.progressiveSectionOrder).toEqual(['scenario-identity', 'borrower-credit', 'loan-structure', 'property', 'income-assets', 'preferences']);
  });

  it('doesNotClassifySelectBackedFallbackFieldsAsNumeric', () => {
    const metadata = fallbackMetadata();
    const fields = [...fieldsForStep(metadata, 4), ...fieldsForStep(metadata, 5), ...fieldsForStep(metadata, 6)];
    expect(fields.find((field) => field.fieldId === 'channel')?.dataType).toBe('text');
    expect(fields.find((field) => field.fieldId === 'incomeType')?.dataType).toBe('text');
    expect(fields.find((field) => field.fieldId === 'incomeVerificationStatus')?.dataType).toBe('text');
  });
});
