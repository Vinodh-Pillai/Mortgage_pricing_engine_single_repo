import { QuoteIntakeFlow, initialQuoteIntake } from './QuoteIntakeFlow';
import { fallbackMetadata } from './metadata';
import { ProgressIndicator } from './ProgressIndicator';
import { quoteIntakeSteps } from './metadata';

export default { title: 'PII-25/Quote Intake/Progressive Flow' };

const metadataState = { kind: 'loaded' as const, metadata: fallbackMetadata() };

export function FullFlowEmpty() { return <QuoteIntakeFlow metadataState={metadataState} />; }
export function ResumeDraftScenario() { return <QuoteIntakeFlow metadataState={metadataState} intake={{ ...initialQuoteIntake, quoteIntent: 'Purchase', channel: 'Retail' }} />; }
export function Step1ScenarioIdentityEmpty() { return <QuoteIntakeFlow metadataState={metadataState} />; }
export function Step2BorrowerCreditValid() { return <QuoteIntakeFlow metadataState={metadataState} intake={{ ...initialQuoteIntake, quoteIntent: 'Purchase', channel: 'Retail', borrowerName: 'Alex Borrower', contactEmail: 'alex@example.test' }} />; }
export function Step3LoanStructureValid() { return <QuoteIntakeFlow metadataState={metadataState} intake={{ ...initialQuoteIntake, loanAmount: '425000' }} />; }
export function Step4PropertyValid() { return <QuoteIntakeFlow metadataState={metadataState} intake={{ ...initialQuoteIntake, propertyState: 'CA', propertyZip: '90001' }} />; }
export function Step5IncomeAssetsValid() { return <QuoteIntakeFlow metadataState={metadataState} intake={{ ...initialQuoteIntake, monthlyIncome: '12000', monthlyDebt: '2500' }} />; }
export function Step6PreferencesReady() { return <QuoteIntakeFlow metadataState={metadataState} intake={{ ...initialQuoteIntake, productFamily: 'Configured product family', effectiveDate: '2026-06-11' }} />; }
export function ProgressIndicatorStates() {
  return <ProgressIndicator steps={quoteIntakeSteps} activeStep={3} statuses={{ 1: 'complete', 2: 'complete', 3: 'in-progress', 4: 'empty', 5: 'error', 6: 'empty' }} onStepSelect={() => undefined} />;
}
