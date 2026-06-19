import type { BorrowerIntake, IntakeValidation, ScenarioIntakeField } from '../../lib/api/quoteRuns';
import { quoteIntakeTraceId } from './metadata';

export type IntakeFieldErrors = Partial<Record<keyof BorrowerIntake, string>>;

const selectBackedFields = new Set<keyof BorrowerIntake>(['channel', 'documentationType', 'secondaryDocumentationType', 'documentationTypeTimeFrame', 'selfEmployedTimeFrame', 'state', 'propertyType', 'occupancyType', 'lienPosition', 'desiredAmortizationType', 'mortgageType', 'selfEmployed', 'citizenshipType']);

export function validateFields(fields: ScenarioIntakeField[], values: BorrowerIntake): IntakeFieldErrors {
  const errors: IntakeFieldErrors = {};
  for (const field of fields) {
    const value = values[field.fieldId] ?? '';
    const dataType = fieldValueType(field);
    if (field.required && !value.trim()) {
      errors[field.fieldId] = `${field.label} is required.`;
      continue;
    }
    if (value.trim() && dataType === 'email' && !/^\S+@\S+\.\S+$/.test(value)) {
      errors[field.fieldId] = `${field.label} must be a valid email address.`;
      continue;
    }
    if (value.trim() && isNumericField(field) && !selectBackedFields.has(field.fieldId)) {
      const numericError = numericMetadataError(field, value);
      if (numericError) errors[field.fieldId] = numericError;
    }
  }
  return errors;
}

function isNumericField(field: ScenarioIntakeField) {
  const dataType = fieldValueType(field);
  return dataType === 'number' || dataType === 'duration' || hasNumericMetadata(field);
}

function hasNumericMetadata(field: ScenarioIntakeField) {
  return firstNumericConstraint(field, ['minimum', 'maximum', 'min', 'max', 'minValue', 'maxValue', 'precision', 'step', 'increment']) !== null;
}

function fieldValueType(field: ScenarioIntakeField) {
  const valueType = valueTypeRecord(field)?.type;
  return typeof valueType === 'string' && valueType ? valueType : field.dataType;
}

function numericMetadataError(field: ScenarioIntakeField, rawValue: string) {
  const numericValue = Number(rawValue);
  if (Number.isNaN(numericValue)) return `${field.label} must be numeric.`;

  const minimum = firstNumericConstraint(field, ['minimum', 'min', 'minValue']);
  if (minimum !== null && numericValue < minimum) return `${field.label} must be at least ${formatNumber(minimum)}.`;

  const maximum = firstNumericConstraint(field, ['maximum', 'max', 'maxValue']);
  if (maximum !== null && numericValue > maximum) return `${field.label} must be no more than ${formatNumber(maximum)}.`;

  const precision = firstNumericConstraint(field, ['precision']);
  if (precision !== null && !precisionAllows(rawValue, precision)) return `${field.label} must use no more than ${formatNumber(precision)} decimal places.`;

  const step = firstNumericConstraint(field, ['step', 'increment']);
  if (step !== null && step > 0 && !matchesStep(numericValue, minimum ?? 0, step)) return `${field.label} must use increments of ${formatNumber(step)}.`;

  return '';
}

function firstNumericConstraint(field: ScenarioIntakeField, keys: string[]) {
  const source = field as ScenarioIntakeField & { constraints?: Record<string, unknown>; [key: string]: unknown };
  const valueType = valueTypeRecord(field);
  for (const key of keys) {
    const value = source.constraints?.[key] ?? source[key] ?? valueType?.[key];
    const numericValue = numericConstraintValue(value);
    if (numericValue !== null) return numericValue;
  }
  return null;
}

function valueTypeRecord(field: ScenarioIntakeField): Record<string, unknown> | null {
  return typeof field.valueType === 'object' && field.valueType !== null ? field.valueType as Record<string, unknown> : null;
}

function numericConstraintValue(value: unknown) {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  if (typeof value !== 'string') return null;
  const trimmed = value.trim();
  if (!trimmed || Number.isNaN(Number(trimmed))) return null;
  return Number(trimmed);
}

function precisionAllows(rawValue: string, precision: number) {
  if (!Number.isInteger(precision) || precision < 0) return true;
  const decimalPart = rawValue.trim().split(/[eE]/)[0]?.split('.')[1] ?? '';
  return decimalPart.length <= precision;
}

function matchesStep(value: number, base: number, step: number) {
  const quotient = (value - base) / step;
  return Math.abs(quotient - Math.round(quotient)) < 1e-9;
}

function formatNumber(value: number) {
  return Number.isInteger(value) ? String(value) : String(value);
}

export function errorsToValidation(errors: IntakeFieldErrors, message = 'Complete the highlighted required fields.'): IntakeValidation {
  const hasErrors = Object.keys(errors).length > 0;
  return {
    passed: !hasErrors,
    status: hasErrors ? 'BLOCKED' : 'PASSED',
    message: hasErrors ? message : 'Step validation passed.',
    blockers: errors,
  };
}

export async function validateIntakeOnServer(
  tenantId: string,
  runId: string,
  intake: BorrowerIntake,
  fetchImpl: typeof fetch = fetch,
): Promise<IntakeValidation> {
  const response = await fetchImpl(`/api/v1/tenants/${encodeURIComponent(tenantId)}/quote-runs/${encodeURIComponent(runId)}/intake/validate`, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'X-Ui-Trace-Id': quoteIntakeTraceId,
    },
    body: JSON.stringify(intake),
  });

  if (response.status >= 500) throw new Error('Server-side intake validation is temporarily unavailable.');
  return (await response.json()) as IntakeValidation;
}

export function firstInvalidField(errors: IntakeFieldErrors) {
  return Object.keys(errors)[0] as keyof BorrowerIntake | undefined;
}
