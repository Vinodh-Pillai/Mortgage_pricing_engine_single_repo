import { useEffect, type ReactElement } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider, useAuth } from '../lib/auth/AuthContext';
import { AccessDenied, RouteGuard } from './RouteGuard';

export default {
  title: 'Auth/Route Guard',
};

function PersonaSession({ personaId, children }: { personaId?: string; children: ReactElement }) {
  const { login, logout } = useAuth();
  useEffect(() => {
    if (personaId) login(personaId);
    else logout();
  }, [login, logout, personaId]);
  return children;
}

export function AllowedState() {
  return (
    <AuthProvider>
      <MemoryRouter initialEntries={['/ops/dashboard']}>
        <PersonaSession personaId="persona-operations-lead">
          <RouteGuard>
            <section className="panel"><h2>Operations dashboard allowed</h2><p>The operations lead persona can read operations routes.</p></section>
          </RouteGuard>
        </PersonaSession>
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
        <PersonaSession>
          <RouteGuard>
            <section className="panel"><h2>Protected operations route</h2></section>
          </RouteGuard>
        </PersonaSession>
      </MemoryRouter>
    </AuthProvider>
  );
}
