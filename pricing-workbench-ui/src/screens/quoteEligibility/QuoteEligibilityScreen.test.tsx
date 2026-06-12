import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import QuoteEligibilityScreen, { stateForEligibility } from './QuoteEligibilityScreen';
import { blockedEligibilityFixture, conditionalEligibilityFixture, eligibleEligibilityFixture, ineligibleEligibilityFixture } from './fixtures';
import { quoteEligibilityScreenModule } from './index';

afterEach(() => cleanup());

describe('PII-24-S13 eligibility explanation screen', () => {
  it('defines the quote eligibility route module and evidence target', () => {
    expect(quoteEligibilityScreenModule.routePattern).toBe('/quote/:runId/eligibility');
    expect(quoteEligibilityScreenModule.evidenceTarget).toBe('.local-harness/evidence/PII-24-S13/quote-eligibility.json');
    expect(quoteEligibilityScreenModule.match('/quote/run-preview-001/eligibility')).toBe(true);
    expect(quoteEligibilityScreenModule.stateCoverage).toEqual(expect.arrayContaining(['eligible', 'ineligible', 'conditional', 'cache-health']));
  });

  it('renders eligible decision badge, facts, overlay refs, cache freshness, and evidence capture', () => {
    const onEvidenceCapture = vi.fn();
    render(<QuoteEligibilityScreen module={eligibleEligibilityFixture} onEvidenceCapture={onEvidenceCapture} />);

    expect(screen.getByRole('heading', { name: /Eligibility Explanation/i })).toBeInTheDocument();
    expect(screen.getByRole('status', { name: /Eligibility decision ELIGIBLE/i })).toHaveTextContent('ELIGIBLE');
    expect(screen.getByText('Credit profile package')).toBeInTheDocument();
    expect(screen.getByText('overlay-investor-a')).toBeInTheDocument();
    expect(screen.getAllByText('FRESH').length).toBeGreaterThan(0);
    expect(onEvidenceCapture).toHaveBeenCalledWith(expect.objectContaining({ screenId: 'quote-eligibility', state: 'ready' }));
  });

  it('renders conditional blockers and deep-links required facts to intake', () => {
    const onNavigate = vi.fn();
    render(<QuoteEligibilityScreen module={conditionalEligibilityFixture} onNavigate={onNavigate} />);

    expect(screen.getByRole('status', { name: /Eligibility decision CONDITIONAL/i })).toHaveTextContent('CONDITIONAL');
    expect(screen.getByText('MISSING_REQUIRED_FACT')).toBeInTheDocument();
    expect(screen.getAllByText('Income verification package').length).toBeGreaterThan(0);
    fireEvent.click(screen.getByRole('button', { name: /Complete in Intake/i }));
    expect(onNavigate).toHaveBeenCalledWith('/pipeline?step=income&highlight=fact%3Aincome-verification-package');
  });

  it('renders ineligible backend blockers without deriving eligibility rules locally', () => {
    render(<QuoteEligibilityScreen module={ineligibleEligibilityFixture} />);

    expect(screen.getByRole('status', { name: /Eligibility decision INELIGIBLE/i })).toHaveTextContent('INELIGIBLE');
    expect(screen.getByText('INVESTOR_OVERLAY_FAILED')).toBeInTheDocument();
    expect(screen.getByText('hard blocker returned')).toBeInTheDocument();
    expect(stateForEligibility(ineligibleEligibilityFixture)).toBe('needs-attention');
  });

  it('renders blocked cache-missing state for unavailable connected runtime', () => {
    render(<QuoteEligibilityScreen module={blockedEligibilityFixture} />);

    expect(screen.getByRole('alert')).toHaveTextContent(/Eligibility view blocked/i);
    expect(screen.getAllByText('MISSING').length).toBeGreaterThan(0);
    expect(screen.getByText('eligibility-service:unavailable')).toBeInTheDocument();
    expect(stateForEligibility(blockedEligibilityFixture)).toBe('blocked');
  });
});
