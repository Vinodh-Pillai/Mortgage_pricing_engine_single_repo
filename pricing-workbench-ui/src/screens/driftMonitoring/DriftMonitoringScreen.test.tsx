import '@testing-library/jest-dom/vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { driftMonitoringScreenModule, DriftMonitoringScreen } from './index';
import { blockedDriftMonitoringView } from './fixtures';

describe('DriftMonitoringScreen', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        expect(init?.headers).toEqual(expect.objectContaining({ 'X-Ui-Trace-Id': 'drift-s27-local-trace' }));
        const url = input.toString();
        if (url.includes('/feature')) return { ok: true, status: 200, json: async () => blockedDriftMonitoringView.featureDrift };
        if (url.includes('/prediction')) return { ok: true, status: 200, json: async () => blockedDriftMonitoringView.predictionDrift };
        if (url.includes('/population')) return { ok: true, status: 200, json: async () => blockedDriftMonitoringView.populationStability };
        if (url.includes('/alerts')) return { ok: true, status: 200, json: async () => blockedDriftMonitoringView.alerts };
        if (url.includes('/investigation')) return { ok: true, status: 200, json: async () => blockedDriftMonitoringView.investigation };
        throw new Error(`Unexpected drift endpoint ${url}`);
      }),
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('registers the drift monitoring screen contract and route', () => {
    expect(driftMonitoringScreenModule.routePattern).toBe('/advisory/ml/drift');
    expect(driftMonitoringScreenModule.evidenceTarget).toBe('.local-harness/evidence/PII-24-S27/drift-monitoring.json');
    expect(driftMonitoringScreenModule.match('/advisory/ml/drift')).toBe(true);
    expect(driftMonitoringScreenModule.stateCoverage).toEqual(expect.arrayContaining(['load-state', 'blocked', 'ready']));
  });

  it('renders feature, prediction, population, alerts, and investigation tabs', async () => {
    render(<DriftMonitoringScreen />);
    expect(await screen.findByRole('heading', { name: 'Drift Monitoring' })).toBeInTheDocument();
    expect(screen.getByRole('table', { name: 'Feature drift metrics' })).toHaveTextContent('PSI');
    expect(screen.getByRole('table', { name: 'Feature drift metrics' })).toHaveTextContent('CRITICAL');

    fireEvent.click(screen.getByRole('tab', { name: 'Prediction Drift' }));
    expect(screen.getByRole('list', { name: 'Prediction drift cards' })).toHaveTextContent('Insufficient labels');
    expect(screen.getByRole('list', { name: 'Prediction drift cards' })).toHaveTextContent('Wasserstein distance');

    fireEvent.click(screen.getByRole('tab', { name: 'Population Stability' }));
    expect(screen.getByLabelText('PSI over time chart')).toHaveTextContent('threshold 0.1');
    expect(screen.getByRole('table', { name: 'Cohort drift drilldown' })).toHaveTextContent('region');

    fireEvent.click(screen.getByRole('tab', { name: 'Alerts' }));
    expect(screen.getByRole('table', { name: 'Drift alerts' })).toHaveTextContent('ACKNOWLEDGE');
    fireEvent.click(screen.getAllByRole('button', { name: 'View Alert Detail' })[0]);
    expect(screen.getByRole('dialog', { name: /Alert detail/ })).toHaveTextContent('> 0.2');

    fireEvent.click(screen.getByRole('tab', { name: 'Investigation' }));
    expect(screen.getByRole('list', { name: 'Investigation workspace' })).toHaveTextContent('Root Cause Analysis');
    expect(screen.getByRole('table', { name: 'Remediation plan' })).toHaveTextContent('Review population stability evidence');
  });

  it('supports feature drilldown detail modal', async () => {
    render(<DriftMonitoringScreen />);
    await screen.findByRole('heading', { name: 'Drift Monitoring' });

    const table = screen.getByRole('table', { name: 'Feature drift metrics' });
    fireEvent.click(within(table).getAllByRole('button', { name: 'Open detail' })[0]);
    expect(screen.getByRole('dialog', { name: /drift detail/ })).toHaveTextContent('Reference vs current histogram');
    expect(screen.getByRole('dialog', { name: /drift detail/ })).toHaveTextContent('KS p-value');
  });
});
