import { useCallback, useState, type ReactNode } from 'react';
import { useTranslation } from '../lib/i18n';
import { workbenchModules, type WorkbenchScreenModule } from '../screens/workbenchShell/WorkbenchShell';
import { ContentArea } from './ContentArea';
import { Footer } from './Footer';
import { Header } from './Header';
import { useNavRailMode } from './hooks/useNavRailMode';
import './layout.css';
import { NavRail } from './NavRail';
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
  const mode = useNavRailMode();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [navCollapsed, setNavCollapsed] = useState(readPersistedNavRailCollapsed);
  const [notificationsOpen, setNotificationsOpen] = useState(false);

  const closeDrawer = useCallback(() => setDrawerOpen(false), []);
  const toggleCollapsed = useCallback(() => {
    setNavCollapsed((current) => {
      persistNavRailCollapsed(!current);
      return !current;
    });
  }, []);

  return (
    <div className="layout-shell" data-breakpoint-mode={mode}>
      <SkipLink />
      <Header
        breadcrumb={breadcrumb}
        drawerOpen={drawerOpen}
        notificationCount={notifications.length}
        onMenuToggle={() => setDrawerOpen((current) => !current)}
        onNotificationsToggle={() => setNotificationsOpen((current) => !current)}
        onThemeToggle={onThemeToggle}
        theme={theme}
        user={user}
      />
      <div className="layout-frame">
        <NavRail
          activeModuleId={activeModuleId}
          activeRunId={activeRunId}
          collapsed={navCollapsed}
          drawerOpen={drawerOpen}
          mode={mode}
          modules={modules}
          onCloseDrawer={closeDrawer}
          onToggleCollapsed={toggleCollapsed}
        />
        <ContentArea>{children}</ContentArea>
      </div>
      {notificationsOpen ? (
        <aside className="layout-notifications" aria-label={t('notifications', { count: notifications.length })} role="status">
          {notifications.length ? notifications.map((notification) => <p key={notification.id}>{notification.label}</p>) : <p>{t('noNotifications')}</p>}
        </aside>
      ) : null}
      <Footer />
    </div>
  );
}
