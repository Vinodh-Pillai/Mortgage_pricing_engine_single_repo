import type { BorrowerIntake, DropdownOption, ScenarioIntakeField } from '../../../lib/api/quoteRuns';
import { HelpIcon } from '../../../design-system/icons';
import { displayChannelLabel } from '../../../lib/utils/channelDisplay';
import type { IntakeFieldErrors } from '../validation';
import { isBorrowerIntakeField, isHeaderMetadataField } from '../metadata';

export type SelectOption = DropdownOption;

export type DropdownOptionsByField = Partial<Record<keyof BorrowerIntake, SelectOption[]>>;

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
  channelCode: [
    { value: '', label: 'Select channel' },
    { value: 'RETAIL', label: 'Retail' },
    { value: 'WHOLESALE', label: 'Wholesale' },
    { value: 'CORRESPONDENT', label: 'Correspondent' },
    { value: 'CONSUMER_DIRECT', label: 'Consumer Direct' },
  ],
  channelType: [
    { value: '', label: 'Select channel type' },
    { value: 'RETAIL', label: 'Retail' },
    { value: 'WHOLESALE', label: 'Wholesale' },
    { value: 'CORRESPONDENT', label: 'Correspondent' },
    { value: 'TPO', label: 'TPO' },
    { value: 'CONSUMER_DIRECT', label: 'Consumer Direct' },
  ],
  investorCode: [
    { value: '', label: 'Select investor' },
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
    { value: 'W-2', label: 'W-2' },
    { value: 'VOE', label: 'VOE' },
  ],
  incomeDocumentationType: [
    { value: '', label: 'Select income documentation type' },
    { value: 'DSCR', label: 'DSCR' },
    { value: 'Full Documentation', label: 'Full Documentation' },
    { value: 'Bank Statements', label: 'Bank Statements' },
    { value: '1099', label: '1099' },
    { value: 'Profit and Loss', label: 'Profit and Loss' },
    { value: 'WVOE Only', label: 'WVOE Only' },
    { value: 'Asset Utilization', label: 'Asset Utilization' },
    { value: 'ATR-In-Full', label: 'ATR-In-Full' },
    { value: 'K-1 Only', label: 'K-1 Only' },
    { value: 'W-2', label: 'W-2' },
    { value: 'VOE', label: 'VOE' },
  ],
  secondaryDocumentationType: [
    { value: '', label: 'Select secondary documentation type' },
    { value: 'None', label: 'None' },
    { value: 'Bank Statement', label: 'Bank Statement' },
    { value: '1099', label: '1099' },
    { value: 'P&L', label: 'P&L' },
    { value: 'WVOE', label: 'WVOE' },
    { value: 'Asset Utilization', label: 'Asset Utilization' },
    { value: 'K-1 Only', label: 'K-1 Only' },
  ],
  documentationTypeTimeFrame: [
    { value: '', label: 'Select documentation timeframe' },
    { value: '12 Month', label: '12 Month' },
    { value: '24 Month', label: '24 Month' },
  ],
  selfEmployedTimeFrame: [
    { value: '', label: 'Select self-employed timeframe' },
    { value: 'Less Than 2 Years', label: 'Less Than 2 Years' },
    { value: '2 Years', label: '2 Years' },
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
  transactionType: [
    { value: '', label: 'Select transaction type' },
    { value: 'Purchase', label: 'Purchase' },
    { value: 'Rate/Term Refinance', label: 'Rate/Term Refinance' },
    { value: 'Cash-Out Refinance', label: 'Cash-Out Refinance' },
  ],
  desiredAmortizationType: [
    { value: '', label: 'Select amortization type' },
    { value: 'Fixed', label: 'Fixed' },
    { value: 'Adjustable Rate', label: 'Adjustable Rate' },
  ],
  amortizationType: [
    { value: '', label: 'Select amortization type' },
    { value: 'Fixed', label: 'Fixed' },
    { value: 'Adjustable Rate', label: 'Adjustable Rate' },
  ],
  loanTermType: [
    { value: '', label: 'Select loan term type' },
    { value: '10 Year', label: '10 Year' },
    { value: '15 Year', label: '15 Year' },
    { value: '20 Year', label: '20 Year' },
    { value: '25 Year', label: '25 Year' },
    { value: '30 Year', label: '30 Year' },
    { value: '40 Year', label: '40 Year' },
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
    { value: 'Permanent Resident', label: 'Permanent Resident' },
    { value: 'Non-Permanent Resident', label: 'Non-Permanent Resident' },
    { value: 'Foreign National', label: 'Foreign National' },
    { value: 'ITIN', label: 'ITIN' },
    { value: 'DACA', label: 'DACA' },
  ],
  firstTimeHomeBuyer: yesNoOptions('Select first-time home buyer'),
  firstTimeInvestor: yesNoOptions('Select first-time investor'),
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
  prePaymentPenaltyStructureType: [
    { value: '', label: 'Select prepayment structure' },
    { value: 'None', label: 'None' },
  ],
  lockPeriodType: [
    { value: '', label: 'Select lock period' },
    { value: '15 Days', label: '15 Days' },
    { value: '30 Days', label: '30 Days' },
    { value: '45 Days', label: '45 Days' },
    { value: '60 Days', label: '60 Days' },
    { value: '90 Days', label: '90 Days' },
  ],
  propertyInformationType: [
    { value: '', label: 'Select property information type' },
    { value: 'Subject Property', label: 'Subject Property' },
    { value: 'Investment Property', label: 'Investment Property' },
    { value: 'Second Home', label: 'Second Home' },
  ],
  loanQualificationType: [
    { value: '', label: 'Select loan qualification type' },
    { value: 'QM', label: 'QM' },
    { value: 'Non-QM', label: 'Non-QM' },
    { value: 'DSCR', label: 'DSCR' },
  ],
  mortgageInsuranceType: [
    { value: '', label: 'Select mortgage insurance type' },
    { value: 'None', label: 'None' },
    { value: 'Borrower Paid', label: 'Borrower Paid' },
    { value: 'Lender Paid', label: 'Lender Paid' },
  ],
  miOptionType: [
    { value: '', label: 'Select MI option type' },
    { value: 'None', label: 'None' },
    { value: 'Monthly', label: 'Monthly' },
    { value: 'Single Premium', label: 'Single Premium' },
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
  rentalIncomeMightBeUsed: yesNoOptions('Select rental income use'),
  condoApprovalType: [
    { value: '', label: 'Select condo approval type' },
    { value: 'Not Applicable', label: 'Not Applicable' },
    { value: 'Warrantable', label: 'Warrantable' },
    { value: 'Non-Warrantable', label: 'Non-Warrantable' },
  ],
  compensationPaidType: [
    { value: '', label: 'Select compensation paid type' },
    { value: 'Borrower Paid', label: 'Borrower Paid' },
    { value: 'Lender Paid', label: 'Lender Paid' },
  ],
  vaLoanType: [
    { value: '', label: 'Select VA loan type' },
    { value: 'Purchase', label: 'Purchase' },
    { value: 'IRRRL', label: 'IRRRL' },
    { value: 'Cash-Out', label: 'Cash-Out' },
  ],
  vaFundingFeeExemptionType: [
    { value: '', label: 'Select VA funding fee exemption' },
    { value: 'Completely Exempt', label: 'Completely Exempt' },
  ],
  vaFirstTimeUse: yesNoOptions('Select VA first-time use'),
  refinancingType: [
    { value: '', label: 'Select refinancing type' },
    { value: 'Rate/Term', label: 'Rate/Term' },
    { value: 'Cash-Out', label: 'Cash-Out' },
  ],
};

function yesNoOptions(placeholder: string): SelectOption[] {
  return [{ value: '', label: placeholder }, { value: 'Yes', label: 'Yes' }, { value: 'No', label: 'No' }];
}

export type StepFieldsProps = {
  fields: ScenarioIntakeField[];
  intake: BorrowerIntake;
  errors: IntakeFieldErrors;
  onChange: (field: keyof BorrowerIntake, value: string) => void;
  dropdownOptions?: DropdownOptionsByField;
  dropdownLoading?: boolean;
};

export function StepFields({ fields, intake, errors, onChange, dropdownOptions, dropdownLoading = false }: StepFieldsProps) {
  if (fields.length === 0) {
    return <p className="quote-intake-empty" role="status">No fields are configured for this step. Review intake metadata setup before launch.</p>;
  }

  return (
    <div className="quote-intake-fields">
      {fields.map((field, index) => {
        const fieldKey = `${String(field.fieldId)}-${index}`;
        if (isHeaderMetadataField(field)) return <MetadataSectionHeader key={fieldKey} field={field} />;
        if (!isBorrowerIntakeField(String(field.fieldId))) return null;
        const borrowerField = field as ScenarioIntakeField & { fieldId: keyof BorrowerIntake };
        return <MetadataDrivenField key={fieldKey} field={borrowerField} value={intake[borrowerField.fieldId] ?? ''} error={errors[borrowerField.fieldId]} onChange={onChange} dropdownOptions={dropdownOptions} dropdownLoading={dropdownLoading} />;
      })}
    </div>
  );
}

function MetadataSectionHeader({ field }: { field: ScenarioIntakeField }) {
  return (
    <div className="quote-intake-section-header" role="separator" aria-label={field.label} data-field-id={String(field.fieldId)}>
      <span>{field.label}</span>
      {field.helpText ? <small>{field.helpText}</small> : null}
    </div>
  );
}

function MetadataDrivenField({
  field,
  value,
  error,
  onChange,
  dropdownOptions,
  dropdownLoading,
}: {
  field: ScenarioIntakeField;
  value: string;
  error?: string;
  onChange: (field: keyof BorrowerIntake, value: string) => void;
  dropdownOptions?: DropdownOptionsByField;
  dropdownLoading: boolean;
}) {
  const helpTooltip = field.showHelpIcon ? field.helpTooltip : undefined;
  const selectOptions = optionsForField(field, value, dropdownOptions, dropdownLoading);
  const renderedValue = displayValue(field.fieldId, value);
  const validationMessages = field.validationMessages ?? [];
  const errorId = error ? `${field.fieldId}-error` : undefined;
  const guidanceId = validationMessages.length > 0 ? `${field.fieldId}-guidance` : undefined;
  const describedBy = [errorId, guidanceId].filter(Boolean).join(' ') || undefined;
  const commonProps = {
    id: field.fieldId,
    name: field.fieldId,
    value: renderedValue,
    required: field.required,
    'aria-invalid': Boolean(error),
    'aria-describedby': describedBy,
    'aria-errormessage': errorId,
    onChange: (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => onChange(field.fieldId, event.target.value),
  };

  return (
    <div className={`${fieldValueType(field) === 'textarea' ? 'quote-intake-field quote-intake-field--wide' : 'quote-intake-field'}${error ? ' quote-intake-field--error' : ''}`}>
      <div className="quote-intake-field-label">
        <label htmlFor={field.fieldId}>{field.label}{field.required ? <span className="quote-intake-required-badge" aria-label="required">*</span> : null}</label>
        {helpTooltip ? <button type="button" className="quote-intake-help-icon" aria-label={helpTooltip} title={helpTooltip}><HelpIcon size={16} /></button> : null}
      </div>
      {selectOptions ? (
        <select {...commonProps} disabled={dropdownLoading && isConfigBackedDropdown(field.fieldId)}>
          {selectOptions.map((option) => <option key={option.value || 'empty'} value={option.value}>{option.label}</option>)}
        </select>
      ) : fieldValueType(field) === 'textarea' ? (
        <textarea {...commonProps} rows={4} />
      ) : (
        <input {...commonProps} {...numericConstraintAttributes(field)} type={inputTypeForField(field)} inputMode={fieldValueType(field) === 'duration' ? 'numeric' : undefined} />
      )}
      {error ? <p id={errorId} className="quote-intake-field-error" role="alert">{error}</p> : null}
      {validationMessages.length > 0 ? (
        <ul id={guidanceId} className="quote-intake-validation-hints" aria-label={`${field.label} validation guidance`}>
          {validationMessages.map((message) => <li key={message}>{message}</li>)}
        </ul>
      ) : null}
    </div>
  );
}

function inputTypeForField(field: ScenarioIntakeField) {
  const type = fieldValueType(field);
  if (type === 'email') return 'email';
  if (type === 'number' || type === 'duration') return 'number';
  if (type === 'date' || dateLikeField(field.fieldId)) return 'date';
  return 'text';
}

function numericConstraintAttributes(field: ScenarioIntakeField) {
  const type = fieldValueType(field);
  if (type !== 'number' && type !== 'duration') return {};
  const attributes: Record<string, string> = {};
  const minimum = firstNumericConstraint(field, ['minimum', 'min', 'minValue']);
  const maximum = firstNumericConstraint(field, ['maximum', 'max', 'maxValue']);
  const step = firstNumericConstraint(field, ['step', 'increment']) || precisionStep(field);
  if (minimum) attributes.min = minimum;
  if (maximum) attributes.max = maximum;
  if (step) attributes.step = step;
  return attributes;
}

function firstNumericConstraint(field: ScenarioIntakeField, keys: string[]) {
  const source = field as ScenarioIntakeField & { constraints?: Record<string, unknown>; [key: string]: unknown };
  const valueType = valueTypeRecord(field);
  for (const key of keys) {
    const value = source.constraints?.[key] ?? source[key] ?? valueType?.[key];
    const numericValue = normalizedNumericAttribute(value);
    if (numericValue) return numericValue;
  }
  return '';
}

function precisionStep(field: ScenarioIntakeField) {
  const precision = firstNumericConstraint(field, ['precision']);
  if (!precision) return '';
  const precisionNumber = Number(precision);
  if (!Number.isInteger(precisionNumber) || precisionNumber < 0 || precisionNumber > 8) return '';
  return precisionNumber === 0 ? '1' : `0.${'0'.repeat(Math.max(0, precisionNumber - 1))}1`;
}

function normalizedNumericAttribute(value: unknown) {
  if (typeof value === 'number' && Number.isFinite(value)) return String(value);
  if (typeof value !== 'string') return '';
  const trimmed = value.trim();
  if (!trimmed || Number.isNaN(Number(trimmed))) return '';
  return trimmed;
}

function dateLikeField(fieldId: keyof BorrowerIntake) {
  return /Date$/.test(fieldId);
}

function optionsForField(field: ScenarioIntakeField, value: string, dropdownOptions?: DropdownOptionsByField, dropdownLoading = false): SelectOption[] | undefined {
  const fieldId = field.fieldId;
  const inlineOptions = field.options ?? field.enumOptions ?? valueTypeOptions(field);
  if (fieldValueType(field) === 'enum' && inlineOptions && inlineOptions.length > 0) return withCurrentValue(inlineOptions, value);
  if (dropdownLoading && isConfigBackedDropdown(fieldId)) return value ? [{ value, label: friendlyEnumLabel(value) }, { value: '', label: 'Loading options...' }] : [{ value: '', label: 'Loading options...' }];
  const configuredOptions = dropdownOptions?.[fieldId];
  const options = configuredOptions && configuredOptions.length > 0 ? configuredOptions : selectOptionsByField[fieldId];
  if (!options) return undefined;
  return withCurrentValue(options, value);
}

function fieldValueType(field: ScenarioIntakeField) {
  const valueType = valueTypeRecord(field)?.type;
  return typeof valueType === 'string' && valueType ? valueType : field.dataType;
}

function valueTypeRecord(field: ScenarioIntakeField): Record<string, unknown> | null {
  return typeof field.valueType === 'object' && field.valueType !== null ? field.valueType as Record<string, unknown> : null;
}

function valueTypeOptions(field: ScenarioIntakeField): SelectOption[] | undefined {
  const valueType = valueTypeRecord(field);
  if (!valueType) return undefined;
  const options = valueType.options ?? valueType.enumOptions ?? valueType.variants;
  if (!Array.isArray(options)) return undefined;
  const normalized = options.map((option) => {
    if (!option || typeof option !== 'object') return null;
    const record = option as Record<string, unknown>;
    const value = stringOption(record.value) ?? stringOption(record.id) ?? stringOption(record.variantId);
    if (!value) return null;
    return { value, label: stringOption(record.label) ?? stringOption(record.name) ?? friendlyEnumLabel(value) };
  }).filter((option): option is SelectOption => Boolean(option));
  return normalized.length > 0 ? normalized : undefined;
}

function stringOption(value: unknown) {
  return typeof value === 'string' && value.trim() ? value : null;
}

function withCurrentValue(options: SelectOption[], value: string) {
  if (!value || options.some((option) => option.value === value)) return options;
  return [...options, { value, label: friendlyEnumLabel(value) }];
}

function isConfigBackedDropdown(fieldId: keyof BorrowerIntake) {
  return ['mortgageType', 'investorCode', 'channel', 'channelCode'].includes(fieldId);
}

function displayValue(fieldId: keyof BorrowerIntake, value: string) {
  if ((fieldId === 'purchasePrice' || fieldId === 'appraisedValue') && value.trim() === '-1') return '';
  return value;
}

function friendlyEnumLabel(value: string) {
  if (/^(corr|correspondent|retail|wholesale|tpo|consumer_direct)$/i.test(value.trim())) return displayChannelLabel(value);
  return value
    .toLowerCase()
    .split('_')
    .map((part) => part ? part[0].toUpperCase() + part.slice(1) : part)
    .join(' ')
    .replace(/\bW2\b/i, 'W-2');
}
