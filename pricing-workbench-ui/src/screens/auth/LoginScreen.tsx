import { useEffect, useState, type FormEvent } from 'react';
import { useLocation } from 'react-router-dom';
import { Button, Input } from '../../design-system';
import { useAuth } from '../../lib/auth/AuthContext';
import { useTranslation } from '../../lib/i18n';
import { useAppNavigate } from '../../routing/hooks';
import type { ScreenProps } from '../contract/ScreenProps';
import './LoginScreen.css';

export type LoginEvidenceType = 'login-submit' | 'login-error' | 'route-redirect' | 'forgot-password';

export interface LoginEvidenceDetail {
  type: LoginEvidenceType;
  userId?: string;
  target?: string;
  value?: string;
}

export interface LoginScreenProps extends Partial<ScreenProps> {
  initialEmail?: string;
  loading?: boolean;
  disableAutoRedirect?: boolean;
}

function emitLoginEvidence(detail: LoginEvidenceDetail) {
  if (typeof window === 'undefined' || typeof window.dispatchEvent !== 'function') return;
  window.dispatchEvent(new CustomEvent<LoginEvidenceDetail>('loanweft:login-screen', { detail }));
}

export function LoginScreen({ initialEmail = '', loading = false, disableAutoRedirect = false }: LoginScreenProps) {
  const { user, isAuthenticated, isLoading, login } = useAuth();
  const location = useLocation();
  const navigate = useAppNavigate();
  const { t } = useTranslation('auth');
  const [email, setEmail] = useState(initialEmail);
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!disableAutoRedirect && isAuthenticated && location.pathname === '/login') {
      emitLoginEvidence({ type: 'route-redirect', userId: user?.id, target: '/home' });
      navigate('/home', { replace: true });
    }
  }, [disableAutoRedirect, isAuthenticated, location.pathname, navigate, user?.id]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const signedInUser = await login(email, password);
      emitLoginEvidence({ type: 'login-submit', userId: signedInUser.id, target: '/home' });
      navigate('/home', { replace: true });
    } catch (exception) {
      const message = exception instanceof Error ? exception.message : 'Invalid email or password';
      setError(message);
      emitLoginEvidence({ type: 'login-error', value: message });
    } finally {
      setSubmitting(false);
    }
  }

  function handleForgotPassword(event: React.MouseEvent<HTMLAnchorElement>) {
    event.preventDefault();
    emitLoginEvidence({ type: 'forgot-password', target: '/forgot-password' });
  }

  const authResolving = loading || isLoading;
  const busy = authResolving || submitting;

  return (
    <main className="login-page" aria-labelledby="login-title" data-testid="login-page">
      <section className="login-container" aria-describedby="login-tagline">
        <div className="login-hero">
          <div className="login-logo" aria-hidden="true">
            <span className="login-logo__mark">◇</span>
          </div>
          <p className="login-eyebrow">Secure sign in</p>
          <h1 id="login-title">{t('pricingWorkbench')}</h1>
          <p id="login-tagline" className="login-tagline">{t('tagline')}</p>
        </div>

        <form className="login-search-panel login-form" onSubmit={handleSubmit} aria-busy={busy}>
          <label htmlFor="login-email" className="login-search-label">Email</label>
          <div className="login-search-shell">
            <span aria-hidden="true">@</span>
            <Input
              id="login-email"
              type="email"
              placeholder="user@example.com"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              className="login-search"
              aria-label="Email"
              autoComplete="email"
              required
              disabled={busy}
            />
          </div>

          <label htmlFor="login-password" className="login-search-label">Password</label>
          <div className="login-search-shell login-search-shell--password">
            <Input
              id="login-password"
              type="password"
              placeholder="Enter password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              className="login-search"
              aria-label="Password"
              autoComplete="current-password"
              required
              disabled={busy}
            />
          </div>

          {authResolving ? <p className="login-status" role="status">Checking your session before sign in…</p> : null}
          {error ? <p className="login-error" role="alert">{error}</p> : null}

          <div className="login-form__actions">
            <Button type="submit" variant="primary" size="lg" className="login-submit" disabled={busy}>
              {authResolving ? 'Checking session…' : submitting ? 'Signing in…' : 'Sign in'}
            </Button>
            <a href="/forgot-password" className="login-forgot" onClick={handleForgotPassword}>Forgot password?</a>
          </div>

          <p className="login-search-help" aria-live="polite">
            Use your organization account to access LoanWeft.
          </p>
        </form>
      </section>
    </main>
  );
}
