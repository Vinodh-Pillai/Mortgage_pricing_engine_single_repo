import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { FairLendingDashboard, fairLendingDashboardEvidence } from './FairLendingDashboard';
import { fairLendingScreenModule } from './index';
import type { FairLendingReport } from '../../lib/api/fairLending';

afterEach(() => cleanup());

const report: FairLendingReport = {
  reportId: 'report-1',
  tenantId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
  startDate: '2026-06-01',
  endDate: '2026-06-30',
  sampleSize: 120,
  regressionResults: [{ outcome: 'NOTE_RATE', protectedClass: 'RACE', coefficients: { BLACK: 0.125 }, pValues: { BLACK: 0.023 }, confidenceIntervals: { BLACK: '[0.010000, 0.240000]' }, rSquared: 0.73, sampleSize: 120, significantDisparity: true, warnings: [] }],
  airTables: [{ outcome: 'NOTE_RATE', protectedClass: 'RACE', airRatios: { WHITE: 1, BLACK: 0.79 }, favorableCounts: { WHITE: 50, BLACK: 20 }, totalCounts: { WHITE: 60, BLACK: 60 }, referenceGroup: 'WHITE', fourFifthsViolation: true, dataQualityFlags: [] }],
  violations: [{ outcome: 'NOTE_RATE', protectedClass: 'RACE', group: 'BLACK', violationType: 'AIR_FOUR_FIFTHS', value: 0.79, threshold: 0.8, severity: 'CRITICAL', recommendedAction: 'Review pricing controls for BLACK group.' }],
  recommendations: ['Review fair-lending policy.'],
  dataQualityFlags: [],
  createdAt: '2026-06-15T00:00:00Z',
};

describe('PII-31-S01 fair lending dashboard', () => {
  it('defines compliance fair lending route metadata', () => {
    expect(fairLendingScreenModule.routePattern).toBe('/compliance/fair-lending');
    expect(fairLendingScreenModule.evidenceTarget).toBe('.local-harness/evidence/PII-31-S01/fair-lending-dashboard.json');
    expect(fairLendingScreenModule.match('/compliance/fair-lending')).toBe(true);
  });

  it('renders AIR, p-value, and violation drill-down modal', () => {
    render(<FairLendingDashboard initialReport={report} />);

    const table = screen.getByRole('table', { name: /Fair lending analysis results/i });
    expect(within(table).getByText('Note Rate')).toBeInTheDocument();
    expect(within(table).getByText('0.79')).toBeInTheDocument();
    expect(within(table).getByText('0.023')).toBeInTheDocument();
    expect(within(table).getByText('VIOLATION')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Drill-down' }));
    expect(screen.getByRole('dialog', { name: /Violation Detail: Note Rate ~ Race/i })).toHaveTextContent('Regression coefficients');
    expect(screen.getByRole('dialog')).toHaveTextContent('AIR=0.79');
  });

  it('runs analysis through the fair lending API adapter', async () => {
    const fetchImpl = vi.fn(async () => ({ status: 200, json: async () => report })) as unknown as typeof fetch;
    render(<FairLendingDashboard initialViolations={[]} fetchImpl={fetchImpl} />);

    fireEvent.click(screen.getByRole('button', { name: 'Run Analysis' }));
    expect(await screen.findByText('0.79')).toBeInTheDocument();
    expect(fetchImpl).toHaveBeenCalledWith('/api/v1/fair-lending/analyze', expect.objectContaining({ method: 'POST' }));
  });

  it('creates compact dashboard evidence metadata', () => {
    expect(fairLendingDashboardEvidence(report)).toEqual(expect.objectContaining({ route: '/compliance/fair-lending', violationCount: 1 }));
  });
});
