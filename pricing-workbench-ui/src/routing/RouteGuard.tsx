import type { ReactElement } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../lib/auth/AuthContext';
import type { Permission } from '../lib/auth/personas';

export interface RouteGuardProps {
  children: ReactElement;
  route?: string;
  requiredPermission?: Permission;
  publicRoutes?: string[];
  accessDeniedComponent?: ReactElement;
}

export function AccessDenied({ route }: { route: string }) {
  return (
    <section className="panel" role="alert" aria-labelledby="access-denied-heading">
      <p className="eyebrow">Access control</p>
      <h2 id="access-denied-heading">Access denied</h2>
      <p>Your authenticated account does not have permission to view this workbench area.</p>
      <dl className="status-grid">
        <dt>Requested route</dt>
        <dd><code>{route}</code></dd>
        <dt>Next step</dt>
        <dd>Use an account with the required backend role or return to the pipeline.</dd>
      </dl>
    </section>
  );
}

export function RouteGuard({ children, route, requiredPermission, publicRoutes = ['/login'], accessDeniedComponent }: RouteGuardProps) {
  const location = useLocation();
  const { isAuthenticated, isLoading, hasPermission, canAccessRoute } = useAuth();
  const targetRoute = route ?? `${location.pathname}${location.search}${location.hash}`;
  const pathname = route ?? location.pathname;
  const isPublic = publicRoutes.some((publicRoute) => publicRoute === pathname);

  if (isPublic) return children;

  if (isLoading) {
    return <section className="panel"><p role="status">Checking authentication…</p></section>;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  const allowed = requiredPermission ? hasPermission(requiredPermission) : canAccessRoute(targetRoute);
  if (!allowed) {
    return accessDeniedComponent ?? <AccessDenied route={targetRoute} />;
  }

  return children;
}
