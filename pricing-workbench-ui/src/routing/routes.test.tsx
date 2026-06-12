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
      '/quote/:runId/what-if',
    ]));
    expect(matchAppRoute('/quote/run-123/offers').id).toBe('quote-offers');
    expect(matchAppRoute('/quote/run-123/offers/option-7').id).toBe('quote-detail');
    expect(matchAppRoute('/quote/run-123/what-if/fico-sensitivity').id).toBe('scenario-fico-sensitivity');
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
