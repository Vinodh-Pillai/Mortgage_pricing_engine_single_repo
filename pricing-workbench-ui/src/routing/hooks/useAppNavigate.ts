import { useCallback } from 'react';
import { type NavigateOptions, type To, useNavigate } from 'react-router-dom';
import { buildRoutePath, findRouteDefinition, type RouteParams } from '../routes';

export function useAppNavigate() {
  const navigate = useNavigate();

  return useCallback((to: To | { routeId: string; params?: RouteParams }, options?: NavigateOptions) => {
    if (typeof to === 'object' && 'routeId' in to) {
      const route = findRouteDefinition(to.routeId);
      if (!route) throw new Error(`Unknown application route: ${to.routeId}`);
      navigate(buildRoutePath(route.path, to.params), options);
      return;
    }

    navigate(to, options);
  }, [navigate]);
}
