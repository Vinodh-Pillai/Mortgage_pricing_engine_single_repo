import { useEffect } from 'react';
import { AuthProvider, useAuth } from './AuthContext';

export default {
  title: 'Auth/Auth Context',
};

function AuthContextPanel({ personaId }: { personaId: string }) {
  const { currentPersona, login, hasPermission, canAccessRoute, availablePersonas } = useAuth();
  useEffect(() => {
    login(personaId);
  }, [login, personaId]);

  return (
    <section className="panel" aria-labelledby="auth-context-story-heading">
      <h2 id="auth-context-story-heading">Auth context provider</h2>
      <p>Available synthetic personas: {availablePersonas.length}</p>
      <dl className="status-grid">
        <dt>Current persona</dt><dd>{currentPersona?.name ?? 'none'}</dd>
        <dt>Role</dt><dd>{currentPersona?.role ?? 'none'}</dd>
        <dt>Can read operations</dt><dd>{String(canAccessRoute('/ops/dashboard'))}</dd>
        <dt>Can manage pricing</dt><dd>{String(hasPermission('pricing:waterfall'))}</dd>
      </dl>
    </section>
  );
}

export function PricingAnalystContext() {
  return <AuthProvider><AuthContextPanel personaId="persona-pricing-analyst" /></AuthProvider>;
}

export function OperationsLeadContext() {
  return <AuthProvider><AuthContextPanel personaId="persona-operations-lead" /></AuthProvider>;
}
