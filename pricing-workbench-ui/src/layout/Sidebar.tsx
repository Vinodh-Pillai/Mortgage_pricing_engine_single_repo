import { useEffect, useMemo, useRef, type KeyboardEvent } from 'react';
import { NavLink } from 'react-router-dom';
import { useOptionalAuth } from '../lib/auth/AuthContext';
import { useTranslation } from '../lib/i18n';
import type { WorkbenchScreenModule } from '../screens/workbenchShell/WorkbenchShell';
import { buildNavigationTree, navigationGroups } from './navigation';

type SidebarProps = {
  activeModuleId: string;
  activeRunId?: string | null;
  collapsed: boolean;
  drawerOpen: boolean;
  mode: 'rail' | 'drawer';
  modules: WorkbenchScreenModule[];
  fallbackPersona?: string;
  onCloseDrawer: () => void;
  onToggleCollapsed: () => void;
  returnFocusTarget?: () => HTMLElement | null;
};

export function Sidebar({ activeModuleId, activeRunId, collapsed, drawerOpen, mode, modules, fallbackPersona, onCloseDrawer, onToggleCollapsed, returnFocusTarget }: SidebarProps) {
  const { t } = useTranslation('navigation');
  const auth = useOptionalAuth();
  const navRef = useRef<HTMLElement>(null);
  const navigationPersona = auth ? auth.currentPersona : fallbackPersona;
  const items = useMemo(() => buildNavigationTree(modules, activeRunId, navigationPersona), [activeRunId, navigationPersona, modules]);
  const groups = navigationGroups(items);
  const isCollapsed = mode === 'rail' && collapsed;
  const sidebarClassName = ['layout-sidebar', mode === 'drawer' ? 'layout-sidebar--drawer' : 'layout-sidebar--rail', isCollapsed ? 'layout-sidebar--collapsed' : 'layout-sidebar--expanded'].join(' ');

  function getFocusableControls() {
    return Array.from(navRef.current?.querySelectorAll<HTMLElement>('a.layout-sidebar__link[href], button:not(:disabled)') ?? []);
  }

  useEffect(() => {
    if (mode !== 'drawer' || !drawerOpen) return undefined;
    const previousActive = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const firstControl = navRef.current?.querySelector<HTMLElement>('a[href], button:not(:disabled)');
    firstControl?.focus();
    return () => (returnFocusTarget?.() ?? previousActive)?.focus({ preventScroll: true });
  }, [drawerOpen, mode, returnFocusTarget]);

  function handleKeyDown(event: KeyboardEvent<HTMLElement>) {
    const focusable = getFocusableControls();
    const currentIndex = focusable.indexOf(document.activeElement as HTMLElement);
    if (event.key === 'Escape' && mode === 'drawer') {
      event.preventDefault();
      onCloseDrawer();
      return;
    }
    if (event.key === 'Tab' && mode === 'drawer' && drawerOpen && focusable.length > 0) {
      if (event.shiftKey && currentIndex <= 0) {
        event.preventDefault();
        focusable[focusable.length - 1].focus();
      } else if (!event.shiftKey && (currentIndex === -1 || currentIndex === focusable.length - 1)) {
        event.preventDefault();
        focusable[0].focus();
      }
    }
    if ((event.key === 'ArrowDown' || event.key === 'ArrowUp') && focusable.length > 0) {
      event.preventDefault();
      const direction = event.key === 'ArrowDown' ? 1 : -1;
      focusable[(currentIndex + direction + focusable.length) % focusable.length].focus();
    }
  }

  if (mode === 'drawer' && !drawerOpen) return null;

  return (
    <nav
      id="primary-navigation"
      ref={navRef}
      className={sidebarClassName}
      role={mode === 'drawer' ? 'dialog' : 'navigation'}
      aria-label={mode === 'drawer' ? t('primaryNavigationDrawer') : t('mainNavigation')}
      aria-modal={mode === 'drawer' ? true : undefined}
      data-collapsed={isCollapsed ? 'true' : 'false'}
      onKeyDown={handleKeyDown}
    >
      <div className="layout-sidebar__chrome" aria-hidden="true" />
      <div className="layout-sidebar__actions">
        {mode === 'rail' ? (
          <button
            type="button"
            className="layout-sidebar__control layout-sidebar__control--collapse"
            onClick={onToggleCollapsed}
            aria-expanded={!collapsed}
            aria-label={collapsed ? t('expand') : t('collapse')}
            title={collapsed ? t('expand') : t('collapse')}
          >
            <span aria-hidden="true">{collapsed ? '›' : '‹'}</span>
            <span className="layout-sidebar__control-label">{collapsed ? t('expand') : t('collapse')}</span>
          </button>
        ) : null}
      </div>
      {items.length === 0 ? <p className="layout-sidebar__empty" role="status">{t('noAccessibleModules')}</p> : null}
      <div className="layout-sidebar__groups">
        {groups.map((group) => {
          const groupId = `sidebar-group-${group.toLowerCase().replace(/\s+/g, '-')}`;
          const groupItems = items.filter((item) => item.group === group);
          const groupAlertCount = groupItems.reduce((total, item) => total + (item.badgeTone === 'alert' ? item.badgeCount ?? 0 : 0), 0);
          return (
            <section className="layout-sidebar__group" key={group} aria-labelledby={groupId}>
              <h2 id={groupId} aria-label={group} title={group}>
                <span aria-hidden="true">{group.slice(0, 1)}</span>
                <span className="layout-sidebar__group-label">{group}</span>
                {groupAlertCount > 0 ? <span className="layout-sidebar__group-alert" aria-label={`${groupAlertCount} alerts in ${group}`}>{groupAlertCount}</span> : null}
              </h2>
              <div className="layout-sidebar__links">
                {groupItems.map((item) => (
                  <span key={item.id}>
                    <NavLink
                      className={({ isActive }) => ['layout-sidebar__link', isActive || item.id === activeModuleId || item.sourceModuleId === activeModuleId ? 'layout-sidebar__link--active' : '', item.badgeTone ? `layout-sidebar__link--${item.badgeTone}` : ''].filter(Boolean).join(' ')}
                      to={item.route}
                      title={`${item.label}${item.badgeLabel ? ` · ${item.badgeLabel}` : ''}`}
                      aria-label={item.badgeLabel ? `${item.label}, ${item.badgeLabel}` : item.label}
                      onClick={() => {
                        if (mode === 'drawer') onCloseDrawer();
                      }}
                    >
                      <span className="layout-sidebar__link-icon" aria-hidden="true">{item.icon}</span>
                      <span className="layout-sidebar__link-label">{item.label}</span>
                      {item.badgeCount ? <span className="layout-badge layout-sidebar__badge" aria-label={item.badgeLabel}>{item.badgeCount}</span> : null}
                    </NavLink>
                  </span>
                ))}
              </div>
            </section>
          );
        })}
      </div>
    </nav>
  );
}
