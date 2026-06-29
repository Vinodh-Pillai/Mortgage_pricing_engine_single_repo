import '@testing-library/jest-dom/vitest';
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import QuoteJourneyMapScreen from './QuoteJourneyMapScreen';

describe('QuoteJourneyMapScreen', () => {
  it('labels the direct journey map as preview evidence without chip-style summaries', () => {
    render(<QuoteJourneyMapScreen />);

    expect(screen.getByRole('heading', { name: /Quote Journey Map preview/i })).toBeInTheDocument();
    expect(screen.getByText(/Preview evidence page · non-production/i)).toBeInTheDocument();
    expect(screen.getByText(/does not calculate prices or claim production service connectivity/i)).toBeInTheDocument();
    expect(screen.getByRole('list')).toBeInTheDocument();
  });
});
