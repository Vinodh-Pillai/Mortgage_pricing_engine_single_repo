import { useEffect, useMemo, useRef, type KeyboardEvent } from 'react';
import { NavLink } from 'react-router-dom';
import { Button } from '../design-system';
import { useTranslation } from '../lib/i18n';
import { useOptionalAuth } from '../lib/auth/AuthContext';
import { buildNavigationTree, navigationGroups } from './navigation';
import type { WorkbenchScreenModule } from '../screens/workbenchShell/WorkbenchShell';

type NavRailProps = {
  activeModuleId: string;
  activeRunId?: string | null;
  collapsed: boolean;
  drawerOpen: boolean;
  mode: 'rail' | 'drawer';
  modules: WorkbenchScreenModule[];
  onCloseDrawer: () => void;
  onToggleCollapsed: () => void;
};

export function NavRail({ activeModuleId, activeRunId, collapsed, drawerOpen, mode, modules, onCloseDrawer, onToggleCollapsed }: NavRailProps) {
  const { t } = useTranslation('navigation');
  const auth = useOptionalAuth();
  const navRef = useRef<HTMLElement>(null);
  const items = useMemo(() => buildNavigationTree(modules, activeRunId, auth?.currentPersona ?? undefined), [activeRunId, auth?.currentPersona, modules]);
  const groups = navigationGroups(items);
  const isRailCollapsed = mode === 'rail' && collapsed;
  const navClassName = ['layout-nav', mode === 'drawer' ? 'layout-nav--drawer' : 'layout-nav--rail', isRailCollapsed ? 'layout-nav--collapsed' : 'layout-nav--expanded'].filter(Boolean).join(' ');

  useEffect(() => {
    if (mode !== 'drawer' || !drawerOpen) return undefined;
    const previousActive = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const firstLink = navRef.current?.querySelector<HTMLElement>('a, button');
    firstLink?.focus();
    return () => previousActive?.focus();
  }, [drawerOpen, mode]);

  function handleKeyDown(event: KeyboardEvent<HTMLElement>) {
    const focusable = Array.from(navRef.current?.querySelectorAll<HTMLElement>('a[href], button:not(:disabled)') ?? []);
    const currentIndex = focusable.indexOf(document.activeElement as HTMLElement);
    if (event.key === 'Escape' && mode === 'drawer') {
      event.preventDefault();
      onCloseDrawer();
    }
    if (event.key === 'Tab' && mode === 'drawer' && drawerOpen && focusable.length > 0) {
      if (event.shiftKey && currentIndex === 0) {
        event.preventDefault();
        focusable[focusable.length - 1].focus();
      } else if (!event.shiftKey && currentIndex === focusable.length - 1) {
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
      className={navClassName}
      role={mode === 'drawer' ? 'dialog' : 'navigation'}
      aria-label={mode === 'drawer' ? t('primaryNavigationDrawer') : t('mainNavigation')}
      aria-modal={mode === 'drawer' ? true : undefined}
      onKeyDown={handleKeyDown}
    >
      <div className="layout-nav__header">
        <h2 className="layout-nav__title">{mode === 'drawer' ? t('navigateWorkbench') : t('workspace')}</h2>
        {mode === 'drawer' ? (
          <Button type="button" variant="ghost" onClick={onCloseDrawer}>{t('common:close')}</Button>
        ) : (
          <Button
            type="button"
            variant="ghost"
            onClick={onToggleCollapsed}
            aria-expanded={!collapsed}
            aria-label={collapsed ? t('expand') : t('collapse')}
            title={collapsed ? t('expand') : t('collapse')}
          >
            {collapsed ? '›' : t('collapse')}
          </Button>
        )}
      </div>
      {items.length === 0 ? <p role="status">{t('noAccessibleModules')}</p> : null}
      {groups.map((group) => {
        const groupId = `nav-group-${group.toLowerCase().replace(/\s+/g, '-')}`;
        return (
          <section className="layout-nav__group" key={group} aria-labelledby={groupId}>
            <h3 id={groupId} aria-label={group}>{isRailCollapsed ? group.slice(0, 1) : group}</h3>
            {items.filter((item) => item.group === group).map((item) => (
              <NavLink
                key={item.id}
                className={({ isActive }) => `layout-nav__link${isActive || item.id === activeModuleId ? ' layout-nav__link--active' : ''}`}
                to={item.route}
                title={`${item.label}${item.badgeLabel ? ` · ${item.badgeLabel}` : ''}`}
                aria-label={item.badgeLabel ? `${item.label}, ${item.badgeLabel}` : item.label}
                onClick={() => {
                  if (mode === 'drawer') onCloseDrawer();
                }}
              >
                <span className="layout-nav__link-label" aria-hidden={isRailCollapsed ? true : undefined}>{isRailCollapsed ? item.label.slice(0, 1) : item.label}</span>
                {item.badgeCount ? <span className="layout-badge" aria-label={item.badgeLabel ?? t('attentionItem', { count: item.badgeCount })}>{item.badgeCount}</span> : null}
              </NavLink>
            ))}
          </section>
        );
      })}
    </nav>
  );
}
