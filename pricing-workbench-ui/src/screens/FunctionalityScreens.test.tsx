import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { tenantOnboardingScreenModule, TenantOnboardingScreen } from './tenant';
import { productManagementScreenModule, ProductManagementScreen } from './product';
import { rateSheetIntakeScreenModule, RateSheetIntakeScreen } from './ratesheet';
import { pricingAnalysisScreenModule, PricingAnalysisScreen } from './pricing';
import { lockManagementScreenModule, LockManagementScreen } from './locks';
import { buildNavigationTree, navigationGroups } from '../layout/navigation';
import { workbenchModules } from './workbenchShell/WorkbenchShell';
import { matchAppRoute } from '../routing/routes';

afterEach(() => cleanup());

describe('PII-25-S04 functionality page modules', () => {
  it('registers ScreenModule contracts and routes for all five pages', () => {
    expect(tenantOnboardingScreenModule.evidenceTarget).toBe('.local-harness/evidence/PII-25-S04/tenant-onboarding.json');
    expect(productManagementScreenModule.stateCoverage).toEqual(expect.arrayContaining(['loading', 'empty', 'blocked', 'needs-attention', 'ready']));
    expect(rateSheetIntakeScreenModule.match('/pricing/rate-sheets/new')).toBe(true);
    expect(pricingAnalysisScreenModule.match('/pricing/analysis/run-001')).toBe(true);
    expect(lockManagementScreenModule.match('/locks/lock-001')).toBe(true);
    expect(matchAppRoute('/pricing/rate-sheets/new').sourceModuleId).toBe('rate-sheet-intake');
    expect(matchAppRoute('/locks/lock-001').sourceModuleId).toBe('lock-management');
  });

  it('adds navigation groups and persona-visible entries', () => {
    const items = buildNavigationTree(workbenchModules, 'run-test', 'pricing-analyst');
    expect(items.map((item) => item.id)).toEqual(expect.arrayContaining(['product-management', 'rate-sheet-intake', 'pricing-analysis']));
    expect(navigationGroups(items)).toEqual(expect.arrayContaining(['Products', 'Pricing']));
  });
});

describe('PII-25-S04 page behavior', () => {
  it('TenantOnboardingTest.rendersAllSections', async () => {
    const onEvidenceCapture = vi.fn();
    render(<TenantOnboardingScreen onEvidenceCapture={onEvidenceCapture} />);
    expect(screen.getByRole('heading', { name: 'Tenant Onboarding' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Workspace Setup' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Identity Configuration' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Launch Checklist' })).toBeInTheDocument();
    await waitFor(() => expect(onEvidenceCapture).toHaveBeenCalledWith(expect.objectContaining({ screenId: 'tenant-onboarding', state: 'ready' })));
  });

  it('ProductManagementTest.tableSortsFilters', () => {
    render(<ProductManagementScreen />);
    const table = screen.getByRole('table', { name: /Product catalog records/i });
    expect(within(table).getByText('Purchase product draft')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText(/Filter records/i), { target: { value: 'Refinance' } });
    expect(within(table).getByText('Refinance product draft')).toBeInTheDocument();
    expect(within(table).queryByText('Purchase product draft')).not.toBeInTheDocument();
  });

  it('RateSheetIntakeTest.handlesFileUpload', () => {
    const onEvidenceCapture = vi.fn();
    render(<RateSheetIntakeScreen onEvidenceCapture={onEvidenceCapture} />);
    const file = new File(['preview'], 'ratesheet.csv', { type: 'text/csv' });
    fireEvent.change(screen.getByLabelText(/Rate sheet file/i), { target: { files: [file] } });
    expect(screen.getByText(/ratesheet.csv uploaded 100%/i)).toBeInTheDocument();
    expect(onEvidenceCapture).toHaveBeenCalledWith(expect.objectContaining({ action: 'rate-sheet-file-selected' }));
  });

  it('PricingAnalysisTest.rendersWaterfall', () => {
    render(<PricingAnalysisScreen />);
    expect(screen.getByRole('heading', { name: 'Pricing Analysis' })).toBeInTheDocument();
    expect(screen.getByLabelText(/Read-only waterfall chart/i)).toHaveTextContent(/Base selection/i);
    expect(screen.getByText(/No local pricing calculations/i)).toBeInTheDocument();
  });

  it('LockManagementTest.showsStatusBadges', () => {
    render(<LockManagementScreen />);
    expect(screen.getByRole('heading', { name: 'Lock Management' })).toBeInTheDocument();
    for (const status of ['requested', 'confirmed', 'expired', 'cancelled', 'delivered']) {
      expect(screen.getByText(status)).toBeInTheDocument();
    }
  });
});
