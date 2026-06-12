import type { QuoteIntakeStepDefinition, QuoteIntakeStepId } from './metadata';

export type StepStatus = 'empty' | 'in-progress' | 'complete' | 'error';

export function ProgressIndicator({
  steps,
  activeStep,
  statuses,
  onStepSelect,
}: {
  steps: QuoteIntakeStepDefinition[];
  activeStep: QuoteIntakeStepId;
  statuses: Record<QuoteIntakeStepId, StepStatus>;
  onStepSelect: (step: QuoteIntakeStepId) => void;
}) {
  const completedCount = steps.filter((step) => statuses[step.id] === 'complete').length;
  return (
    <nav className="quote-intake-progress" aria-label="Quote intake progress">
      <div className="quote-intake-progress__bar" role="progressbar" aria-valuemin={1} aria-valuemax={steps.length} aria-valuenow={activeStep} aria-valuetext={`Step ${activeStep} of ${steps.length}`}>
        <span style={{ inlineSize: `${(completedCount / steps.length) * 100}%` }} />
      </div>
      <ol>
        {steps.map((step) => {
          const status = step.id === activeStep ? 'in-progress' : statuses[step.id];
          const canSelect = step.id === activeStep || statuses[step.id] === 'complete' || step.id < activeStep;
          return (
            <li key={step.id} data-status={status}>
              <button type="button" disabled={!canSelect} aria-current={step.id === activeStep ? 'step' : undefined} onClick={() => onStepSelect(step.id)}>
                <span className="quote-intake-progress__number" aria-hidden="true">{step.id}</span>
                <span>{step.shortLabel}</span>
                <small>{statusLabel(status)}</small>
              </button>
            </li>
          );
        })}
      </ol>
    </nav>
  );
}

function statusLabel(status: StepStatus) {
  if (status === 'in-progress') return 'in progress';
  if (status === 'complete') return 'complete';
  if (status === 'error') return 'needs attention';
  return 'empty';
}
