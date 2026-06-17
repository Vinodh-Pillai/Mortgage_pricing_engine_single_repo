import { describe, expect, it } from 'vitest';
import { initialQuoteIntake } from './QuoteIntakeFlow';
import { validateFields } from './validation';

describe('ValidationTest', () => {
  it('showsFieldLevelErrors', () => {
    const errors = validateFields([{ fieldId: 'contactEmail', label: 'Contact email', groupId: 'borrower-credit', dataType: 'email', required: true, helpText: '', sourceRef: '', decisionQuality: 'VERIFIED', validationMessages: [] }], initialQuoteIntake);
    expect(errors.contactEmail).toBe('Contact email is required.');
  });

  it('doesNotTreatSelectBackedFieldsAsNumericEvenWhenMetadataIsWrong', () => {
    const errors = validateFields([{ fieldId: 'documentationType', label: 'Documentation type', groupId: 'income-assets', dataType: 'number', required: false, helpText: '', sourceRef: '', decisionQuality: 'VERIFIED', validationMessages: [] }], initialQuoteIntake);
    expect(errors.documentationType).toBeUndefined();
  });
});
