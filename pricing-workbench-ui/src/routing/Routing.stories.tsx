import { MemoryRouter } from 'react-router-dom';
import { appRouteDefinitions, matchAppRoute, quoteNestedRoutePaths } from './routes';
import { useCurrentRoute } from './hooks';

export default {
  title: 'Routing/React Router Infrastructure',
};

function CurrentRouteCard() {
  const current = useCurrentRoute();
  return (
    <section className="panel" aria-labelledby="routing-story-heading">
      <h2 id="routing-story-heading">Current route</h2>
      <dl className="status-grid">
        <dt>Route id</dt><dd>{current.route.id}</dd>
        <dt>Module</dt><dd>{current.module.label}</dd>
        <dt>Path</dt><dd>{current.location.pathname}</dd>
      </dl>
    </section>
  );
}

export function RouteConfiguration() {
  return (
    <section className="panel" aria-labelledby="route-config-story-heading">
      <h2 id="route-config-story-heading">Workbench route configuration</h2>
      <p>{appRouteDefinitions.length} routes are configured from the workbench module registry and routing-only entries.</p>
      <ul>
        {appRouteDefinitions.map((route) => <li key={route.id}><code>{route.path}</code> — {route.label}</li>)}
      </ul>
    </section>
  );
}

export function NestedQuoteRoutes() {
  return (
    <section className="panel" aria-labelledby="nested-routes-story-heading">
      <h2 id="nested-routes-story-heading">Nested quote routes</h2>
      <ul>{quoteNestedRoutePaths.map((path) => <li key={path}><code>{path}</code></li>)}</ul>
      <p>Example match: {matchAppRoute('/quote/run-story/offers/offer-story').id}</p>
    </section>
  );
}

export function CurrentRouteHook() {
  return (
    <MemoryRouter initialEntries={['/quote/run-story/what-if/fico-sensitivity']}>
      <CurrentRouteCard />
    </MemoryRouter>
  );
}
