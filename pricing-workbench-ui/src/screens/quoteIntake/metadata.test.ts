import { describe, expect, it } from 'vitest';
import { fallbackMetadata, fieldsForStep } from './metadata';

describe('MetadataTest', () => {
  it('mapsFieldGroupsToSteps', () => {
    const metadata = fallbackMetadata();
    expect(fieldsForStep(metadata, 1).map((field) => field.fieldId)).toEqual(expect.arrayContaining(['quoteIntent', 'channel']));
    expect(fieldsForStep(metadata, 6).map((field) => field.fieldId)).toEqual(expect.arrayContaining(['productFamily', 'effectiveDate']));
  });

  it('ordersStepsPerProgressiveSectionOrder', () => {
    const metadata = fallbackMetadata();
    expect(metadata.quickQuoteState?.progressiveSectionOrder).toEqual(['scenario-identity', 'borrower-credit', 'loan-structure', 'property', 'income-assets', 'preferences']);
  });
});
