import { MortgageInput } from '../../../components/MortgageInput';
import type { BorrowerIntake } from '../../../lib/api/quoteRuns';

export type Step6ProductLockProps = {
  intake: BorrowerIntake;
  errors: Partial<Record<keyof BorrowerIntake, string>>;
  onChange: (field: keyof BorrowerIntake, value: string) => void;
};

export function Step6ProductLock({ intake, errors, onChange }: Step6ProductLockProps) {
  return (
    <fieldset className="quick-quote-section">
      <legend>Step 6: Product & Lock Preferences</legend>
      <p className="field-help">Specify product preferences and pricing filters for quote comparison.</p>
      <div className="quick-quote-minimal">
        <MortgageInput
          id="productFamily"
          label="Product family preference"
          value={intake.productFamily}
          error={errors.productFamily}
          onChange={onChange}
        />
        <MortgageInput
          id="productPreference"
          label="Product preference notes"
          value={intake.productPreference}
          error={errors.productPreference}
          onChange={onChange}
        />
        <MortgageInput
          id="quoteFilters"
          label="Quote filters"
          value={intake.quoteFilters}
          error={errors.quoteFilters}
          onChange={onChange}
        />
        <MortgageInput
          id="effectiveDate"
          label="Effective date"
          value={intake.effectiveDate}
          error={errors.effectiveDate}
          type="date"
          onChange={onChange}
        />
      </div>
    </fieldset>
  );
}
