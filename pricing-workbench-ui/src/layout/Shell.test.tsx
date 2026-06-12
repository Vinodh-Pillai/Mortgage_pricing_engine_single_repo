import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { getBreakpointForWidth } from './hooks/useBreakpoint';
import { buildNavigationTree } from './navigation';
import { Shell } from './Shell';
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
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
    window.localStorage.removeItem('wcpe:layout-shell:nav-rail-collapsed');
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
    expect(items).toEqual(expect.arrayContaining([expect.objectContaining({ label: 'Quote workspace', route: '/quote/run-123/offers', group: 'Quote' })]));
    expect(items).toEqual(expect.arrayContaining([expect.objectContaining({ label: 'Operations dashboard', group: 'Operations', badgeCount: 1 })]));
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
    expect(screen.getByRole('navigation', { name: 'Main navigation' })).toBeInTheDocument();
    expect(screen.getByRole('main')).toHaveTextContent('Screen content');
    expect(screen.getByRole('contentinfo')).toHaveTextContent('Responsive shell ready');
    expect(screen.getByRole('link', { name: 'Quote workspace' })).toHaveClass('layout-nav__link--active');
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
    fireEvent.click(menuButton);
    expect(screen.getByRole('dialog', { name: 'Primary navigation drawer' })).toBeInTheDocument();
    fireEvent.keyDown(screen.getByRole('dialog', { name: 'Primary navigation drawer' }), { key: 'Escape' });
    expect(screen.queryByRole('dialog', { name: 'Primary navigation drawer' })).not.toBeInTheDocument();
    expect(menuButton).toHaveFocus();
  });

  it('toggles and persists rail collapse without changing desktop/tablet rail mode', () => {
    installMatchMedia(false);
    window.localStorage.removeItem('wcpe:layout-shell:nav-rail-collapsed');
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 1199 });
    renderShell(
      <Shell activeModuleId="quote" activeRunId="run-123" breadcrumb="Quote" modules={modules} notifications={[]} onThemeToggle={vi.fn()} theme="dark" user={{ name: 'Alex Rivera', role: 'Pricing analyst' }}>
        <section>Screen content</section>
      </Shell>,
    );

    const shell = document.querySelector('.layout-shell');
    expect(shell).toHaveAttribute('data-breakpoint-mode', 'rail');
    expect(shell).toHaveAttribute('data-nav-collapsed', 'false');
    fireEvent.click(screen.getByRole('button', { name: 'Collapse' }));
    expect(shell).toHaveClass('layout-shell--nav-collapsed');
    expect(window.localStorage.getItem('wcpe:layout-shell:nav-rail-collapsed')).toBe('true');
    fireEvent.click(screen.getByRole('button', { name: 'Expand' }));
    expect(shell).not.toHaveClass('layout-shell--nav-collapsed');
    expect(window.localStorage.getItem('wcpe:layout-shell:nav-rail-collapsed')).toBe('false');
  });

  it('opens persona menu with role badge and persists theme toggles', () => {
    installMatchMedia(false);
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 1200 });
    const toggle = vi.fn();
    renderShell(
      <Shell activeModuleId="quote" breadcrumb="Quote" modules={modules} notifications={[{ id: 'n1', label: 'Review alert' }]} onThemeToggle={toggle} theme="dark" user={{ name: 'Alex Rivera', role: 'Pricing analyst' }}>
        <section>Screen content</section>
      </Shell>,
    );

    fireEvent.click(screen.getByRole('button', { name: 'User menu for Alex Rivera' }));
    expect(screen.getByRole('menu', { name: 'User menu for Alex Rivera' })).toHaveTextContent('Switch persona (dev)');
    expect(screen.getAllByText('Pricing analyst').some((element) => element.classList.contains('layout-role-badge'))).toBe(true);

    fireEvent.click(screen.getByRole('switch', { name: 'Toggle theme' }));
    expect(window.localStorage.getItem('wcpe:layout-theme')).toBe('light');
    expect(toggle).toHaveBeenCalledTimes(1);
  });

  it('supports arrow-key movement through nav rail links', () => {
    installMatchMedia(false);
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 1200 });
    renderShell(
      <Shell activeModuleId="quote" activeRunId="run-123" breadcrumb="Quote" modules={modules} notifications={[]} onThemeToggle={vi.fn()} theme="dark" user={{ name: 'Alex Rivera', role: 'Pricing analyst' }}>
        <section>Screen content</section>
      </Shell>,
    );

    const firstLink = screen.getByRole('link', { name: 'Quote workspace' });
    firstLink.focus();
    fireEvent.keyDown(screen.getByRole('navigation', { name: 'Main navigation' }), { key: 'ArrowDown' });
    expect(screen.getByRole('link', { name: 'Operations dashboard' })).toHaveFocus();
  });
});
