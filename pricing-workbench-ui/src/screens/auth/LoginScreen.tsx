import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { useLocation } from 'react-router-dom';
import { Button, Input } from '../../design-system';
import { useAuth } from '../../lib/auth/AuthContext';
import { roleLabels, syntheticPersonas, type Persona, type PersonaRole } from '../../lib/auth/personas';
import { useTranslation } from '../../lib/i18n';
import { useAppNavigate } from '../../routing/hooks';
import type { ScreenProps } from '../contract/ScreenProps';
import './LoginScreen.css';

const roleOrder: PersonaRole[] = [
  'borrower',
  'loan-officer',
  'pricing-analyst',
  'operations-lead',
  'governance-reviewer',
  'admin',
  'partner-manager',
  'compliance-officer',
];

export type LoginEvidenceType = 'login-submit' | 'login-error' | 'route-redirect';

export interface LoginEvidenceDetail {
  type: LoginEvidenceType;
  personaId?: string;
  target?: string;
  value?: string;
}

export interface LoginScreenProps extends Partial<ScreenProps> {
  personas?: Persona[];
  initialEmail?: string;
  initialSearchTerm?: string;
  initialSelectedPersonaId?: string;
  loading?: boolean;
  disableAutoRedirect?: boolean;
}

export function normalizePersonaQuery(value: string) {
  return value.trim().toLowerCase().replace(/[\s_]+/g, '-');
}

export function filterPersonas(personas: Persona[], query: string) {
  const normalized = normalizePersonaQuery(query);
  if (!normalized) return personas;

  return personas.filter((persona) => {
    const searchable = [
      persona.name,
      persona.role,
      roleLabels[persona.role],
      persona.email,
      persona.description,
      persona.permissions.join(' '),
    ].join(' ').toLowerCase().replace(/[\s_]+/g, '-');

    return searchable.includes(normalized);
  });
}

export function groupPersonasByRole(personas: Persona[]) {
  return roleOrder
    .map((role) => ({ role, personas: personas.filter((persona) => persona.role === role) }))
    .filter((group) => group.personas.length > 0);
}

function emitLoginEvidence(detail: LoginEvidenceDetail) {
  if (typeof window === 'undefined' || typeof window.dispatchEvent !== 'function') return;
  window.dispatchEvent(new CustomEvent<LoginEvidenceDetail>('wcpe:login-screen', { detail }));
}

export function LoginScreen({
  initialEmail = '',
  initialSearchTerm,
  initialSelectedPersonaId,
  loading = false,
  disableAutoRedirect = false,
}: LoginScreenProps) {
  const { currentPersona, isAuthenticated, isLoading, login } = useAuth();
  const location = useLocation();
  const navigate = useAppNavigate();
  const { t } = useTranslation('auth');
  const initialPersonaEmail = syntheticPersonas.find((persona) => persona.id === initialSelectedPersonaId)?.email;
  const [email, setEmail] = useState(initialEmail || initialPersonaEmail || initialSearchTerm || '');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const demoUsers = useMemo(() => syntheticPersonas.map((persona) => persona.email).join(', '), []);

  useEffect(() => {
    if (!disableAutoRedirect && isAuthenticated && currentPersona && location.pathname === '/login') {
      emitLoginEvidence({ type: 'route-redirect', personaId: currentPersona.id, target: currentPersona.defaultRoute });
      navigate(currentPersona.defaultRoute, { replace: true });
    }
  }, [currentPersona, disableAutoRedirect, isAuthenticated, location.pathname, navigate]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const persona = await login(email, password);
      const from = (location.state as { from?: { pathname?: string } } | null)?.from?.pathname;
      const target = from ?? persona?.defaultRoute ?? '/pipeline';
      emitLoginEvidence({ type: 'login-submit', personaId: persona?.id, target });
      navigate(target, { replace: true });
    } catch (exception) {
      const message = exception instanceof Error ? exception.message : 'Unable to sign in';
      setError(message);
      emitLoginEvidence({ type: 'login-error', value: message });
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="login-page" aria-labelledby="login-title" data-testid="login-page">
      <section className="login-container" aria-describedby="login-tagline">
        <div className="login-hero">
          <div className="login-logo" aria-hidden="true">
            <span className="login-logo__mark">◇</span>
          </div>
          <p className="login-eyebrow">Secure workbench sign in</p>
          <h1 id="login-title">{t('pricingWorkbench')}</h1>
          <p id="login-tagline" className="login-tagline">{t('tagline')}</p>
          <div className="login-hero__signals" aria-label="Login screen highlights">
            <span>PostgreSQL users</span>
            <span>JWT session cookie</span>
            <span>Role-based access</span>
          </div>
        </div>

        <form className="login-search-panel login-form" onSubmit={handleSubmit}>
          <label htmlFor="login-email" className="login-search-label">Email</label>
          <div className="login-search-shell">
            <span aria-hidden="true">@</span>
            <Input
              id="login-email"
              type="email"
              placeholder="sarah.mitchell@wcpe.demo"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              className="login-search"
              aria-label="Email"
              autoComplete="email"
              required
            />
          </div>

          <label htmlFor="login-password" className="login-search-label">Password</label>
          <div className="login-search-shell">
            <span aria-hidden="true">••</span>
            <Input
              id="login-password"
              type="password"
              placeholder="Password123!"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              className="login-search"
              aria-label="Password"
              autoComplete="current-password"
              required
            />
          </div>

          {error ? <p className="login-error" role="alert">{error}</p> : null}

          <Button type="submit" variant="primary" size="lg" className="login-submit" disabled={loading || isLoading || submitting}>
            {submitting ? 'Signing in…' : 'Sign in'}
          </Button>

          <p className="login-search-help" aria-live="polite">
            Seeded demo emails: {demoUsers}
          </p>
        </form>

        <footer className="login-footer">
          <p>{t('demoNotice')}</p>
          <p>Sessions are issued by the tenant-context backend and stored in an HttpOnly cookie.</p>
        </footer>
      </section>
    </main>
  );
}
