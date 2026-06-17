import '@testing-library/jest-dom/vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { QuoteIntakeFlow } from '../../src/screens/quoteIntake/QuoteIntakeFlow';
import { loanPassMetadata, loanPassMinimumIntake } from '../integration/loanpass/loanpass-test-fixtures';

describe('PII-26-S17 LoanPass local performance budgets', () => {
  it('mounts the LoanPass pipeline intake form under the approved local 200ms budget', () => {
    const startedAt = performance.now();
    render(<QuoteIntakeFlow metadataState={{ kind: 'loaded', metadata: loanPassMetadata() }} />);
    const durationMs = performance.now() - startedAt;

    expect(screen.getByRole('heading', { name: /Pipeline Intake/i })).toBeInTheDocument();
    expect(durationMs).toBeLessThan(200);
  });

  it('handles a field change under the approved local 50ms interaction budget', () => {
    render(<QuoteIntakeFlow metadataState={{ kind: 'loaded', metadata: loanPassMetadata() }} />);
    fireEvent.click(screen.getByRole('button', { name: /Borrower & Credit/i }));
    const borrowerFirstName = screen.getByRole('textbox', { name: /^Borrower First Name/i });

    const startedAt = performance.now();
    fireEvent.change(borrowerFirstName, { target: { value: 'Alex' } });
    const durationMs = performance.now() - startedAt;

    expect(borrowerFirstName).toHaveValue('Alex');
    expect(durationMs).toBeLessThan(50);
  });

  it('expands a collapsible LoanPass section under the approved local 100ms budget', () => {
    render(<QuoteIntakeFlow metadataState={{ kind: 'loaded', metadata: loanPassMetadata() }} intake={loanPassMinimumIntake} />);
    const borrowerSectionToggle = screen.getByRole('button', { name: /Borrower & Credit/i });

    const startedAt = performance.now();
    fireEvent.click(borrowerSectionToggle);
    const durationMs = performance.now() - startedAt;

    expect(borrowerSectionToggle).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByRole('textbox', { name: /^Borrower First Name/i })).toBeInTheDocument();
    expect(durationMs).toBeLessThan(100);
  });
});
