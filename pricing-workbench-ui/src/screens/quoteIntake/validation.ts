import type { BorrowerIntake, IntakeValidation, ScenarioIntakeField } from '../../lib/api/quoteRuns';
import { quoteIntakeTraceId } from './metadata';

export type IntakeFieldErrors = Partial<Record<keyof BorrowerIntake, string>>;

const selectBackedFields = new Set<keyof BorrowerIntake>(['channel', 'incomeType', 'incomeVerificationStatus', 'propertyState', 'propertyType', 'occupancyType']);

export function validateFields(fields: ScenarioIntakeField[], values: BorrowerIntake): IntakeFieldErrors {
  const errors: IntakeFieldErrors = {};
  for (const field of fields) {
    const value = values[field.fieldId] ?? '';
    if (field.required && !value.trim()) {
      errors[field.fieldId] = `${field.label} is required.`;
      continue;
    }
    if (value.trim() && field.dataType === 'email' && !/^\S+@\S+\.\S+$/.test(value)) {
      errors[field.fieldId] = `${field.label} must be a valid email address.`;
      continue;
    }
    if (value.trim() && field.dataType === 'number' && !selectBackedFields.has(field.fieldId) && Number.isNaN(Number(value))) {
      errors[field.fieldId] = `${field.label} must be numeric.`;
    }
  }
  return errors;
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
