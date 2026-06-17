import '@testing-library/jest-dom/vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { MappingWizardScreen } from './MappingWizardScreen';

describe('MappingWizardScreen', () => {
  it('shows review grid, normalized preview, and profile governance save path', () => {
    render(<MappingWizardScreen />);

    expect(screen.getByRole('heading', { name: 'Rate Sheet Mapping Wizard' })).toBeInTheDocument();
    expect(screen.getByRole('table', { name: 'Proposed field mappings' })).toBeInTheDocument();
    expect(screen.getAllByText(/note_rate/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/base_price/).length).toBeGreaterThan(0);
    expect(screen.getByText(/"note_rate"/)).toBeInTheDocument();
    expect(screen.queryByText(/"adjustment_value"/)).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Heuristic mode' }));
    expect(screen.getByText(/without external calls/)).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Profile name'), { target: { value: 'Onslow sample' } });
    fireEvent.change(screen.getByLabelText('Investor code'), { target: { value: 'ONSLOW' } });
    fireEvent.change(screen.getByLabelText('Product code'), { target: { value: 'DSCR' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save as Profile' }));

    expect(screen.getByText(/governance remains DRAFT/)).toBeInTheDocument();
  });
});
