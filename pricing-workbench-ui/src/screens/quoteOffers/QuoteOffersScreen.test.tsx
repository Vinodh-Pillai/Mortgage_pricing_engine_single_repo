import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import QuoteOffersScreen from './QuoteOffersScreen';
import { deterministicOfferComparison } from './fixtures';
import { filterOffers, sortOffers, toggleCompareOffer } from './offerComparison';

afterEach(() => cleanup());

describe('PII-24-S10 offer comparison screen', () => {
  it('sorts by backend rank by default and supports local sort/filter helpers', () => {
    const reversed = [...deterministicOfferComparison.offers].reverse();
    expect(sortOffers(reversed, { field: 'rank', direction: 'asc' }).map((offer) => offer.offerId)).toEqual(['offer-a', 'offer-b']);
    expect(filterOffers(deterministicOfferComparison.offers, { productFamily: 'FHA', investor: '', rateMax: '', confidenceMin: '', lockPeriodDays: '', eligibilityStatus: '' }).map((offer) => offer.offerId)).toEqual(['offer-b']);
  });

  it('renders key metrics, rationale chips, explanation preview, and blocked commit facts', () => {
    render(<QuoteOffersScreen comparison={deterministicOfferComparison} />);

    expect(screen.getByRole('heading', { name: /Compare Offers/i })).toBeInTheDocument();
    expect(screen.getByRole('table', { name: /Quote offer comparison/i })).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: /Inspect explanation/i }).length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText('Conventional 30 year fixed')).toBeInTheDocument();
    expect(screen.queryByText(/LoanHouse capture/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/loanhouse product records v1/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/LoanPass|quickpricer|get generic quote summary|source url|source_url|Backend rank|confidence|Rank score/i)).not.toBeInTheDocument();
    expect(screen.getAllByText(/99\.934/).length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText('PRICE_UNAVAILABLE')).toBeInTheDocument();

    fireEvent.click(screen.getByLabelText('Select offer FHA fixed'));
    expect(screen.getAllByRole('alert').some((alert) => /Commit blocked/i.test(alert.textContent ?? ''))).toBe(true);
    expect(screen.getByText('pricing-service quote price')).toBeInTheDocument();
  });

  it('keeps nested row controls from triggering row keyboard shortcuts and names controls by offer', () => {
    render(<QuoteOffersScreen comparison={deterministicOfferComparison} />);

    const conventionalRow = screen.getAllByRole('row')[1];
    const compareControl = screen.getByLabelText('Compare offer Conventional 30 year fixed');
    const inspectButton = screen.getByRole('button', { name: 'Inspect explanation for offer Conventional 30 year fixed' });

    fireEvent.keyDown(inspectButton, { key: 'Enter' });
    expect(conventionalRow).toHaveAttribute('aria-selected', 'false');

    fireEvent.keyDown(compareControl, { key: ' ' });
    expect(compareControl).not.toBeChecked();

    fireEvent.keyDown(conventionalRow, { key: 'Enter' });
    expect(conventionalRow).toHaveAttribute('aria-selected', 'true');

    fireEvent.keyDown(conventionalRow, { key: ' ' });
    expect(compareControl).toBeChecked();
  });

  it('keeps the desktop offer table wide with sticky header and action state instead of card layout', () => {
    render(<QuoteOffersScreen comparison={deterministicOfferComparison} />);

    const table = screen.getByRole('table', { name: /Quote offer comparison/i });
    expect(table).toHaveStyle({ minWidth: '1180px', width: '100%' });
    expect(table.parentElement).toHaveStyle({ overflowX: 'auto', maxWidth: '100%' });
    expect(screen.queryByRole('list', { name: /Offer cards/i })).not.toBeInTheDocument();

    const headerRow = screen.getAllByRole('row')[0];
    expect(headerRow).toHaveStyle({ position: 'sticky', top: '0px' });
    expect(screen.getByRole('columnheader', { name: /Actions/i })).toHaveStyle({ position: 'sticky', right: '0px' });
  });

  it('keeps selected row and filter count badge state stable as filters change', () => {
    render(<QuoteOffersScreen comparison={deterministicOfferComparison} />);

    fireEvent.click(screen.getByLabelText('Select offer Conventional 30 year fixed'));
    expect(screen.getAllByRole('row')[1]).toHaveAttribute('aria-selected', 'true');

    const activeFilterCount = screen.getByLabelText(/Active filter count/i);
    expect(activeFilterCount).toHaveStyle({ minWidth: '2ch' });
    expect(activeFilterCount).toHaveTextContent('0');

    fireEvent.change(screen.getByLabelText(/Product family/i), { target: { value: 'FHA' } });
    expect(activeFilterCount).toHaveTextContent('1');
    expect(screen.getByText('FHA fixed')).toBeInTheDocument();
  });

  it('enables detail and lock navigation for an unblocked selected offer', () => {
    const onNavigate = vi.fn();
    render(<QuoteOffersScreen comparison={deterministicOfferComparison} onNavigate={onNavigate} />);

    fireEvent.click(screen.getByLabelText('Select offer Conventional 30 year fixed'));
    fireEvent.click(screen.getByRole('button', { name: /Inspect full explanation/i }));
    fireEvent.click(screen.getByRole('button', { name: /Start lock request/i }));

    expect(onNavigate).toHaveBeenCalledWith('/quote/run-preview-001/offers/offer-a');
    expect(onNavigate).toHaveBeenCalledWith('/quote/run-preview-001/lock');
  });

  it('hides internal source and ranking evidence on product cards', () => {
    render(<QuoteOffersScreen comparison={deterministicOfferComparison} />);

    fireEvent.click(screen.getByRole('button', { name: 'Cards' }));

    expect(screen.getByRole('list', { name: /Offer cards/i })).toBeInTheDocument();
    expect(screen.queryByText(/LoanHouse capture/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/quickpricer get generic quote summary/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/confidence/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/Rank score/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/LoanPass|source url|source_url|Backend rank/i)).not.toBeInTheDocument();
    expect(screen.getAllByText(/99\.934/).length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('30').length).toBeGreaterThanOrEqual(1);
  });

  it('caps multi-select comparison at four offers', () => {
    expect(toggleCompareOffer(['a', 'b', 'c', 'd'], 'e')).toEqual(['a', 'b', 'c', 'd']);
    expect(toggleCompareOffer(['a', 'b'], 'b')).toEqual(['a']);
  });
});
