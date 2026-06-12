import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '../lib/auth/AuthContext';
import { AccessDenied, RouteGuard } from './RouteGuard';

export default {
  title: 'Auth/Route Guard',
};

export function AllowedState() {
  return (
    <AuthProvider>
      <MemoryRouter initialEntries={['/login']}>
        <RouteGuard>
          <section className="panel"><h2>Login route is public</h2><p>Authenticated routes are enabled after /api/auth/me returns a user.</p></section>
        </RouteGuard>
      </MemoryRouter>
    </AuthProvider>
  );
}

export function DeniedState() {
  return <AccessDenied route="/admin/governance" />;
}

export function RedirectState() {
  return (
    <AuthProvider>
      <MemoryRouter initialEntries={['/ops/dashboard']}>
        <RouteGuard>
          <section className="panel"><h2>Protected operations route</h2></section>
        </RouteGuard>
      </MemoryRouter>
    </AuthProvider>
  );
}
