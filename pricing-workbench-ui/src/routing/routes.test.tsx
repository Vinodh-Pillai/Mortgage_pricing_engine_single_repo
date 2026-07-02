import '@testing-library/jest-dom/vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it } from 'vitest';
import { workbenchModules } from '../screens/workbenchShell/WorkbenchShell';
import { appRouteDefinitions, buildRoutePath, matchAppRoute, quoteNestedRoutePaths, routeComponentLoaders } from './routes';
import { useAppNavigate, useCurrentRoute, useRouteParams } from './hooks';

afterEach(() => cleanup());

describe('RoutingTest', () => {
  it('RoutingTest.matchesAllWorkbenchModules', () => {
    for (const module of workbenchModules) {
      expect(appRouteDefinitions).toEqual(expect.arrayContaining([
        expect.objectContaining({ sourceModuleId: module.id, path: module.routePattern }),
      ]));
    }
  });

  it('RoutingTest.lazyLoadsScreenComponents', () => {
    for (const route of appRouteDefinitions) {
      expect(route.lazyComponent).toBeTruthy();
      expect(routeComponentLoaders[route.id] ?? routeComponentLoaders[route.sourceModuleId]).toEqual(expect.any(Function));
    }
  });

  it('RoutingTest.nestedQuoteRoutesWork', () => {
    expect(quoteNestedRoutePaths).toEqual(expect.arrayContaining([
      '/quote/:runId/offers',
      '/quote/:runId/offers/:optionId',
      '/quote/:runId/pricing-waterfall',
      '/quote/:runId/journey',
      '/quote/:runId/what-if',
    ]));
    expect(matchAppRoute('/quote/run-123/offers').id).toBe('quote-offers');
    expect(matchAppRoute('/quote/run-123/offers/option-7').id).toBe('quote-detail');
    expect(matchAppRoute('/quote/run-123/pricing-waterfall').id).toBe('pricing-waterfall');
    expect(matchAppRoute('/quote/run-123/journey').id).toBe('quote-journey');
    expect(matchAppRoute('/quote/run-123/what-if/fico-sensitivity').id).toBe('scenario-fico-sensitivity');
  });

  it('RoutingTest.exposesRunSelectionRoutesAndKeepsPreviewsOnExplicitPreviewPaths', () => {
    expect(matchAppRoute('/pricing/waterfall')).toEqual(expect.objectContaining({ id: 'pricing-waterfall-select-run', sourceModuleId: 'pricing-waterfall' }));
    expect(matchAppRoute('/journey-map')).toEqual(expect.objectContaining({ id: 'quote-journey-select-run', sourceModuleId: 'quote-journey' }));
    expect(matchAppRoute('/pricing/waterfall/preview')).toEqual(expect.objectContaining({ id: 'pricing-waterfall-preview', sourceModuleId: 'pricing-waterfall' }));
    expect(matchAppRoute('/journey-map/preview')).toEqual(expect.objectContaining({ id: 'quote-journey-preview', sourceModuleId: 'quote-journey' }));
    expect(matchAppRoute('/lock-management')).toEqual(expect.objectContaining({ id: 'lock-management-alias', sourceModuleId: 'lock-management' }));
  });

  it('RoutingTest.exposesPostDeployRequiredRouteAliases', () => {
    expect(matchAppRoute('/scenario-analysis')).toEqual(expect.objectContaining({ id: 'scenario-analysis-review-alias', sourceModuleId: 'quote-offers' }));
    expect(matchAppRoute('/rate-sheet-intake')).toEqual(expect.objectContaining({ id: 'rate-sheet-intake-direct-alias', sourceModuleId: 'rate-sheet-intake' }));
    expect(matchAppRoute('/rate-feed-pipeline')).toEqual(expect.objectContaining({ id: 'rate-feed-pipeline-direct-alias', sourceModuleId: 'rate-feed-pipeline' }));
    expect(matchAppRoute('/tenant-admin')).toEqual(expect.objectContaining({ id: 'tenant-admin-direct-alias', sourceModuleId: 'tenant-admin' }));
    expect(matchAppRoute('/governance')).toEqual(expect.objectContaining({ id: 'governance-lifecycle-direct-alias', sourceModuleId: 'admin-governance' }));
    expect(matchAppRoute('/margin-profitability')).toEqual(expect.objectContaining({ id: 'margin-profitability-direct-alias', sourceModuleId: 'margin-profitability' }));
    expect(matchAppRoute('/quickquote')).toEqual(expect.objectContaining({ id: 'quickquote-direct-alias', sourceModuleId: 'quickquote' }));
    expect(matchAppRoute('/partner-integrations')).toEqual(expect.objectContaining({ id: 'partner-integrations-direct-alias', sourceModuleId: 'partner-transport' }));
  });

  it('RoutingTest.handles404ForUnknownRoutes', () => {
    expect(matchAppRoute('/unknown/workbench/path').id).toBe('not-found');
  });
});

describe('NavigationHooksTest', () => {
  it('NavigationHooksTest.useAppNavigateTypesCorrectly', () => {
    function Harness() {
      const navigate = useAppNavigate();
      return <button type="button" onClick={() => navigate({ routeId: 'quote-detail', params: { runId: 'run-1', optionId: 'offer-2' } })}>Go detail</button>;
    }

    render(<MemoryRouter><Harness /></MemoryRouter>);
    expect(screen.getByRole('button', { name: 'Go detail' })).toBeInTheDocument();
    expect(buildRoutePath('/quote/:runId/offers/:optionId', { runId: 'run-1', optionId: 'offer-2' })).toBe('/quote/run-1/offers/offer-2');
  });

  it('NavigationHooksTest.useRouteParamsReturnsTypedParams', () => {
    function Harness() {
      const params = useRouteParams();
      const current = useCurrentRoute();
      return <div>{params.runId}:{current.route.id}</div>;
    }

    render(
      <MemoryRouter initialEntries={['/quote/run-123/offers']}>
        <Routes>
          <Route path="/quote/:runId/offers" element={<Harness />} />
        </Routes>
      </MemoryRouter>,
    );
    expect(screen.getByText('run-123:quote-offers')).toBeInTheDocument();
  });
});
