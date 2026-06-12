import type { BorrowerIntake, ScenarioIntakeField } from '../../../lib/api/quoteRuns';
import type { IntakeFieldErrors } from '../validation';

export type StepFieldsProps = {
  fields: ScenarioIntakeField[];
  intake: BorrowerIntake;
  errors: IntakeFieldErrors;
  onChange: (field: keyof BorrowerIntake, value: string) => void;
};

export function StepFields({ fields, intake, errors, onChange }: StepFieldsProps) {
  if (fields.length === 0) {
    return <p className="quote-intake-empty" role="status">No fields are configured for this step. Review intake metadata setup before launch.</p>;
  }

  return (
    <div className="quote-intake-fields">
      {fields.map((field) => <MetadataDrivenField key={field.fieldId} field={field} value={intake[field.fieldId]} error={errors[field.fieldId]} onChange={onChange} />)}
    </div>
  );
}

function MetadataDrivenField({
  field,
  value,
  error,
  onChange,
}: {
  field: ScenarioIntakeField;
  value: string;
  error?: string;
  onChange: (field: keyof BorrowerIntake, value: string) => void;
}) {
  const errorId = `${field.fieldId}-error`;
  const helpId = `${field.fieldId}-help`;
  const describedBy = error ? `${helpId} ${errorId}` : helpId;
  const commonProps = {
    id: field.fieldId,
    name: field.fieldId,
    value,
    'aria-invalid': Boolean(error),
    'aria-describedby': describedBy,
    onChange: (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => onChange(field.fieldId, event.target.value),
  };

  return (
    <div className={field.dataType === 'textarea' ? 'quote-intake-field quote-intake-field--wide' : 'quote-intake-field'}>
      <label htmlFor={field.fieldId}>{field.label}{field.required ? <span aria-hidden="true"> *</span> : null}</label>
      {field.dataType === 'textarea' ? (
        <textarea {...commonProps} rows={4} />
      ) : (
        <input {...commonProps} type={field.dataType === 'email' ? 'email' : field.dataType === 'number' ? 'number' : dateLikeField(field.fieldId) ? 'date' : 'text'} />
      )}
      <p id={helpId} className="quote-intake-help">{field.helpText} Source: {field.sourceRef}. Readiness: {field.decisionQuality}.</p>
      {field.validationMessages.length > 0 ? (
        <ul className="quote-intake-validation-hints" aria-label={`${field.label} validation guidance`}>
          {field.validationMessages.map((message) => <li key={message}>{message}</li>)}
        </ul>
      ) : null}
      {error ? <p id={errorId} className="quote-intake-error" role="alert">{error}</p> : null}
    </div>
  );
}

function dateLikeField(fieldId: keyof BorrowerIntake) {
  return /Date$/.test(fieldId) || fieldId === 'effectiveDate';
}
