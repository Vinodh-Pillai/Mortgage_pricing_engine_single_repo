import { useEffect, useRef, useState, type CSSProperties, type KeyboardEvent as ReactKeyboardEvent } from 'react';
import { Avatar, roleColorKeyForLabel, roleColors, themeStorageKey } from '../design-system';
import { useTranslation } from '../lib/i18n';

type HeaderProps = {
  breadcrumb: string;
  notificationCount: number;
  showAuthenticatedChrome?: boolean;
  onNotificationsToggle: () => void;
  onLogout?: () => void;
  onThemeToggle: () => void;
  theme: 'dark' | 'light';
  user: { name: string; role: string; avatar?: string };
};

export function Header({ breadcrumb, notificationCount, showAuthenticatedChrome = true, onNotificationsToggle, onLogout, onThemeToggle, theme, user }: HeaderProps) {
  const { t } = useTranslation('common');
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);
  const userMenuTriggerRef = useRef<HTMLButtonElement>(null);
  const userMenuDropdownRef = useRef<HTMLDivElement>(null);
  const roleColorKey = roleColorKeyForLabel(user.role);
  const roleColor = roleColors[roleColorKey];
  const displayName = user.name?.trim() || t('pricingWorkbench');
  const initials = user.avatar ?? displayName.split(/\s+/).map((part) => part[0]).join('').slice(0, 2).toUpperCase();

  useEffect(() => {
    if (!showAuthenticatedChrome) {
      setUserMenuOpen(false);
      return undefined;
    }
    if (!userMenuOpen) return undefined;
    const onPointerDown = (event: PointerEvent) => {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) setUserMenuOpen(false);
    };
    const onKeyDown = (event: globalThis.KeyboardEvent) => {
      if (event.key === 'Escape') {
        setUserMenuOpen(false);
        userMenuTriggerRef.current?.focus();
      }
    };
    document.addEventListener('pointerdown', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('pointerdown', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [showAuthenticatedChrome, userMenuOpen]);

  useEffect(() => {
    if (!userMenuOpen) return;
    userMenuDropdownRef.current?.querySelector<HTMLElement>('[role="menuitem"]')?.focus();
  }, [userMenuOpen]);

  const closeUserMenu = () => setUserMenuOpen(false);

  const handleNotificationsToggle = () => {
    closeUserMenu();
    onNotificationsToggle();
  };

  const handleUserMenuKeyDown = (event: ReactKeyboardEvent<HTMLDivElement>) => {
    if (event.key === 'Escape') {
      event.preventDefault();
      closeUserMenu();
      userMenuTriggerRef.current?.focus();
      return;
    }
    if (event.key !== 'Tab') return;
    const items = Array.from(userMenuDropdownRef.current?.querySelectorAll<HTMLElement>('[role="menuitem"]') ?? []);
    if (!items.length) return;
    const currentIndex = items.indexOf(document.activeElement as HTMLElement);
    if (event.shiftKey && currentIndex <= 0) {
      event.preventDefault();
      items[items.length - 1].focus();
    } else if (!event.shiftKey && currentIndex === items.length - 1) {
      event.preventDefault();
      items[0].focus();
    }
  };

  const toggleTheme = () => {
    closeUserMenu();
    const nextTheme = theme === 'dark' ? 'light' : 'dark';
    try {
      window.localStorage.setItem(themeStorageKey, nextTheme);
    } catch {
      // Theme still toggles for the current render path when storage is unavailable.
    }
    onThemeToggle();
  };

  return (
    <header className="layout-header" role="banner">
      <div className="layout-header__brand">
        <div className="layout-header__brand-mark" aria-hidden="true">LW</div>
        <div className="layout-header__title">
          <h1>{t('appTitle')}</h1>
          <nav aria-label={t('navigation:breadcrumb')} className="layout-breadcrumbs">
            <ol>
              <li>{t('home')}</li>
              <li aria-current="page">{breadcrumb || t('unknownScreen')}</li>
            </ol>
          </nav>
        </div>
      </div>
      <div className="layout-header__actions">
        {showAuthenticatedChrome ? (
          <button className="layout-icon-button" type="button" onClick={handleNotificationsToggle} aria-label={t('notifications', { count: notificationCount })}>
            <span aria-hidden="true">🔔</span><span className="layout-header__action-label">{t('alerts')}</span>{notificationCount > 0 ? <span className="layout-badge">{notificationCount}</span> : null}
          </button>
        ) : null}
        <button
          className="layout-theme-toggle"
          type="button"
          aria-pressed={theme === 'dark'}
          aria-label={t('toggleTheme')}
          title={t('toggleTheme')}
          data-theme={theme}
          onClick={toggleTheme}
        >
          <span className="layout-theme-toggle__track" aria-hidden="true">
            <span className="layout-theme-toggle__icon layout-theme-toggle__icon--sun">☼</span>
            <span className="layout-theme-toggle__thumb" />
            <span className="layout-theme-toggle__icon layout-theme-toggle__icon--moon">◐</span>
          </span>
        </button>
        {showAuthenticatedChrome ? (
          <div className="layout-user-menu-shell" ref={menuRef}>
            <button ref={userMenuTriggerRef} className="layout-user-menu" type="button" aria-haspopup="menu" aria-expanded={userMenuOpen} aria-controls="layout-user-menu-dropdown" aria-label={t('userMenuFor', { name: displayName })} onClick={() => setUserMenuOpen((open) => !open)}>
              <Avatar initials={initials} roleColor={roleColor} roleLabel={user.role} aria-hidden="true" />
              <span>{displayName}</span>
              <small>{user.role}</small>
              <span className="layout-user-menu__arrow" aria-hidden="true">⌄</span>
            </button>
            {userMenuOpen ? (
              <div id="layout-user-menu-dropdown" ref={userMenuDropdownRef} className="layout-user-menu__dropdown" role="menu" aria-label={t('userMenuFor', { name: displayName })} onKeyDown={handleUserMenuKeyDown}>
                <div className="layout-user-menu__identity">
                  <Avatar initials={initials} roleColor={roleColor} roleLabel={user.role} aria-hidden="true" />
                  <div>
                    <strong>{displayName}</strong>
                    <span className="layout-role-badge" style={{ '--layout-role-color': roleColor } as CSSProperties}>{user.role}</span>
                  </div>
                </div>
                <button type="button" role="menuitem" className="layout-user-menu__item" onClick={closeUserMenu}>{t('profile')}</button>
                <button type="button" role="menuitem" className="layout-user-menu__item" onClick={closeUserMenu}>{t('settings')}</button>
                <button type="button" role="menuitem" className="layout-user-menu__item" onClick={() => { onLogout?.(); closeUserMenu(); }}>Sign Out</button>
              </div>
            ) : null}
          </div>
        ) : null}
      </div>
    </header>
  );
}
