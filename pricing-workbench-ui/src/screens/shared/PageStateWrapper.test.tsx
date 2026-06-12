import '@testing-library/jest-dom/vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { PageStateWrapper } from './PageStateWrapper';

afterEach(() => cleanup());

describe('PageStateWrapperTest', () => {
  it('PageStateWrapperTest.showsLoadingState', () => {
    render(<PageStateWrapper state="loading" title="Tenant Onboarding"><p>ready content</p></PageStateWrapper>);
    expect(screen.getByRole('status')).toHaveTextContent(/Loading screen data/i);
  });

  it('PageStateWrapperTest.showsEmptyState', () => {
    render(<PageStateWrapper state="empty" title="Tenant Onboarding" emptyMessage="No draft"><p>ready content</p></PageStateWrapper>);
    expect(screen.getByRole('heading', { name: /No tenant onboarding records yet/i })).toBeInTheDocument();
    expect(screen.getByText('No draft')).toBeInTheDocument();
  });

  it('PageStateWrapperTest.showsBlockedState', () => {
    render(<PageStateWrapper state="blocked" title="Tenant Onboarding"><p>ready content</p></PageStateWrapper>);
    expect(screen.getByRole('alert')).toHaveTextContent(/blocked/i);
  });
});
