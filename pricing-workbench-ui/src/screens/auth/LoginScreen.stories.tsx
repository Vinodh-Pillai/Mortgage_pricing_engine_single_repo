import { MemoryRouter } from 'react-router-dom';
import type { ReactNode } from 'react';
import { AuthProvider } from '../../lib/auth/AuthContext';
import { LocaleProvider } from '../../lib/i18n';
import { ThemeProvider } from '../../design-system';
import { LoginScreen } from './LoginScreen';

export default {
  title: 'Auth/Login Screen',
};

function StoryFrame({ children, theme = 'dark' }: { children: ReactNode; theme?: 'dark' | 'light' }) {
  return (
    <LocaleProvider>
      <ThemeProvider defaultPreference={theme}>
        <AuthProvider>
          <MemoryRouter initialEntries={['/login']}>
            <div data-theme={theme}>{children}</div>
          </MemoryRouter>
        </AuthProvider>
      </ThemeProvider>
    </LocaleProvider>
  );
}

export function DefaultDarkDesktop() {
  return <StoryFrame><LoginScreen disableAutoRedirect /></StoryFrame>;
}

export function DefaultLightDesktop() {
  return <StoryFrame theme="light"><LoginScreen disableAutoRedirect /></StoryFrame>;
}

export function FilteredSearchResults() {
  return <StoryFrame><LoginScreen initialSearchTerm="pricing" disableAutoRedirect /></StoryFrame>;
}

export function SelectedPersonaConfirmation() {
  return <StoryFrame><LoginScreen initialSelectedPersonaId="persona-loan-officer" disableAutoRedirect /></StoryFrame>;
}

export function EmptyState() {
  return <StoryFrame><LoginScreen initialSearchTerm="no matching persona" disableAutoRedirect /></StoryFrame>;
}

export function LoadingState() {
  return <StoryFrame><LoginScreen loading disableAutoRedirect /></StoryFrame>;
}

export function MobileViewport() {
  return <StoryFrame><div style={{ maxWidth: 390 }}><LoginScreen disableAutoRedirect /></div></StoryFrame>;
}

export function TabletViewport() {
  return <StoryFrame><div style={{ maxWidth: 768 }}><LoginScreen disableAutoRedirect /></div></StoryFrame>;
}
