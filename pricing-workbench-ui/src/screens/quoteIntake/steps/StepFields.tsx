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
    { value: 'Retail', label: 'Retail' },
    { value: 'Wholesale', label: 'Wholesale' },
    { value: 'Correspondent', label: 'Correspondent' },
    { value: 'Consumer Direct', label: 'Consumer Direct' },
  ],
  documentationType: [
    { value: '', label: 'Select documentation type' },
    { value: 'DSCR', label: 'DSCR' },
    { value: 'Full Documentation', label: 'Full Documentation' },
    { value: 'Bank Statements', label: 'Bank Statements' },
    { value: '1099', label: '1099' },
    { value: 'Profit and Loss', label: 'Profit and Loss' },
    { value: 'WVOE Only', label: 'WVOE Only' },
    { value: 'Asset Utilization', label: 'Asset Utilization' },
    { value: 'ATR-In-Full', label: 'ATR-In-Full' },
    { value: 'K-1 Only', label: 'K-1 Only' },
  ],
  secondaryDocumentationType: [
    { value: '', label: 'Select secondary documentation type' },
    { value: 'None', label: 'None' },
    { value: 'Bank Statement', label: 'Bank Statement' },
    { value: '1099', label: '1099' },
    { value: 'P&L', label: 'P&L' },
    { value: 'Asset Depletion', label: 'Asset Depletion' },
    { value: 'WVOE', label: 'WVOE' },
    { value: 'ATR-in-Full', label: 'ATR-in-Full' },
    { value: 'Streamline', label: 'Streamline' },
    { value: 'K-1', label: 'K-1' },
    { value: 'Asset Qualifier', label: 'Asset Qualifier' },
    { value: 'Asset Utilization', label: 'Asset Utilization' },
  ],
  state: usStateOptions,
  propertyType: [
    { value: '', label: 'Select property type' },
    { value: 'Single Family', label: 'Single Family' },
    { value: 'Condominium', label: 'Condominium' },
    { value: 'Condotel', label: 'Condotel' },
    { value: 'Two to Four Family', label: 'Two to Four Family' },
    { value: 'Manufactured Home', label: 'Manufactured Home' },
    { value: 'PUD', label: 'PUD' },
    { value: 'Multi-Family', label: 'Multi-Family' },
    { value: 'Cooperative', label: 'Cooperative' },
    { value: 'Townhouse', label: 'Townhouse' },
    { value: 'Modular Home', label: 'Modular Home' },
    { value: 'Mixed-Use', label: 'Mixed-Use' },
  ],
  occupancyType: [
    { value: '', label: 'Select occupancy type' },
    { value: 'Investment', label: 'Investment' },
    { value: 'Primary Residence', label: 'Primary Residence' },
    { value: 'Second Home', label: 'Second Home' },
  ],
  lienPosition: [
    { value: '', label: 'Select lien position' },
    { value: 'First', label: 'First' },
    { value: 'Second', label: 'Second' },
  ],
  desiredAmortizationType: [
    { value: '', label: 'Select amortization type' },
    { value: 'Fixed', label: 'Fixed' },
    { value: 'Adjustable Rate', label: 'Adjustable Rate' },
  ],
  mortgageType: [
    { value: '', label: 'Select mortgage type' },
    { value: 'Conventional', label: 'Conventional' },
    { value: 'NonQM', label: 'NonQM' },
    { value: 'FHA', label: 'FHA' },
    { value: 'VA', label: 'VA' },
    { value: 'Jumbo', label: 'Jumbo' },
    { value: 'Home Equity', label: 'Home Equity' },
  ],
  selfEmployed: yesNoOptions('Select self-employed status'),
  citizenshipType: [
    { value: '', label: 'Select citizenship type' },
    { value: 'US Citizen', label: 'US Citizen' },
    { value: 'Permanent Resident Alien', label: 'Permanent Resident Alien' },
    { value: 'Non-Permanent Resident Alien', label: 'Non-Permanent Resident Alien' },
    { value: 'Foreign National', label: 'Foreign National' },
    { value: 'ITIN', label: 'ITIN' },
    { value: 'DACA', label: 'DACA' },
  ],
  propertyLocation: [
    { value: '', label: 'Select property location' },
    { value: 'Not Applicable', label: 'Not Applicable' },
    { value: 'Rural Property', label: 'Rural Property' },
  ],
  investorExperience: [
    { value: '', label: 'Select investor experience' },
    { value: 'Experienced', label: 'Experienced' },
    { value: 'Non-Experienced', label: 'Non-Experienced' },
  ],
  wholesaleCompensation: [
    { value: '', label: 'Select wholesale compensation' },
    { value: 'Borrower Paid', label: 'Borrower Paid' },
    { value: 'Lender Paid', label: 'Lender Paid' },
  ],
  prepaymentPenaltyTerm: [
    { value: '', label: 'Select prepayment penalty term' },
    { value: 'No Prepay', label: 'No Prepay' },
    { value: '6 Month', label: '6 Month' },
    { value: '1 Year', label: '1 Year' },
    { value: '2 Year', label: '2 Year' },
    { value: '3 Year', label: '3 Year' },
    { value: '4 Year', label: '4 Year' },
    { value: '5 Year', label: '5 Year' },
  ],
  waiveEscrows: yesNoOptions('Select escrow waiver'),
  gift: yesNoOptions('Select gift'),
  aus: [
    { value: '', label: 'Select AUS' },
    { value: 'FannieMae DU', label: 'FannieMae DU' },
    { value: 'None', label: 'None' },
    { value: 'FreddieMac LP', label: 'FreddieMac LP' },
    { value: 'USDA GUS', label: 'USDA GUS' },
  ],
  manualUnderwriting: yesNoOptions('Select manual underwriting'),
  interestOnly: yesNoOptions('Select interest-only'),
  achPayment: yesNoOptions('Select ACH payment'),
  mortgageLatePayments: yesNoOptions('Select mortgage late payments'),
  creditEvent: yesNoOptions('Select credit event'),
  concession: yesNoOptions('Select concession'),
  secondaryAdjustment: yesNoOptions('Select secondary adjustment'),
  shortTermRental: yesNoOptions('Select short-term rental'),
  professional: yesNoOptions('Select professional status'),
};

function yesNoOptions(placeholder: string): SelectOption[] {
  return [{ value: '', label: placeholder }, { value: 'Yes', label: 'Yes' }, { value: 'No', label: 'No' }];
}

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
      {fields.map((field) => <MetadataDrivenField key={field.fieldId} field={field} value={intake[field.fieldId] ?? ''} error={errors[field.fieldId]} onChange={onChange} />)}
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
    required: field.required,
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
  return /Date$/.test(fieldId);
}

function optionsForField(fieldId: keyof BorrowerIntake, value: string): SelectOption[] | undefined {
  const options = selectOptionsByField[fieldId];
  if (!options) return undefined;
  if (!value || options.some((option) => option.value === value)) return options;
  return [...options, { value, label: friendlyEnumLabel(value) }];
}

function displayValue(fieldId: keyof BorrowerIntake, value: string) {
  if ((fieldId === 'purchasePrice' || fieldId === 'appraisedValue') && value.trim() === '-1') return '';
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
