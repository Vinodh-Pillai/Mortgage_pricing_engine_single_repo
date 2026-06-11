import { useEffect, useMemo, useRef, type KeyboardEvent } from 'react';
import { Button } from '../design-system';
import { useTranslation } from '../lib/i18n';
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
  const navRef = useRef<HTMLElement>(null);
  const items = useMemo(() => buildNavigationTree(modules, activeRunId), [activeRunId, modules]);
  const groups = navigationGroups(items);

  useEffect(() => {
    if (mode !== 'drawer' || !drawerOpen) return undefined;
    const previousActive = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const firstLink = navRef.current?.querySelector<HTMLElement>('a, button');
    firstLink?.focus();
    return () => previousActive?.focus();
  }, [drawerOpen, mode]);

  useEffect(() => {
    if (mode !== 'drawer' && drawerOpen) onCloseDrawer();
  }, [drawerOpen, mode, onCloseDrawer]);

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
      className={mode === 'drawer' ? 'layout-nav layout-nav--drawer' : collapsed ? 'layout-nav layout-nav--collapsed' : 'layout-nav'}
      role={mode === 'drawer' ? 'dialog' : 'navigation'}
      aria-label={mode === 'drawer' ? t('primaryNavigationDrawer') : t('mainNavigation')}
      aria-modal={mode === 'drawer' ? true : undefined}
      onKeyDown={handleKeyDown}
    >
      <div className="layout-nav__header">
        <h2>{mode === 'drawer' ? t('navigateWorkbench') : t('workspace')}</h2>
        {mode === 'drawer' ? <Button type="button" variant="ghost" onClick={onCloseDrawer}>{t('common:close')}</Button> : <Button type="button" variant="ghost" onClick={onToggleCollapsed}>{collapsed ? t('expand') : t('collapse')}</Button>}
      </div>
      {items.length === 0 ? <p role="status">{t('noAccessibleModules')}</p> : null}
      {groups.map((group) => (
        <section className="layout-nav__group" key={group} aria-labelledby={`nav-group-${group.toLowerCase()}`}>
          <h3 id={`nav-group-${group.toLowerCase()}`}>{collapsed && mode === 'rail' ? group.slice(0, 1) : group}</h3>
          {items.filter((item) => item.group === group).map((item) => (
            <a key={item.id} className="layout-nav__link" href={item.route} aria-current={item.id === activeModuleId ? 'page' : undefined} title={item.label}>
              <span>{collapsed && mode === 'rail' ? item.label.slice(0, 1) : item.label}</span>
              {item.badgeCount ? <span className="layout-badge" aria-label={t('attentionItem', { count: item.badgeCount })}>{item.badgeCount}</span> : null}
            </a>
          ))}
        </section>
      ))}
      <a className="layout-nav__link" href="#status-panel">{t('connectionStatus')}</a>
      <a className="layout-nav__link" href="#notice-panel">{t('notices')}</a>
      <a className="layout-nav__link" href="#help-panel">{t('common:help')}</a>
    </nav>
  );
}
