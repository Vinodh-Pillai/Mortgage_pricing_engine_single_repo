import { MemoryRouter } from 'react-router-dom';
import { getPersonaById } from '../lib/auth/personas';
import { workbenchModules } from '../screens/workbenchShell/WorkbenchShell';
import { buildNavigationTree } from './navigation';
import { Shell } from './Shell';

export default {
  title: 'Layout/Workbench Shell',
};

function NavigationFilteringPanel({ personaId }: { personaId: string }) {
  const persona = getPersonaById(personaId)!;
  const items = buildNavigationTree(workbenchModules, 'run-story', persona);
  return (
    <section className="panel" aria-labelledby={`${persona.role}-nav-story-heading`}>
      <h2 id={`${persona.role}-nav-story-heading`}>Visible modules for {persona.name}</h2>
      <p>{items.length} modules visible for the {persona.role} synthetic persona.</p>
      <ul>
        {items.map((item) => <li key={item.id}><code>{item.route}</code> — {item.label} ({item.group})</li>)}
      </ul>
    </section>
  );
}

export function BorrowerNavigation() {
  return <NavigationFilteringPanel personaId="persona-borrower" />;
}

export function AdminNavigation() {
  return <NavigationFilteringPanel personaId="persona-admin" />;
}

function ShellStory({ width, theme = 'dark', notifications = true }: { width: number; theme?: 'dark' | 'light'; notifications?: boolean }) {
  return (
    <div style={{ width, maxWidth: '100%', border: '1px solid var(--ds-glass-border)' }} data-theme={theme}>
      <MemoryRouter initialEntries={['/quote/run-story/offers']}>
        <Shell
          activeModuleId="quote-offers"
          activeRunId="run-story"
          breadcrumb="Offer comparison"
          modules={workbenchModules.slice(0, 12)}
          notifications={notifications ? [{ id: 'alert-1', label: 'One pricing exception needs review', attentionRequired: true }] : []}
          onThemeToggle={() => undefined}
          theme={theme}
          user={{ name: 'David Chen', role: 'Pricing analyst', avatar: 'DC' }}
        >
          <section className="panel" aria-labelledby="storybook-layout-heading">
            <p className="eyebrow">Storybook baseline</p>
            <h2 id="storybook-layout-heading">Glassy workbench shell</h2>
            <p>Responsive content area, persona header, notification controls, and React Router navigation rail baseline.</p>
          </section>
        </Shell>
      </MemoryRouter>
    </div>
  );
}

export function ShellMobile() {
  return <ShellStory width={390} />;
}

export function ShellTabletCollapsed() {
  return <ShellStory width={900} />;
}

export function ShellDesktop() {
  return <ShellStory width={1280} />;
}

export function ShellWide() {
  return <ShellStory width={1680} theme="light" />;
}
