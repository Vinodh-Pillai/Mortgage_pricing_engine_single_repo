import { MortgageInput } from '../../../components/MortgageInput';
import type { BorrowerIntake } from '../../../lib/api/quoteRuns';

export function Step2BorrowerCredit({
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
      <legend>Step 2: Borrower & Credit (PII-01-S02)</legend>
      <p className="field-help">Add primary borrower and optional co-borrowers with credit scores, sources, and dates. System derives representative credit score for pricing.</p>
      <div className="quick-quote-minimal">
        <MortgageInput
          id="borrowerName"
          label="Borrower name *"
          value={intake.borrowerName}
          error={errors.borrowerName}
          onChange={onChange}
        />
        <MortgageInput
          id="borrowerRole"
          label="Borrower role"
          value={intake.borrowerRole}
          error={errors.borrowerRole}
          onChange={onChange}
          as="select"
          options={[
            { value: 'PRIMARY', label: 'Primary' },
            { value: 'CO_BORROWER', label: 'Co-Borrower' },
            { value: 'NON_OCCUPANT_CO_BORROWER', label: 'Non-Occupant Co-Borrower' },
          ]}
        />
        <MortgageInput
          id="coBorrowerName"
          label="Co-borrower name (optional)"
          value={intake.coBorrowerName}
          error={errors.coBorrowerName}
          onChange={onChange}
        />
        <MortgageInput
          id="coBorrowerRole"
          label="Co-borrower role"
          value={intake.coBorrowerRole}
          error={errors.coBorrowerRole}
          onChange={onChange}
          as="select"
          options={[
            { value: 'CO_BORROWER', label: 'Co-Borrower' },
            { value: 'NON_OCCUPANT_CO_BORROWER', label: 'Non-Occupant Co-Borrower' },
          ]}
        />
        <MortgageInput
          id="contactEmail"
          label="Contact email *"
          value={intake.contactEmail}
          error={errors.contactEmail}
          type="email"
          onChange={onChange}
        />
        <MortgageInput
          id="creditStatus"
          label="Credit status"
          value={intake.creditStatus}
          error={errors.creditStatus}
          onChange={onChange}
          as="select"
          options={[
            { value: 'AVAILABLE', label: 'Available' },
            { value: 'MISSING', label: 'Missing' },
            { value: 'FROZEN', label: 'Frozen' },
            { value: 'NO_SCORE', label: 'No Score' },
          ]}
        />
        <MortgageInput
          id="creditScore"
          label="Representative credit score (300-850)"
          value={intake.creditScore}
          error={errors.creditScore}
          type="number"
          onChange={onChange}
        />
        <MortgageInput
          id="creditScoreSource"
          label="Credit score source"
          value={intake.creditScoreSource}
          error={errors.creditScoreSource}
          onChange={onChange}
          as="select"
          options={[
            { value: 'TRI_MERGE', label: 'Tri-Merge' },
            { value: 'DU', label: 'DU' },
            { value: 'LPA', label: 'LPA' },
            { value: 'MANUAL', label: 'Manual' },
          ]}
        />
        <MortgageInput
          id="creditReportDate"
          label="Credit report date"
          value={intake.creditReportDate}
          error={errors.creditReportDate}
          type="date"
          onChange={onChange}
        />
        <MortgageInput
          id="creditReadiness"
          label="Credit readiness notes"
          value={intake.creditReadiness}
          error={errors.creditReadiness}
          onChange={onChange}
        />
      </div>
    </fieldset>
  );
}
