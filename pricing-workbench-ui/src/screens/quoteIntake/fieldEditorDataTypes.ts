export type FieldEditorDataType = 'string' | 'enum' | 'number' | 'duration' | 'date' | 'time' | 'us-state' | 'us-county';

export type FieldEditorDraft = {
  fieldName: string;
  fieldId: string;
  fieldDescription: string;
  dataType: FieldEditorDataType | string;
  enumTypeId: string;
  precision: string;
  style: string;
  units: string;
  minimum: string;
  maximum: string;
};

export type FieldEditorSerializedDraft = {
  fieldName: string;
  fieldId: string;
  fieldDescription: string;
  dataType: FieldEditorDataType;
  enumTypeId: string;
  constraints: Record<string, string>;
};

export const fieldEditorDataTypes: Array<{ value: FieldEditorDataType; label: string }> = [
  { value: 'string', label: 'String' },
  { value: 'enum', label: 'Enumeration' },
  { value: 'number', label: 'Number' },
  { value: 'duration', label: 'Duration' },
  { value: 'date', label: 'Date' },
  { value: 'time', label: 'Time' },
  { value: 'us-state', label: 'US state' },
  { value: 'us-county', label: 'US county' },
];

const supportedFieldEditorDataTypes = new Set(fieldEditorDataTypes.map((item) => item.value));

export function emptyFieldEditorDraft(): FieldEditorDraft {
  return { fieldName: '', fieldId: '', fieldDescription: '', dataType: 'string', enumTypeId: '', precision: '', style: '', units: '', minimum: '', maximum: '' };
}

export function validateFieldEditorDraft(draft: FieldEditorDraft) {
  if (!draft.fieldName.trim()) return 'FIELD_NAME_REQUIRED: Field Name is required.';
  if (!draft.fieldId.trim()) return 'FIELD_ID_REQUIRED: Field ID is required.';
  if (!/^field@[a-z0-9][a-z0-9._:-]*$/i.test(draft.fieldId.trim())) return 'FIELD_ID_INVALID: Field ID must be an approved field@ reference.';
  if (!draft.fieldDescription.trim()) return 'FIELD_DESCRIPTION_REQUIRED: Field Description is required.';
  if (!supportedFieldEditorDataTypes.has(draft.dataType as FieldEditorDataType)) return `FIELD_VALUE_TYPE_UNSUPPORTED: ${draft.dataType || 'blank'} is not a supported field-library data type.`;
  if (draft.dataType === 'enum' && !draft.enumTypeId.trim()) return 'FIELD_ENUM_TYPE_REQUIRED: Enumeration fields require an approved enum type.';
  if (draft.dataType === 'number' || draft.dataType === 'duration') {
    for (const key of ['precision', 'minimum', 'maximum'] as const) {
      const value = draft[key].trim();
      if (value && Number.isNaN(Number(value))) return `FIELD_${key.toUpperCase()}_INVALID: ${fieldEditorDataTypeLabel(draft.dataType)} ${key} must be numeric when configured.`;
    }
    if (draft.minimum.trim() && draft.maximum.trim() && Number(draft.minimum) > Number(draft.maximum)) return 'FIELD_RANGE_INVALID: Minimum must be less than or equal to maximum.';
  }
  return '';
}

export function serializeFieldEditorDraft(draft: FieldEditorDraft): FieldEditorSerializedDraft {
  const constraints = Object.fromEntries(
    Object.entries({ precision: draft.precision, style: draft.style, units: draft.units, minimum: draft.minimum, maximum: draft.maximum })
      .map(([key, value]) => [key, value.trim()])
      .filter(([, value]) => value),
  );
  return {
    fieldName: draft.fieldName.trim(),
    fieldId: draft.fieldId.trim(),
    fieldDescription: draft.fieldDescription.trim(),
    dataType: draft.dataType as FieldEditorDataType,
    enumTypeId: draft.dataType === 'enum' ? draft.enumTypeId.trim() : '',
    constraints,
  };
}

export function fieldEditorDataTypeLabel(value: string) {
  return fieldEditorDataTypes.find((item) => item.value === value)?.label ?? value;
}
