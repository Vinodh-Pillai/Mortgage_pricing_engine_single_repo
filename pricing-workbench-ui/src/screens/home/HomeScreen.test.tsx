import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { recordActivity, type ActivityRecord } from '../../lib/activity/activity';
import { HomeScreen } from './HomeScreen';
import { hasPermission, quickActions } from './QuickActions';
import { homeScreenModule } from './index';

afterEach(() => {
  cleanup();
  window.localStorage.clear();
});

const activity: ActivityRecord[] = [
  {
    id: 'pipeline:run-1:2026-06-10T14:30:00Z',
    userId: 'user-1',
    action: 'quote_launched',
    entityType: 'pipeline',
    entityId: 'run-1',
    borrowerName: 'Johnson',
    propertyAddress: '456 Oak Ave, Springfield, IL',
    status: 'ACTIVE',
    lastAction: 'Quote launched',
    timestamp: '2026-06-10T14:30:00Z',
    route: '/quote/run-1/offers',
  },
];

describe('HomeScreenTest', () => {
  it('HomeScreenTest.showsQuickActionsForRole', () => {
    render(<HomeScreen role="pricing_analyst" userId="user-1" initialActivity={activity} onNavigate={vi.fn()} />, { wrapper: MemoryRouter });

    expect(screen.getByRole('heading', { name: "Today's work" })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Rate Sheet Upload/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Pricing Analysis/ })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Tenant Settings/ })).not.toBeInTheDocument();
  });

  it('HomeScreenTest.filtersQuickActionsByPermission', () => {
    const rateSheet = quickActions.find((action) => action.id === 'rate-sheet-upload')!;
    const pipeline = quickActions.find((action) => action.id === 'new-pipeline')!;

    expect(hasPermission('pricing analyst', rateSheet)).toBe(true);
    expect(hasPermission('loan_officer', rateSheet)).toBe(false);
    expect(hasPermission('loan-officer', pipeline)).toBe(true);
  });

  it('HomeScreenTest.showsRecentActivity', () => {
    const onNavigate = vi.fn();
    render(<HomeScreen role="loan_officer" userId="user-1" initialActivity={activity} onNavigate={onNavigate} />, { wrapper: MemoryRouter });

    fireEvent.click(screen.getByRole('button', { name: 'Johnson' }));

    expect(screen.getByText('456 Oak Ave, Springfield, IL')).toBeInTheDocument();
    expect(screen.getByText('Quote launched')).toBeInTheDocument();
    expect(onNavigate).toHaveBeenCalledWith('/quote/run-1/offers');
  });

  it('HomeScreenTest.emptyStateForNewUser', () => {
    const onNavigate = vi.fn();
    render(<HomeScreen role="borrower" userId="empty-user" onNavigate={onNavigate} />, { wrapper: MemoryRouter });

    fireEvent.click(screen.getByRole('button', { name: 'Start Pipeline' }));

    expect(screen.getByText('No recent activity. Start a new pipeline.')).toBeInTheDocument();
    expect(onNavigate).toHaveBeenCalledWith('/pipeline');
  });

  it('QuickActionsTest.keyboardAccessible', () => {
    const onNavigate = vi.fn();
    render(<HomeScreen role="admin" userId="admin-user" onNavigate={onNavigate} />, { wrapper: MemoryRouter });

    const catalog = screen.getByRole('button', { name: /Product Catalog/ });
    catalog.focus();
    fireEvent.click(catalog);

    expect(catalog).toHaveFocus();
    expect(onNavigate).toHaveBeenCalledWith('/admin/products/catalog');
  });

  it('registers the home screen module contract', () => {
    expect(homeScreenModule.id).toBe('home');
    expect(homeScreenModule.routePattern).toBe('/home');
    expect(homeScreenModule.evidenceTarget).toBe('.local-harness/evidence/PII-26-S02/home-screen.json');
    expect(homeScreenModule.match('/home')).toBe(true);
    expect(homeScreenModule.stateCoverage).toEqual(expect.arrayContaining(['load-state', 'empty', 'ready']));
  });

  it('shows newly recorded localStorage activity', () => {
    recordActivity('stored-user', 'pipeline_viewed', 'pipeline', 'run-2', {
      borrowerName: 'Williams',
      propertyAddress: '321 Pine Rd',
      timestamp: '2026-06-10T15:30:00Z',
    });

    render(<HomeScreen role="loan_officer" userId="stored-user" onNavigate={vi.fn()} />, { wrapper: MemoryRouter });

    expect(screen.getByRole('button', { name: 'Williams' })).toBeInTheDocument();
  });
});
