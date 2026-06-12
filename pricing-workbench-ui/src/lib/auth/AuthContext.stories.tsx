import { AuthProvider, useAuth } from './AuthContext';

export default {
  title: 'Auth/Auth Context',
};

function AuthContextPanel() {
  const { user, currentPersona, hasPermission, canAccessRoute } = useAuth();

  return (
    <section className="panel" aria-labelledby="auth-context-story-heading">
      <h2 id="auth-context-story-heading">Auth context provider</h2>
      <p>Auth state is loaded from the backend /api/auth/me endpoint.</p>
      <dl className="status-grid">
        <dt>Current user</dt><dd>{user?.fullName ?? 'none'}</dd>
        <dt>Role</dt><dd>{currentPersona?.role ?? 'none'}</dd>
        <dt>Can read operations</dt><dd>{String(canAccessRoute('/ops/dashboard'))}</dd>
        <dt>Can read pricing</dt><dd>{String(hasPermission('pricing:read'))}</dd>
      </dl>
    </section>
  );
}

export function PricingAnalystContext() {
  return <AuthProvider><AuthContextPanel /></AuthProvider>;
}

export function OperationsLeadContext() {
  return <AuthProvider><AuthContextPanel /></AuthProvider>;
}
