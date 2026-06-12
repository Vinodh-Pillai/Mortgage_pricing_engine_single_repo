import { matchPath, useLocation } from 'react-router-dom';
import { matchAppRoute, moduleForRoute } from '../routes';

export function useCurrentRoute() {
  const location = useLocation();
  const route = matchAppRoute(location.pathname);
  const match = route.path === '*' ? null : matchPath({ path: route.path, end: true }, location.pathname);

  return {
    location,
    route,
    module: moduleForRoute(route),
    params: match?.params ?? {},
    isNotFound: route.id === 'not-found',
  };
}
