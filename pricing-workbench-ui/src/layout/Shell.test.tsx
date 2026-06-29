import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { getBreakpointForWidth } from './hooks/useBreakpoint';
import { buildNavigationTree } from './navigation';
import { Shell } from './Shell';
import { themeStorageKey, zIndex } from '../design-system';
import type { WorkbenchScreenModule } from '../screens/workbenchShell/WorkbenchShell';

const modules: WorkbenchScreenModule[] = [
  {
    id: 'quote',
    label: 'Quote workspace',
    routePattern: '/quote/:runId/offers',
    breadcrumb: 'Quote',
    screenPackage: 'screens/quote',
    dataBoundary: 'lib/api/offers',
    stateCoverage: ['ready'],
    evidenceTarget: '.local-harness/evidence/PII-24-S02/quote.json',
    match: () => true,
  },
  {
    id: 'ops',
    label: 'Operations dashboard',
    routePattern: '/ops/dashboard',
    breadcrumb: 'Ops',
    screenPackage: 'screens/ops',
    dataBoundary: 'lib/api/opsCases',
    stateCoverage: ['blocked'],
    personaVisibility: ['operations lead'],
    evidenceTarget: '.local-harness/evidence/PII-24-S02/ops.json',
    match: () => false,
  },
];

function installMatchMedia(matches = false) {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches,
      media: query,
      onchange: null,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
}

function renderShell(ui: ReactElement, initialEntry = '/quote/run-123/offers') {
  return render(<MemoryRouter initialEntries={[initialEntry]}>{ui}</MemoryRouter>);
}

describe('responsive layout shell', () => {
  beforeEach(() => {
    window.localStorage.removeItem('loanweft:layout-shell:nav-rail-collapsed');
  });

  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
    window.localStorage.removeItem('loanweft:layout-shell:nav-rail-collapsed');
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 1200 });
  });

  it('returns correct breakpoint names at layout widths', () => {
    expect(getBreakpointForWidth(375)).toBe('mobile');
    expect(getBreakpointForWidth(768)).toBe('tablet');
    expect(getBreakpointForWidth(1200)).toBe('desktop');
    expect(getBreakpointForWidth(1600)).toBe('wide');
  });

  it('builds navigation from registry modules and persona visibility', () => {
    const items = buildNavigationTree(modules, 'run-123', 'operations lead');
    expect(items).toEqual(expect.arrayContaining([expect.objectContaining({ label: 'Quote workspace', route: '/quote/run-123/offers', group: 'Quotes' })]));
    expect(items).toEqual(expect.arrayContaining([expect.objectContaining({ label: 'Operations dashboard', group: 'Operations', badgeCount: undefined })]));
    expect(items).not.toEqual(expect.arrayContaining([expect.objectContaining({ label: 'Quick Quote', group: 'Pipeline' })]));
    expect(items).not.toEqual(expect.arrayContaining([expect.objectContaining({ label: 'Feature Flags', group: 'Admin' })]));
  });

  it('renders header nav content footer and focusable skip link', () => {
    installMatchMedia(false);
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 1200 });
    renderShell(
      <Shell activeModuleId="quote" activeRunId="run-123" breadcrumb="Quote" modules={modules} notifications={[{ id: 'n1', label: 'One blocker needs review' }]} onThemeToggle={vi.fn()} theme="light" user={{ name: 'Alex Rivera', role: 'Pricing analyst' }}>
        <section aria-label="Workbench content">Screen content</section>
      </Shell>,
    );

    expect(screen.getByText('Skip to main content')).toHaveAttribute('href', '#main-content');
    expect(screen.getByRole('banner')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Pricing Workbench' })).toBeInTheDocument();
    expect(screen.getByRole('banner')).not.toHaveTextContent(/LoanWeft|PPE Command Center/i);
    expect(screen.queryByRole('navigation', { name: 'Main navigation' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Open navigation menu' }));
    expect(screen.getByRole('dialog', { name: 'Primary navigation drawer' })).toBeInTheDocument();
    expect(screen.getByRole('main')).toHaveTextContent('Screen content');
    expect(screen.getByRole('contentinfo')).toHaveTextContent('Pricing Workbench v0.1.0');
    expect(screen.getByRole('contentinfo')).not.toHaveTextContent(/LoanWeft|PPE Command Center/i);
    expect(screen.getByRole('link', { name: 'Quote workspace' })).toHaveClass('layout-sidebar__link--active');
  });

  it('activates full-screen workspace navigation as a popup overlay', () => {
    installMatchMedia(false);
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 1440 });
    renderShell(
      <Shell activeModuleId="quote" activeRunId="run-123" breadcrumb="Pipeline" fullScreenWorkspace modules={modules} notifications={[]} onThemeToggle={vi.fn()} theme="light" user={{ name: 'Alex Rivera', role: 'Pricing analyst' }}>
        <section aria-label="Pipeline workspace">Dense workspace</section>
      </Shell>,
      '/pipeline',
    );

    const shell = document.querySelector('.layout-shell');
    expect(shell).toHaveClass('layout-shell--workspace');
    expect(shell).toHaveAttribute('data-full-screen-workspace', 'true');
    expect(document.querySelector('.layout-frame')).toHaveClass('layout-frame--popup-navigation');
    expect(screen.getByRole('main')).toHaveClass('layout-content--workspace');
    expect(document.querySelector('.layout-content__panel')).toHaveClass('layout-content__panel--workspace');
    expect(screen.queryByRole('contentinfo')).not.toBeInTheDocument();
    expect(screen.queryByRole('navigation', { name: 'Main navigation' })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Open navigation menu' }));
    expect(screen.getByRole('dialog', { name: 'Primary navigation drawer' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Close' })).not.toBeInTheDocument();
    const backdrop = document.querySelector('.layout-sidebar-backdrop') as HTMLElement;
    expect(backdrop).toBeInTheDocument();
    fireEvent.click(backdrop);
    expect(screen.queryByRole('dialog', { name: 'Primary navigation drawer' })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Open navigation menu' }));
    expect(screen.getByRole('dialog', { name: 'Primary navigation drawer' })).toBeInTheDocument();
    fireEvent.keyDown(screen.getByRole('dialog', { name: 'Primary navigation drawer' }), { key: 'Escape' });
    expect(screen.queryByRole('dialog', { name: 'Primary navigation drawer' })).not.toBeInTheDocument();
    expect(screen.getByRole('main')).toHaveFocus();
  });

  it('traps keyboard tab focus inside the popup navigation drawer', () => {
    installMatchMedia(false);
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 1440 });
    renderShell(
      <Shell activeModuleId="quote" activeRunId="run-123" breadcrumb="Pipeline" fullScreenWorkspace modules={modules} notifications={[]} onThemeToggle={vi.fn()} theme="light" user={{ name: 'Alex Rivera', role: 'Pricing analyst' }}>
        <section aria-label="Pipeline workspace"><button type="button">Workspace action</button></section>
      </Shell>,
      '/pipeline',
    );

    fireEvent.click(screen.getByRole('button', { name: 'Open navigation menu' }));
    const dialog = screen.getByRole('dialog', { name: 'Primary navigation drawer' });
    const focusable = Array.from(dialog.querySelectorAll<HTMLElement>('a.layout-sidebar__link[href], button:not(:disabled)'));
    const nativeTabAnchors = Array.from(dialog.querySelectorAll<HTMLElement>('a[href]:not([tabindex="-1"])'));
    expect(focusable.length).toBeGreaterThan(0);
    expect(nativeTabAnchors).toHaveLength(focusable.filter((element) => element.tagName === 'A').length);
    expect(nativeTabAnchors.every((element) => element.classList.contains('layout-sidebar__link'))).toBe(true);

    const firstControl = focusable[0];
    const lastControl = focusable[focusable.length - 1];
    lastControl.focus();
    fireEvent.keyDown(dialog, { key: 'Tab' });
    expect(firstControl).toHaveFocus();

    firstControl.focus();
    fireEvent.keyDown(dialog, { key: 'Tab', shiftKey: true });
    expect(lastControl).toHaveFocus();
    expect(screen.getByRole('button', { name: 'Workspace action' })).not.toHaveFocus();
    expect(document.querySelector('.layout-sidebar-backdrop')).toHaveAttribute('tabindex', '-1');
  });

  it('closes mobile drawer with Escape and restores focus to trigger', () => {
    installMatchMedia(false);
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 390 });
    renderShell(
      <Shell activeModuleId="quote" breadcrumb="Quote" modules={modules} notifications={[]} onThemeToggle={vi.fn()} theme="dark" user={{ name: 'Alex Rivera', role: 'Pricing analyst' }}>
        <section>Screen content</section>
      </Shell>,
      '/quote/run-123/offers',
    );

    const menuButton = screen.getByRole('button', { name: 'Open navigation menu' });
    menuButton.focus();
    fireEvent.click(screen.getByRole('button', { name: 'Open navigation menu' }));
    expect(screen.getByRole('dialog', { name: 'Primary navigation drawer' })).toBeInTheDocument();
    expect(document.querySelector('.layout-sidebar-backdrop')).toBeInTheDocument();
    fireEvent.click(document.querySelector('.layout-sidebar-backdrop') as Element);
    expect(screen.queryByRole('dialog', { name: 'Primary navigation drawer' })).not.toBeInTheDocument();
    fireEvent.click(menuButton);
    expect(screen.getByRole('dialog', { name: 'Primary navigation drawer' })).toBeInTheDocument();
    fireEvent.keyDown(screen.getByRole('dialog', { name: 'Primary navigation drawer' }), { key: 'Escape' });
    expect(screen.queryByRole('dialog', { name: 'Primary navigation drawer' })).not.toBeInTheDocument();
    expect(menuButton).toHaveFocus();
  });

  it('keeps navigation floating on desktop without rendering a persistent rail', () => {
    installMatchMedia(false);
    window.localStorage.removeItem('loanweft:layout-shell:nav-rail-collapsed');
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 1199 });
    renderShell(
      <Shell activeModuleId="quote" activeRunId="run-123" breadcrumb="Quote" modules={modules} notifications={[]} onThemeToggle={vi.fn()} theme="dark" user={{ name: 'Alex Rivera', role: 'Loan officer' }}>
        <section>Screen content</section>
      </Shell>,
    );

    const shell = document.querySelector('.layout-shell');
    expect(shell).toHaveAttribute('data-breakpoint-mode', 'rail');
    expect(shell).toHaveAttribute('data-nav-collapsed', 'false');
    expect(screen.queryByRole('navigation', { name: 'Main navigation' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Collapse' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Open navigation menu' }));
    expect(screen.getByRole('dialog', { name: 'Primary navigation drawer' })).toBeInTheDocument();
    expect(window.localStorage.getItem('loanweft:layout-shell:nav-rail-collapsed')).toBeNull();
  });

  it('uses the header hamburger as the only desktop navigation opener', () => {
    installMatchMedia(false);
    window.localStorage.removeItem('loanweft:layout-shell:nav-rail-collapsed');
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 1200 });
    renderShell(
      <Shell activeModuleId="quote" activeRunId="run-123" breadcrumb="Quote" modules={modules} notifications={[]} onThemeToggle={vi.fn()} theme="dark" user={{ name: 'Alex Rivera', role: 'Loan officer' }}>
        <section>Screen content</section>
      </Shell>,
    );

    const shell = document.querySelector('.layout-shell');
    const menuButton = screen.getByRole('button', { name: 'Open navigation menu' });
    expect(menuButton).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByRole('button', { name: 'Collapse' })).not.toBeInTheDocument();
    fireEvent.click(menuButton);
    expect(menuButton).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByRole('dialog', { name: 'Primary navigation drawer' })).toBeInTheDocument();
    fireEvent.click(document.querySelector('.layout-sidebar-backdrop') as HTMLElement);
    expect(shell).not.toHaveClass('layout-shell--nav-collapsed');
    expect(screen.queryByRole('dialog', { name: 'Primary navigation drawer' })).not.toBeInTheDocument();
  });

  it('does not expose zero-count noise in header or sidebar chrome', () => {
    installMatchMedia(false);
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 1200 });
    renderShell(
      <Shell activeModuleId="quote" activeRunId="run-123" breadcrumb="Quote" modules={modules} notifications={[]} onThemeToggle={vi.fn()} theme="dark" user={{ name: 'Alex Rivera', role: 'Loan officer' }}>
        <section>Screen content</section>
      </Shell>,
    );

    const banner = screen.getByRole('banner');
    expect(banner).not.toHaveTextContent(/\b0\b/);
    expect(screen.getByRole('button', { name: 'Alerts' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Open navigation menu' }));
    expect(screen.getByRole('dialog', { name: 'Primary navigation drawer' })).not.toHaveTextContent(/\b0\b/);
  });

  it('opens user menu with role badge and persists theme toggles', () => {
    installMatchMedia(false);
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 1200 });
    const toggle = vi.fn();
    renderShell(
      <Shell activeModuleId="quote" breadcrumb="Quote" modules={modules} notifications={[{ id: 'n1', label: 'Review alert' }]} onThemeToggle={toggle} theme="dark" user={{ name: 'Alex Rivera', role: 'Pricing analyst' }}>
        <section>Screen content</section>
      </Shell>,
    );

    fireEvent.click(screen.getByRole('button', { name: 'User menu for Alex Rivera' }));
    expect(document.querySelector('.layout-user-menu-shell')).toHaveClass('layout-user-menu-shell--open');
    expect(screen.getByRole('button', { name: 'User menu for Alex Rivera' })).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByRole('menu', { name: 'User menu for Alex Rivera' })).toHaveTextContent('Profile');
    expect(screen.getByRole('menu', { name: 'User menu for Alex Rivera' })).toHaveTextContent('Settings');
    expect(screen.getByRole('menu', { name: 'User menu for Alex Rivera' })).toHaveTextContent('Sign Out');
    expect(screen.getByRole('menuitem', { name: 'Profile' })).toHaveFocus();
    fireEvent.keyDown(screen.getByRole('menu', { name: 'User menu for Alex Rivera' }), { key: 'Tab', shiftKey: true });
    expect(screen.getByRole('menuitem', { name: 'Sign Out' })).toHaveFocus();
    expect(screen.getAllByText('Pricing analyst').some((element) => element.classList.contains('layout-role-badge'))).toBe(true);

    const themeToggle = screen.getByRole('button', { name: 'Toggle theme' });
    expect(themeToggle).toHaveClass('layout-theme-toggle');
    expect(themeToggle).toHaveAttribute('title', 'Toggle theme');
    expect(themeToggle).toHaveAttribute('data-theme', 'dark');
    expect(themeToggle.querySelector('.layout-theme-toggle__track')).toBeInTheDocument();
    fireEvent.click(themeToggle);
    expect(themeStorageKey).toBe('wcpe:design-system-theme');
    expect(window.localStorage.getItem(themeStorageKey)).toBe('light');
    expect(window.localStorage.getItem('wcpe:layout-theme')).toBeNull();
    expect(toggle).toHaveBeenCalledTimes(1);
  });

  it('keeps profile dropdown above base content layer', () => {
    expect(zIndex.base).toBe(0);
    expect(zIndex.sticky).toBe(100);
    expect(zIndex.drawerBackdrop).toBe(900);
    expect(zIndex.drawer).toBe(950);
    expect(zIndex.dropdown).toBe(1000);
    expect(zIndex.dropdown).toBeGreaterThan(zIndex.drawer);
  });

  it('supports arrow-key movement through nav rail links', () => {
    installMatchMedia(false);
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 1200 });
    renderShell(
      <Shell activeModuleId="quote" activeRunId="run-123" breadcrumb="Quote" modules={modules} notifications={[]} onThemeToggle={vi.fn()} theme="dark" user={{ name: 'Alex Rivera', role: 'Loan officer' }}>
        <section>Screen content</section>
      </Shell>,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Open navigation menu' }));
    const firstLink = screen.getByRole('link', { name: /Pipeline Intake/ });
    firstLink.focus();
    fireEvent.keyDown(screen.getByRole('dialog', { name: 'Primary navigation drawer' }), { key: 'ArrowDown' });
    expect(screen.getByRole('link', { name: /New quote/ })).toHaveFocus();
  });
});
