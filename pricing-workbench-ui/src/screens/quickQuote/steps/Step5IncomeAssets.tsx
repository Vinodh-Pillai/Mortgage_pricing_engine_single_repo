import { MortgageInput } from '../../../components/MortgageInput';
import type { BorrowerIntake } from '../../../lib/api/quoteRuns';

export interface Step5IncomeAssetsProps {
  intake: BorrowerIntake;
  errors: Partial<Record<keyof BorrowerIntake, string>>;
  onChange: (field: keyof BorrowerIntake, value: string) => void;
}

export function Step5IncomeAssets({ intake, errors, onChange }: Step5IncomeAssetsProps) {
  return (
    <fieldset className="quick-quote-section">
      <legend>Step 5: Income & Assets (PII-01-S05)</legend>
      <p className="field-help">Enter income, debt, reserves, and verification status. System derives DTI and capacity metrics.</p>
      <div className="quick-quote-minimal">
        <MortgageInput
          id="monthlyIncome"
          label="Monthly qualifying income"
          value={intake.monthlyIncome}
          error={errors.monthlyIncome}
          type="number"
          onChange={onChange}
        />
        <MortgageInput
          id="incomeType"
          label="Income type"
          value={intake.incomeType}
          error={errors.incomeType}
          onChange={onChange}
          as="select"
          options={[
            { value: 'W2', label: 'W-2' },
            { value: 'SELF_EMPLOYED', label: 'Self-Employed' },
            { value: 'RETIREMENT', label: 'Retirement' },
            { value: 'OTHER', label: 'Other' },
          ]}
        />
        <MortgageInput
          id="employmentType"
          label="Employment type"
          value={intake.employmentType}
          error={errors.employmentType}
          onChange={onChange}
          as="select"
          options={[
            { value: 'SALARIED', label: 'Salaried' },
            { value: 'HOURLY', label: 'Hourly' },
            { value: 'COMMISSION', label: 'Commission' },
            { value: 'SELF_EMPLOYED', label: 'Self-Employed' },
          ]}
        />
        <MortgageInput
          id="monthlyDebt"
          label="Monthly debt"
          value={intake.monthlyDebt}
          error={errors.monthlyDebt}
          type="number"
          onChange={onChange}
        />
        <MortgageInput
          id="suppliedDti"
          label="Supplied DTI % (optional)"
          value={intake.suppliedDti}
          error={errors.suppliedDti}
          type="number"
          onChange={onChange}
        />
        <MortgageInput
          id="reserveMonths"
          label="Reserve months"
          value={intake.reserveMonths}
          error={errors.reserveMonths}
          type="number"
          onChange={onChange}
        />
        <MortgageInput
          id="incomeVerificationStatus"
          label="Income verification status"
          value={intake.incomeVerificationStatus}
          error={errors.incomeVerificationStatus}
          onChange={onChange}
          as="select"
          options={[
            { value: 'VERIFIED', label: 'Verified' },
            { value: 'STATED', label: 'Stated' },
            { value: 'UNKNOWN', label: 'Unknown' },
            { value: 'NOT_REQUIRED', label: 'Not Required' },
          ]}
        />
        <MortgageInput
          id="assetVerificationStatus"
          label="Asset verification status"
          value={intake.assetVerificationStatus}
          error={errors.assetVerificationStatus}
          onChange={onChange}
          as="select"
          options={[
            { value: 'VERIFIED', label: 'Verified' },
            { value: 'STATED', label: 'Stated' },
            { value: 'UNKNOWN', label: 'Unknown' },
            { value: 'NOT_REQUIRED', label: 'Not Required' },
          ]}
        />
        <MortgageInput
          id="liquidAssets"
          label="Liquid assets available"
          value={intake.liquidAssets}
          error={errors.liquidAssets}
          type="number"
          onChange={onChange}
        />
        <MortgageInput
          id="reserves"
          label="Reserves (total)"
          value={intake.reserves}
          error={errors.reserves}
          type="number"
          onChange={onChange}
        />
      </div>
    </fieldset>
  );
}
