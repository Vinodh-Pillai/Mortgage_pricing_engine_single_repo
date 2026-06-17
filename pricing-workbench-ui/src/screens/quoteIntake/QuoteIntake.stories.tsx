import { QuoteIntakeFlow, initialQuoteIntake } from './QuoteIntakeFlow';
import { fallbackMetadata } from './metadata';
import { ProgressIndicator } from './ProgressIndicator';
import { quoteIntakeSteps } from './metadata';

export default { title: 'PII-25/Quote Intake/Progressive Flow' };

const metadataState = { kind: 'loaded' as const, metadata: fallbackMetadata() };

export function FullFlowEmpty() { return <QuoteIntakeFlow metadataState={metadataState} />; }
export function ResumeDraftScenario() { return <QuoteIntakeFlow metadataState={metadataState} intake={{ ...initialQuoteIntake, loanPurpose: 'Purchase', channel: 'Retail' }} />; }
export function Step1ScenarioIdentityEmpty() { return <QuoteIntakeFlow metadataState={metadataState} />; }
export function Step2BorrowerCreditValid() { return <QuoteIntakeFlow metadataState={metadataState} intake={{ ...initialQuoteIntake, loanPurpose: 'Purchase', channel: 'Retail', borrowerFirstName: 'Alex', borrowerLastName: 'Borrower', contactEmail: 'alex@example.test' }} />; }
export function Step3LoanStructureValid() { return <QuoteIntakeFlow metadataState={metadataState} intake={{ ...initialQuoteIntake, baseLoanAmount: '425000' }} />; }
export function Step4PropertyValid() { return <QuoteIntakeFlow metadataState={metadataState} intake={{ ...initialQuoteIntake, state: 'CA', zip: '90001' }} />; }
export function Step5ProductControlsValid() { return <QuoteIntakeFlow metadataState={metadataState} intake={{ ...initialQuoteIntake, totalBorrowerIncome: '12000', monthlyDebt: '2500', mortgageType: 'Conventional' }} />; }
export function ProgressIndicatorStates() {
  return <ProgressIndicator steps={quoteIntakeSteps} activeStep={3} statuses={{ 1: 'complete', 2: 'complete', 3: 'in-progress', 4: 'empty', 5: 'error' }} onStepSelect={() => undefined} />;
}
