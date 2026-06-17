import { PipelineIntakePage, initialQuoteIntake, type PipelineIntakePageProps } from './PipelineIntakePage';

export type QuoteIntakeFlowProps = PipelineIntakePageProps;

export function QuoteIntakeFlow(props: QuoteIntakeFlowProps) {
  return <PipelineIntakePage {...props} />;
}

export { initialQuoteIntake };

export default QuoteIntakeFlow;
