import { AuthProvider } from '../lib/auth/AuthContext';
import { PersonaSelector } from './PersonaSelector';

export default {
  title: 'Auth/Persona Selector',
};

export function PricingAnalystSelector() {
  return (
    <AuthProvider>
      <PersonaSelector />
    </AuthProvider>
  );
}

export function CompactAdminSelector() {
  return (
    <AuthProvider>
      <PersonaSelector compact />
    </AuthProvider>
  );
}
