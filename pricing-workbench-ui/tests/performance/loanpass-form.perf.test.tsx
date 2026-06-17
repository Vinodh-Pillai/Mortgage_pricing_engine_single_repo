import '@testing-library/jest-dom/vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { QuoteIntakeFlow } from '../../src/screens/quoteIntake/QuoteIntakeFlow';
import { loanPassMetadata, loanPassMinimumIntake } from '../integration/loanpass/loanpass-test-fixtures';

describe('PII-26-S17 LoanPass local performance budgets', () => {
  it('mounts the LoanPass pipeline intake form under the approved local 3500ms jsdom budget', () => {
    const startedAt = performance.now();
    render(<QuoteIntakeFlow metadataState={{ kind: 'loaded', metadata: loanPassMetadata() }} />);
    const durationMs = performance.now() - startedAt;

    expect(screen.getByRole('heading', { name: /^Intake$/i })).toBeInTheDocument();
    expect(durationMs).toBeLessThan(3500);
  });

  it('handles a visible key field change under the approved local 500ms jsdom interaction budget', () => {
    render(<QuoteIntakeFlow metadataState={{ kind: 'loaded', metadata: loanPassMetadata() }} />);
    const loanNumber = screen.getByRole('textbox', { name: /^Loan Number/i });

    const startedAt = performance.now();
    fireEvent.change(loanNumber, { target: { value: 'LP-2002' } });
    const durationMs = performance.now() - startedAt;

    expect(loanNumber).toHaveValue('LP-2002');
    expect(durationMs).toBeLessThan(500);
  });

  it('renders local launch validation under the approved local 1000ms jsdom interaction budget', () => {
    render(<QuoteIntakeFlow metadataState={{ kind: 'loaded', metadata: loanPassMetadata() }} intake={loanPassMinimumIntake} />);
    const launchButton = screen.getByRole('button', { name: /^Launch Quote$/i });

    const startedAt = performance.now();
    fireEvent.click(launchButton);
    const durationMs = performance.now() - startedAt;

    expect(screen.getAllByRole('alert').some((alert) => alert.textContent?.includes('Complete required quote fields'))).toBe(true);
    expect(durationMs).toBeLessThan(1000);
  });
});
