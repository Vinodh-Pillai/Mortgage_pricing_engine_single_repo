import { Button, Switch } from '../design-system';
import { useTranslation } from '../lib/i18n';

type HeaderProps = {
  breadcrumb: string;
  drawerOpen: boolean;
  notificationCount: number;
  onMenuToggle: () => void;
  onNotificationsToggle: () => void;
  onThemeToggle: () => void;
  theme: 'dark' | 'light';
  user: { name: string; role: string; avatar?: string };
};

export function Header({ breadcrumb, drawerOpen, notificationCount, onMenuToggle, onNotificationsToggle, onThemeToggle, theme, user }: HeaderProps) {
  const { t } = useTranslation('common');
  return (
    <header className="layout-header" role="banner">
      <Button
        className="layout-header__menu"
        type="button"
        variant="ghost"
        aria-label={drawerOpen ? t('closeNavigationMenu') : t('openNavigationMenu')}
        aria-expanded={drawerOpen}
        aria-controls="primary-navigation"
        onClick={onMenuToggle}
      >
        {t('menu')}
      </Button>
      <div className="layout-header__title">
        <p className="eyebrow">{t('appTitle')}</p>
        <h1>{t('pricingWorkbench')}</h1>
        <nav aria-label={t('navigation:breadcrumb')} className="layout-breadcrumbs">
          <ol>
            <li>{t('home')}</li>
            <li aria-current="page">{breadcrumb || t('unknownScreen')}</li>
          </ol>
        </nav>
      </div>
      <div className="layout-header__actions">
        <button className="layout-icon-button" type="button" onClick={onNotificationsToggle} aria-label={t('notifications', { count: notificationCount })}>
          {t('alerts')}{notificationCount > 0 ? <span className="layout-badge">{notificationCount}</span> : null}
        </button>
        <label className="layout-theme-toggle">
          <span>{t('theme', { theme: theme === 'dark' ? t('dark') : t('light') })}</span>
          <Switch checked={theme === 'dark'} onClick={onThemeToggle} aria-label={t('toggleTheme')} />
        </label>
        <button className="layout-user-menu" type="button" aria-haspopup="menu" aria-label={t('userMenuFor', { name: user.name })}>
          <span className="layout-user-menu__avatar" aria-hidden="true">{user.avatar ?? user.name.slice(0, 1).toUpperCase()}</span>
          <span>{user.name}</span>
          <small>{user.role}</small>
        </button>
      </div>
    </header>
  );
}
