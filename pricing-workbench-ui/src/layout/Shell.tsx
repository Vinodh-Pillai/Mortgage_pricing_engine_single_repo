import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { useTranslation } from '../lib/i18n';
import { useOptionalAuth } from '../lib/auth/AuthContext';
import { roleLabels } from '../lib/auth/personas';
import { workbenchModules, type WorkbenchScreenModule } from '../screens/workbenchShell/WorkbenchShell';
import { ContentArea } from './ContentArea';
import { Footer } from './Footer';
import { themeStorageKey } from '../design-system';
import { Header } from './Header';
import { useNavRailMode } from './hooks/useNavRailMode';
import './layout.css';
import { Sidebar } from './Sidebar';
import { SkipLink } from './SkipLink';
import { persistNavRailCollapsed, readPersistedNavRailCollapsed } from './state/navRailState';

export type Notification = { id: string; label: string; attentionRequired?: boolean };

export interface ShellProps {
  children: ReactNode;
  activeModuleId: string;
  activeRunId?: string | null;
  breadcrumb: string;
  modules?: WorkbenchScreenModule[];
  onThemeToggle: () => void;
  theme: 'dark' | 'light';
  user: { name: string; role: string; avatar?: string };
  notifications: Notification[];
}

export function Shell({ children, activeModuleId, activeRunId, breadcrumb, modules = workbenchModules, onThemeToggle, theme, user, notifications }: ShellProps) {
  const { t } = useTranslation('common');
  const auth = useOptionalAuth();
  const showAuthenticatedChrome = auth ? auth.isLoading || auth.isAuthenticated : true;
  const currentUser = auth?.currentPersona ? { name: auth.currentPersona.name, role: roleLabels[auth.currentPersona.role], avatar: auth.currentPersona.avatar } : user;
  const mode = useNavRailMode();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [navCollapsed, setNavCollapsed] = useState(() => {
    try {
      return readPersistedNavRailCollapsed();
    } catch {
      return false;
    }
  });
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const railCollapsed = mode === 'rail' && navCollapsed;

  useEffect(() => {
    try {
      if (window.localStorage.getItem(themeStorageKey) !== theme) window.localStorage.setItem(themeStorageKey, theme);
    } catch {
      // Storage can be disabled; the shell remains usable.
    }
  }, [theme]);

  useEffect(() => {
    if (mode === 'rail') setDrawerOpen(false);
  }, [mode]);

  useEffect(() => {
    if (showAuthenticatedChrome) return;
    setDrawerOpen(false);
    setNotificationsOpen(false);
  }, [showAuthenticatedChrome]);

  const closeDrawer = useCallback(() => setDrawerOpen(false), []);
  const toggleCollapsed = useCallback(() => {
    setNavCollapsed((current) => {
      const next = !current;
      try {
        persistNavRailCollapsed(next);
      } catch {
        // Storage can be disabled; keep the in-memory rail state usable.
      }
      return next;
    });
  }, []);
  return (
    <div className={`layout-shell layout-shell--${mode}${railCollapsed ? ' layout-shell--nav-collapsed' : ''}`} data-breakpoint-mode={mode} data-nav-collapsed={railCollapsed ? 'true' : 'false'}>
      <SkipLink />
      <Header
        breadcrumb={breadcrumb}
        notificationCount={showAuthenticatedChrome ? notifications.length : 0}
        showAuthenticatedChrome={showAuthenticatedChrome}
        onNotificationsToggle={() => {
          if (showAuthenticatedChrome) setNotificationsOpen((current) => !current);
        }}
        onLogout={auth?.logout}
        onThemeToggle={onThemeToggle}
        theme={theme}
        user={currentUser}
      />
      <div className="layout-frame">
        {showAuthenticatedChrome ? (
          <>
            {mode === 'drawer' && drawerOpen ? <button type="button" className="layout-sidebar-backdrop" aria-label="Close navigation backdrop" onClick={closeDrawer} /> : null}
            <Sidebar
              activeModuleId={activeModuleId}
              activeRunId={activeRunId}
              collapsed={railCollapsed}
              drawerOpen={drawerOpen}
              mode={mode}
              modules={modules}
              onCloseDrawer={closeDrawer}
              onOpenDrawer={() => setDrawerOpen(true)}
              onToggleCollapsed={toggleCollapsed}
            />
          </>
        ) : null}
        <ContentArea>{children}</ContentArea>
      </div>
      {showAuthenticatedChrome && notificationsOpen ? (
        <aside className="layout-notifications" aria-label={t('notifications', { count: notifications.length })} role="status">
          {notifications.length ? notifications.map((notification) => <p key={notification.id}>{notification.label}</p>) : <p>{t('noNotifications')}</p>}
        </aside>
      ) : null}
      <Footer />
    </div>
  );
}
