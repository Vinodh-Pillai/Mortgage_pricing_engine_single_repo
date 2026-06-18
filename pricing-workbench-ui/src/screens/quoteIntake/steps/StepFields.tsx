import type { BorrowerIntake, DropdownOption, ScenarioIntakeField } from '../../../lib/api/quoteRuns';
import { HelpIcon } from '../../../design-system/icons';
import type { IntakeFieldErrors } from '../validation';

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
      {fields.map((field) => <MetadataDrivenField key={field.fieldId} field={field} value={intake[field.fieldId] ?? ''} error={errors[field.fieldId]} onChange={onChange} dropdownOptions={dropdownOptions} dropdownLoading={dropdownLoading} />)}
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
  const errorId = `${field.fieldId}-error`;
  const describedBy = error ? errorId : undefined;
  const helpTooltip = field.showHelpIcon ? field.helpTooltip : undefined;
  const selectOptions = optionsForField(field.fieldId, value, dropdownOptions, dropdownLoading);
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
        <select {...commonProps} disabled={dropdownLoading && isConfigBackedDropdown(field.fieldId)}>
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

function optionsForField(fieldId: keyof BorrowerIntake, value: string, dropdownOptions?: DropdownOptionsByField, dropdownLoading = false): SelectOption[] | undefined {
  if (dropdownLoading && isConfigBackedDropdown(fieldId)) return value ? [{ value, label: friendlyEnumLabel(value) }, { value: '', label: 'Loading options...' }] : [{ value: '', label: 'Loading options...' }];
  const configuredOptions = dropdownOptions?.[fieldId];
  const options = configuredOptions && configuredOptions.length > 0 ? configuredOptions : selectOptionsByField[fieldId];
  if (!options) return undefined;
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
  return value
    .toLowerCase()
    .split('_')
    .map((part) => part ? part[0].toUpperCase() + part.slice(1) : part)
    .join(' ')
    .replace(/\bW2\b/i, 'W-2');
}
