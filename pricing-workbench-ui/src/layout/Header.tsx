import { useEffect, useRef, useState, type CSSProperties } from 'react';
import { Avatar, Button, Switch, roleColorKeyForLabel, roleColors } from '../design-system';
import { useTranslation } from '../lib/i18n';

export const layoutThemeStorageKey = 'wcpe:layout-theme';

type HeaderProps = {
  breadcrumb: string;
  drawerOpen: boolean;
  notificationCount: number;
  onMenuToggle: () => void;
  onNotificationsToggle: () => void;
  onLogout?: () => void;
  onThemeToggle: () => void;
  theme: 'dark' | 'light';
  user: { name: string; role: string; avatar?: string };
};

export function Header({ breadcrumb, drawerOpen, notificationCount, onMenuToggle, onNotificationsToggle, onLogout, onThemeToggle, theme, user }: HeaderProps) {
  const { t } = useTranslation('common');
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);
  const roleColorKey = roleColorKeyForLabel(user.role);
  const roleColor = roleColors[roleColorKey];
  const initials = user.avatar ?? user.name.split(/\s+/).map((part) => part[0]).join('').slice(0, 2).toUpperCase();

  useEffect(() => {
    if (!userMenuOpen) return undefined;
    const onPointerDown = (event: PointerEvent) => {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) setUserMenuOpen(false);
    };
    const onKeyDown = (event: globalThis.KeyboardEvent) => {
      if (event.key === 'Escape') setUserMenuOpen(false);
    };
    document.addEventListener('pointerdown', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('pointerdown', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [userMenuOpen]);

  const toggleTheme = () => {
    const nextTheme = theme === 'dark' ? 'light' : 'dark';
    try {
      window.localStorage.setItem(layoutThemeStorageKey, nextTheme);
    } catch {
      // Theme still toggles for the current render path when storage is unavailable.
    }
    onThemeToggle();
  };

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
          <span aria-hidden="true">🔔</span><span className="layout-header__action-label">{t('alerts')}</span>{notificationCount > 0 ? <span className="layout-badge">{notificationCount}</span> : null}
        </button>
        <label className="layout-theme-toggle">
          <span aria-hidden="true">{theme === 'dark' ? '🌙' : '☀️'}</span>
          <span>{t('theme', { theme: theme === 'dark' ? t('dark') : t('light') })}</span>
          <Switch checked={theme === 'dark'} onClick={toggleTheme} aria-label={t('toggleTheme')} />
        </label>
        <div className="layout-user-menu-shell" ref={menuRef}>
          <button className="layout-user-menu" type="button" aria-haspopup="menu" aria-expanded={userMenuOpen} aria-label={t('userMenuFor', { name: user.name })} onClick={() => setUserMenuOpen((open) => !open)}>
            <Avatar initials={initials} roleColor={roleColor} roleLabel={user.role} aria-hidden="true" />
            <span>{user.name}</span>
            <small>{user.role}</small>
          </button>
          {userMenuOpen ? (
            <div className="layout-user-menu__dropdown" role="menu" aria-label={t('userMenuFor', { name: user.name })}>
              <div className="layout-user-menu__identity">
                <Avatar initials={initials} roleColor={roleColor} roleLabel={user.role} aria-hidden="true" />
                <div>
                  <strong>{user.name}</strong>
                  <span className="layout-role-badge" style={{ '--layout-role-color': roleColor } as CSSProperties}>{user.role}</span>
                </div>
              </div>
              <button type="button" role="menuitem" className="layout-user-menu__item" onClick={() => { onLogout?.(); setUserMenuOpen(false); }}>{t('logout')}</button>
            </div>
          ) : null}
        </div>
      </div>
    </header>
  );
}
