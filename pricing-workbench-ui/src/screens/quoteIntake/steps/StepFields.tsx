import type { BorrowerIntake, ScenarioIntakeField } from '../../../lib/api/quoteRuns';
import { HelpIcon } from '../../../design-system/icons';
import type { IntakeFieldErrors } from '../validation';

type SelectOption = { value: string; label: string };

const usStateOptions: SelectOption[] = [
  { value: '', label: 'Select state' },
  { value: 'AL', label: 'Alabama' }, { value: 'AK', label: 'Alaska' }, { value: 'AZ', label: 'Arizona' }, { value: 'AR', label: 'Arkansas' },
  { value: 'CA', label: 'California' }, { value: 'CO', label: 'Colorado' }, { value: 'CT', label: 'Connecticut' }, { value: 'DE', label: 'Delaware' },
  { value: 'FL', label: 'Florida' }, { value: 'GA', label: 'Georgia' }, { value: 'HI', label: 'Hawaii' }, { value: 'ID', label: 'Idaho' },
  { value: 'IL', label: 'Illinois' }, { value: 'IN', label: 'Indiana' }, { value: 'IA', label: 'Iowa' }, { value: 'KS', label: 'Kansas' },
  { value: 'KY', label: 'Kentucky' }, { value: 'LA', label: 'Louisiana' }, { value: 'ME', label: 'Maine' }, { value: 'MD', label: 'Maryland' },
  { value: 'MA', label: 'Massachusetts' }, { value: 'MI', label: 'Michigan' }, { value: 'MN', label: 'Minnesota' }, { value: 'MS', label: 'Mississippi' },
  { value: 'MO', label: 'Missouri' }, { value: 'MT', label: 'Montana' }, { value: 'NE', label: 'Nebraska' }, { value: 'NV', label: 'Nevada' },
  { value: 'NH', label: 'New Hampshire' }, { value: 'NJ', label: 'New Jersey' }, { value: 'NM', label: 'New Mexico' }, { value: 'NY', label: 'New York' },
  { value: 'NC', label: 'North Carolina' }, { value: 'ND', label: 'North Dakota' }, { value: 'OH', label: 'Ohio' }, { value: 'OK', label: 'Oklahoma' },
  { value: 'OR', label: 'Oregon' }, { value: 'PA', label: 'Pennsylvania' }, { value: 'RI', label: 'Rhode Island' }, { value: 'SC', label: 'South Carolina' },
  { value: 'SD', label: 'South Dakota' }, { value: 'TN', label: 'Tennessee' }, { value: 'TX', label: 'Texas' }, { value: 'UT', label: 'Utah' },
  { value: 'VT', label: 'Vermont' }, { value: 'VA', label: 'Virginia' }, { value: 'WA', label: 'Washington' }, { value: 'WV', label: 'West Virginia' },
  { value: 'WI', label: 'Wisconsin' }, { value: 'WY', label: 'Wyoming' },
];

const selectOptionsByField: Partial<Record<keyof BorrowerIntake, SelectOption[]>> = {
  channel: [
    { value: '', label: 'Select channel' },
    { value: 'RETAIL', label: 'Retail' },
    { value: 'WHOLESALE', label: 'Wholesale' },
    { value: 'CORRESPONDENT', label: 'Correspondent' },
    { value: 'CONSUMER_DIRECT', label: 'Consumer Direct' },
  ],
  incomeType: [
    { value: '', label: 'Select income type' },
    { value: 'W2', label: 'W-2' },
    { value: 'SELF_EMPLOYED', label: 'Self-Employed' },
    { value: 'RETIREMENT', label: 'Retirement' },
    { value: 'OTHER', label: 'Other' },
  ],
  incomeVerificationStatus: [
    { value: '', label: 'Select income verification status' },
    { value: 'VERIFIED', label: 'Verified' },
    { value: 'STATED', label: 'Stated' },
    { value: 'UNKNOWN', label: 'Unknown' },
    { value: 'NOT_REQUIRED', label: 'Not Required' },
  ],
  propertyState: usStateOptions,
  propertyType: [
    { value: '', label: 'Select property type' },
    { value: 'SINGLE_FAMILY', label: 'Single Family' },
    { value: 'CONDO', label: 'Condo' },
    { value: 'TOWNHOUSE', label: 'Townhouse' },
    { value: 'MULTI_FAMILY_2_4', label: 'Multi-Family (2-4 units)' },
    { value: 'MANUFACTURED', label: 'Manufactured Home' },
  ],
  occupancyType: [
    { value: '', label: 'Select occupancy type' },
    { value: 'PRIMARY_RESIDENCE', label: 'Primary Residence' },
    { value: 'SECOND_HOME', label: 'Second Home' },
    { value: 'INVESTMENT_PROPERTY', label: 'Investment Property' },
  ],
};

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
  const describedBy = error ? errorId : undefined;
  const helpTooltip = field.showHelpIcon ? field.helpTooltip : undefined;
  const selectOptions = optionsForField(field.fieldId, value);
  const renderedValue = displayValue(field.fieldId, value);
  const commonProps = {
    id: field.fieldId,
    name: field.fieldId,
    value: renderedValue,
    'aria-invalid': Boolean(error),
    'aria-describedby': describedBy,
    onChange: (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => onChange(field.fieldId, event.target.value),
  };

  return (
    <div className={field.dataType === 'textarea' ? 'quote-intake-field quote-intake-field--wide' : 'quote-intake-field'}>
      <div className="quote-intake-field-label">
        <label htmlFor={field.fieldId}>{field.label}{field.required ? <span aria-hidden="true"> *</span> : null}</label>
        {helpTooltip ? <button type="button" className="quote-intake-help-icon" aria-label={helpTooltip} title={helpTooltip}><HelpIcon size={16} /></button> : null}
      </div>
      {selectOptions ? (
        <select {...commonProps}>
          {selectOptions.map((option) => <option key={option.value || 'empty'} value={option.value}>{option.label}</option>)}
        </select>
      ) : field.dataType === 'textarea' ? (
        <textarea {...commonProps} rows={4} />
      ) : (
        <input {...commonProps} type={field.dataType === 'email' ? 'email' : field.dataType === 'number' ? 'number' : dateLikeField(field.fieldId) ? 'date' : 'text'} />
      )}
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

function optionsForField(fieldId: keyof BorrowerIntake, value: string): SelectOption[] | undefined {
  const options = selectOptionsByField[fieldId];
  if (!options) return undefined;
  if (!value || options.some((option) => option.value === value)) return options;
  return [...options, { value, label: friendlyEnumLabel(value) }];
}

function displayValue(fieldId: keyof BorrowerIntake, value: string) {
  if ((fieldId === 'purchasePrice' || fieldId === 'purchasePriceOrValue') && value.trim() === '-1') return '';
  return value;
}

function friendlyEnumLabel(value: string) {
  return value
    .toLowerCase()
    .split('_')
    .map((part) => part ? part[0].toUpperCase() + part.slice(1) : part)
    .join(' ')
    .replace(/\bW2\b/i, 'W-2');
}
