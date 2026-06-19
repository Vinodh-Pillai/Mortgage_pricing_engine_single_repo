import { describe, expect, it } from 'vitest';
import { emptyFieldEditorDraft, serializeFieldEditorDraft, validateFieldEditorDraft } from './fieldEditorDataTypes';

describe('field editor data types', () => {
  it('requires field name, field id, description, and a supported data type', () => {
    expect(validateFieldEditorDraft(emptyFieldEditorDraft())).toContain('FIELD_NAME_REQUIRED');
    expect(validateFieldEditorDraft({ ...emptyFieldEditorDraft(), fieldName: 'Unsupported local field', fieldId: 'field@unsupported-local', fieldDescription: 'Rejected unsupported type proof.', dataType: 'pricing-formula' })).toContain('FIELD_VALUE_TYPE_UNSUPPORTED');
  });

  it('requires an approved enum type reference for enumeration fields', () => {
    const draft = { ...emptyFieldEditorDraft(), fieldName: 'Product Channel', fieldId: 'field@product-channel', fieldDescription: 'Channel selector from approved enum catalog.', dataType: 'enum' };
    expect(validateFieldEditorDraft(draft)).toContain('FIELD_ENUM_TYPE_REQUIRED');

    expect(validateFieldEditorDraft({ ...draft, enumTypeId: 'product-channel' })).toBe('');
    expect(serializeFieldEditorDraft({ ...draft, enumTypeId: 'product-channel' })).toMatchObject({ fieldId: 'field@product-channel', fieldName: 'Product Channel', dataType: 'enum', enumTypeId: 'product-channel' });
  });

  it('validates and serializes number and duration constraints when configured', () => {
    const durationDraft = { ...emptyFieldEditorDraft(), fieldName: 'Review Duration', fieldId: 'field@review-duration', fieldDescription: 'Duration reference supplied by field metadata.', dataType: 'duration', precision: '1', style: 'metadata-style-ref', units: 'metadata-unit-ref', minimum: '90', maximum: '30' };
    expect(validateFieldEditorDraft(durationDraft)).toContain('FIELD_RANGE_INVALID');

    const serialized = serializeFieldEditorDraft({ ...durationDraft, maximum: '120' });
    expect(validateFieldEditorDraft({ ...durationDraft, maximum: '120' })).toBe('');
    expect(serialized.constraints).toEqual({ precision: '1', style: 'metadata-style-ref', units: 'metadata-unit-ref', minimum: '90', maximum: '120' });
  });
});
