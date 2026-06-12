import { useEffect, type ReactElement } from 'react';
import { AuthProvider, useAuth } from '../lib/auth/AuthContext';
import { PersonaSelector } from './PersonaSelector';

export default {
  title: 'Auth/Persona Selector',
};

function PersonaStorySession({ personaId = 'persona-pricing-analyst', children }: { personaId?: string; children: ReactElement }) {
  const { login } = useAuth();
  useEffect(() => {
    login(personaId);
  }, [login, personaId]);
  return children;
}

export function PricingAnalystSelector() {
  return (
    <AuthProvider>
      <PersonaStorySession>
        <PersonaSelector />
      </PersonaStorySession>
    </AuthProvider>
  );
}

export function CompactAdminSelector() {
  return (
    <AuthProvider>
      <PersonaStorySession personaId="persona-admin">
        <PersonaSelector compact />
      </PersonaStorySession>
    </AuthProvider>
  );
}
