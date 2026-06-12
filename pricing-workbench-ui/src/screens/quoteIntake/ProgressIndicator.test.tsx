import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { quoteIntakeSteps } from './metadata';
import { ProgressIndicator, type StepStatus } from './ProgressIndicator';

afterEach(() => cleanup());

describe('ProgressIndicatorTest', () => {
  it('updatesOnStepChangeAndIsKeyboardNavigable', () => {
    const onStepSelect = vi.fn();
    const statuses = quoteIntakeSteps.reduce((acc, step) => ({ ...acc, [step.id]: step.id === 1 ? 'complete' : 'empty' }), {} as Record<any, StepStatus>);
    render(<ProgressIndicator steps={quoteIntakeSteps} activeStep={2} statuses={statuses} onStepSelect={onStepSelect} />);
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '2');
    fireEvent.click(screen.getByRole('button', { name: /Identitycomplete/i }));
    expect(onStepSelect).toHaveBeenCalledWith(1);
  });
});
