import { MortgageInput } from '../../../components/MortgageInput';
import type { BorrowerIntake } from '../../../lib/api/quoteRuns';

export function Step1DraftScenario({
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
      <legend>Step 1: Create draft scenario <span className="required-badge">Required</span></legend>
      <p className="field-help">Select quote intent and channel to create a draft scenario. This establishes the pricing workflow and required sections.</p>
      <div className="quick-quote-minimal">
        <MortgageInput
          id="quoteIntent"
          label="Quote intent *"
          value={intake.quoteIntent}
          error={errors.quoteIntent}
          onChange={onChange}
          as="select"
          options={[
            { value: '', label: 'Select quote intent' },
            { value: 'PURCHASE', label: 'Purchase' },
            { value: 'RATE_TERM_REFI', label: 'Rate/Term Refinance' },
            { value: 'CASH_OUT_REFI', label: 'Cash-Out Refinance' },
            { value: 'SCENARIO_ANALYSIS', label: 'Scenario Analysis' },
          ]}
        />
        <MortgageInput
          id="channel"
          label="Channel *"
          value={intake.channel}
          error={errors.channel}
          onChange={onChange}
          as="select"
          options={[
            { value: '', label: 'Select channel' },
            { value: 'RETAIL', label: 'Retail' },
            { value: 'WHOLESALE', label: 'Wholesale' },
            { value: 'CORRESPONDENT', label: 'Correspondent' },
            { value: 'CONSUMER_DIRECT', label: 'Consumer Direct' },
          ]}
        />
        <MortgageInput
          id="scenarioName"
          label="Scenario name (optional)"
          value={intake.scenarioName}
          error={errors.scenarioName}
          onChange={onChange}
          placeholder="e.g., Smith purchase - initial quote"
        />
        <MortgageInput
          id="externalLoanId"
          label="External loan ID (optional)"
          value={intake.externalLoanId}
          error={errors.externalLoanId}
          onChange={onChange}
          placeholder="LOS/POS correlation ID"
        />
      </div>
    </fieldset>
  );
}
