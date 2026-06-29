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
    expect(screen.getByRole('button', { name: /Rate Sheet Review/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Pricing Analysis/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Product Catalog Review/ })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Tenant Settings/ })).not.toBeInTheDocument();
    expect(screen.getByText(/Configured tenant\/product mappings are unavailable/)).toBeInTheDocument();
    expect(screen.getByText(/Product mappings are visible as setup blockers/)).toBeInTheDocument();
    expect(screen.queryByText(/Product catalog changes require audit evidence/)).not.toBeInTheDocument();
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

    expect(screen.getByRole('button', { name: /Quote Workspace/ })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Tenant Settings/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /User Access/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Product Catalog Review/ })).not.toBeInTheDocument();
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
    expect(screen.getByText('No active pipeline items need attention.')).toBeInTheDocument();
    expect(screen.getByLabelText('Home screen state')).not.toHaveTextContent(/Recent\s*0/i);
    expect(screen.getByRole('heading', { name: 'My Pipeline' }).closest('section')).not.toHaveTextContent(/Active\s*0|Pending lock\s*0|Expiring soon\s*0/i);
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

  it('HomeScreenTest.hidesRestrictedActionsWhenRoleMetadataIsUnavailable', () => {
    render(<HomeScreen role="not-a-role" userId="unknown-user" onNavigate={vi.fn()} />, { wrapper: MemoryRouter });

    expect(screen.getByRole('alert', { name: 'Role metadata unavailable' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Tenant Settings/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Rate Sheet Review/ })).not.toBeInTheDocument();
  });

  it('HomeScreenTest.rendersOperationsAndAdminPersonaActionsFromConfiguredScopes', () => {
    const onNavigate = vi.fn();
    const { rerender } = render(<HomeScreen role="operations-lead" userId="ops-user" onNavigate={onNavigate} />, { wrapper: MemoryRouter });

    expect(screen.getByRole('button', { name: /Operational Remediation/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Rate Sheet Review/ })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Async quote callbacks and ratesheet jobs/ })).toBeInTheDocument();
    expect(screen.getByText('Async quote callback queue')).toBeInTheDocument();
    expect(screen.getByText('Ratesheet import job')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Review callback exceptions/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Retry ratesheet job/ })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /User Access/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: /Tenant, product, and access changes are audited/ })).not.toBeInTheDocument();

    rerender(<HomeScreen role="admin" userId="admin-user" onNavigate={onNavigate} />);

    expect(screen.getByRole('button', { name: /Tenant Settings/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /User Access/ })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Tenant, product, and access changes are audited/ })).toBeInTheDocument();
    expect(screen.getByText(/Administrative tenant settings, product catalog setup, and user access changes/)).toBeInTheDocument();
    expect(screen.getByText(/Product catalog changes require audit evidence/)).toBeInTheDocument();
    expect(screen.getByText(/Tenant management changes must be traceable in audit history/)).toBeInTheDocument();
    expect(screen.getByText(/User access updates require audit evidence/)).toBeInTheDocument();
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
