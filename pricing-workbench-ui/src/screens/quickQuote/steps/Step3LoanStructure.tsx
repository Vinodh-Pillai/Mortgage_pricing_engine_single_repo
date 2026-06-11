import { MortgageInput } from '../../../components/MortgageInput';
import type { BorrowerIntake } from '../../../lib/api/quoteRuns';

export function Step3LoanStructure({
  intake,
  errors,
  onChange,
}: {
  intake: BorrowerIntake;
  errors: Partial<Record<keyof BorrowerIntake, string>>;
  onChange: (field: keyof BorrowerIntake, value: string) => void;
}) {
  return (
    <fieldset className="quick-quote-section">
      <legend>Step 3: Loan Structure (PII-01-S03)</legend>
      <p className="field-help">Define loan terms, amounts, and lock period. System calculates LTV, CLTV, HCLTV for pricing and eligibility.</p>
      <div className="quick-quote-minimal">
        <MortgageInput
          id="loanPurpose"
          label="Loan purpose"
          value={intake.loanPurpose}
          error={errors.loanPurpose}
          onChange={onChange}
          as="select"
          options={[
            { value: '', label: 'Select loan purpose' },
            { value: 'PURCHASE', label: 'Purchase' },
            { value: 'RATE_TERM_REFI', label: 'Rate/Term Refinance' },
            { value: 'CASH_OUT_REFI', label: 'Cash-Out Refinance' },
          ]}
        />
        <MortgageInput
          id="loanAmount"
          label="Loan amount"
          value={intake.loanAmount}
          error={errors.loanAmount}
          type="number"
          onChange={onChange}
        />
        <MortgageInput
          id="lienPosition"
          label="Lien position"
          value={intake.lienPosition}
          error={errors.lienPosition}
          onChange={onChange}
          as="select"
          options={[
            { value: 'FIRST', label: 'First Lien' },
            { value: 'SECOND', label: 'Second Lien' },
          ]}
        />
        <MortgageInput
          id="termMonths"
          label="Term (months)"
          value={intake.termMonths}
          error={errors.termMonths}
          onChange={onChange}
          as="select"
          options={[
            { value: '180', label: '15 Year (180)' },
            { value: '240', label: '20 Year (240)' },
            { value: '360', label: '30 Year (360)' },
          ]}
        />
        <MortgageInput
          id="amortizationType"
          label="Amortization type"
          value={intake.amortizationType}
          error={errors.amortizationType}
          onChange={onChange}
          as="select"
          options={[
            { value: 'FIXED', label: 'Fixed' },
            { value: 'ARM', label: 'ARM' },
          ]}
        />
        <MortgageInput
          id="subordinateFinancingAmount"
          label="Subordinate financing amount"
          value={intake.subordinateFinancingAmount}
          error={errors.subordinateFinancingAmount}
          type="number"
          onChange={onChange}
        />
        <MortgageInput
          id="helocDrawnAmount"
          label="HELOC drawn amount"
          value={intake.helocDrawnAmount}
          error={errors.helocDrawnAmount}
          type="number"
          onChange={onChange}
        />
        <MortgageInput
          id="helocLimitAmount"
          label="HELOC limit amount"
          value={intake.helocLimitAmount}
          error={errors.helocLimitAmount}
          type="number"
          onChange={onChange}
        />
        <MortgageInput
          id="requestedLockPeriodDays"
          label="Requested lock period (days)"
          value={intake.requestedLockPeriodDays}
          error={errors.requestedLockPeriodDays}
          onChange={onChange}
          as="select"
          options={[
            { value: '15', label: '15 days' },
            { value: '30', label: '30 days' },
            { value: '45', label: '45 days' },
            { value: '60', label: '60 days' },
          ]}
        />
      </div>
    </fieldset>
  );
}
