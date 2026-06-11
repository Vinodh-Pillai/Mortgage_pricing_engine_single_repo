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
    expect(screen.getByRole('table', { name: /Ranked quote offers/i })).toBeInTheDocument();
    expect(screen.getByText('Conventional 30 year fixed')).toBeInTheDocument();
    expect(screen.getByText('PRICE_UNAVAILABLE')).toBeInTheDocument();

    fireEvent.click(screen.getAllByLabelText(/Select/i)[1]);
    expect(screen.getAllByRole('alert').some((alert) => /Commit blocked/i.test(alert.textContent ?? ''))).toBe(true);
    expect(screen.getByText('pricing-service quote price')).toBeInTheDocument();
  });

  it('enables detail and lock navigation for an unblocked selected offer', () => {
    const onNavigate = vi.fn();
    render(<QuoteOffersScreen comparison={deterministicOfferComparison} onNavigate={onNavigate} />);

    fireEvent.click(screen.getAllByLabelText(/Select/i)[0]);
    fireEvent.click(screen.getByRole('button', { name: /Compare Detail/i }));
    fireEvent.click(screen.getByRole('button', { name: /Lock Terms/i }));

    expect(onNavigate).toHaveBeenCalledWith('/quote/run-preview-001/offers/offer-a');
    expect(onNavigate).toHaveBeenCalledWith('/quote/run-preview-001/lock');
  });

  it('caps multi-select comparison at four offers', () => {
    expect(toggleCompareOffer(['a', 'b', 'c', 'd'], 'e')).toEqual(['a', 'b', 'c', 'd']);
    expect(toggleCompareOffer(['a', 'b'], 'b')).toEqual(['a']);
  });
});
