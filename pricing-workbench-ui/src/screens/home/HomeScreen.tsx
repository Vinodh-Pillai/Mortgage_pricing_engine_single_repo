import { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { getRecentActivity, seedRecentActivity, type ActivityRecord } from '../../lib/activity/activity';
import { QuickActions, type HomeRole } from './QuickActions';
import { RecentActivity } from './RecentActivity';
import './HomeScreen.css';

type HomeScreenProps = {
  userId?: string;
  role?: HomeRole | string;
  initialActivity?: ActivityRecord[];
  onNavigate?: (route: string) => void;
};

const defaultUserId = 'local-home-user';
const homeEvidenceTarget = '.local-harness/evidence/PII-26-S02/home-screen.json';

export function HomeScreen({ userId = defaultUserId, role = 'pricing_analyst', initialActivity, onNavigate }: HomeScreenProps) {
  const navigate = useNavigate();

  const recentActivity = useMemo(() => {
    if (initialActivity) seedRecentActivity(userId, initialActivity);
    return getRecentActivity(userId, 5);
  }, [initialActivity, userId]);
  const widgets = widgetsForRole(role);
  const visualState = recentActivity.length ? 'ready' : 'empty';

  const navigateTo = (route: string) => {
    onNavigate?.(route);
    if (!onNavigate) navigate(route);
  };

  return (
    <main className="home-screen" aria-labelledby="home-screen-title" data-evidence-target={homeEvidenceTarget} data-visual-state={visualState}>
      <header className="home-header">
        <div>
          <p className="eyebrow">Home</p>
          <h1 id="home-screen-title">Today&apos;s work</h1>
        </div>
        <dl className="home-summary" aria-label="Home screen state">
          <dt>Recent</dt><dd>{recentActivity.length}</dd>
          <dt>Role</dt><dd>{formatRole(role)}</dd>
        </dl>
      </header>

      <section className="home-layout" aria-label="Quick launch and activity dashboard">
        <div className="home-main-column">
          <QuickActions role={role} onNavigate={navigateTo} />
          <RecentActivity records={recentActivity} onNavigate={navigateTo} onStartPipeline={() => navigateTo('/pipeline')} />
        </div>
        <aside className="home-widget-column" aria-label="Role widgets">
          {widgets.map((widget) => (
            <section className="home-card home-widget" key={widget.title} aria-labelledby={`${widget.id}-heading`}>
              <p className="eyebrow">{widget.kicker}</p>
              <h2 id={`${widget.id}-heading`}>{widget.title}</h2>
              <ul>
                {widget.items.map((item) => <li key={item.label}><span>{item.label}</span><strong>{item.value}</strong></li>)}
              </ul>
            </section>
          ))}
        </aside>
      </section>
    </main>
  );
}

function widgetsForRole(role: HomeRole | string) {
  const normalized = String(role).trim().toLowerCase().replace(/[\s-]+/g, '_');
  if (normalized === 'loan_officer' || normalized === 'borrower') {
    return [{ id: 'my-pipeline', kicker: 'Pipeline', title: 'My Pipeline', items: [{ label: 'Active', value: '0' }, { label: 'Pending lock', value: '0' }, { label: 'Expiring soon', value: '0' }] }];
  }
  if (normalized === 'ops' || normalized === 'operations_lead') {
    return [{ id: 'operations', kicker: 'Operations', title: 'Queues', items: [{ label: 'Lock queue', value: '0' }, { label: 'Exception queue', value: '0' }, { label: 'Partner alerts', value: '0' }] }];
  }
  if (normalized === 'compliance' || normalized === 'governance' || normalized === 'governance_reviewer') {
    return [{ id: 'governance', kicker: 'Governance', title: 'Reviews', items: [{ label: 'Pending reviews', value: '0' }, { label: 'Audit flags', value: '0' }] }];
  }
  if (normalized === 'admin') {
    return [{ id: 'admin', kicker: 'Admin', title: 'System', items: [{ label: 'System health', value: 'Ready' }, { label: 'User activity', value: '0' }, { label: 'Tenant status', value: 'Ready' }] }];
  }
  if (normalized === 'partner_manager') {
    return [{ id: 'partners', kicker: 'Partners', title: 'Partner Work', items: [{ label: 'Quote queue', value: '0' }, { label: 'Integration health', value: 'Ready' }] }];
  }
  return [{ id: 'pricing', kicker: 'Pricing', title: 'Pricing Desk', items: [{ label: 'Margin alerts', value: '0' }, { label: 'Rate notifications', value: '0' }] }];
}

function formatRole(role: HomeRole | string) {
  return String(role).replace(/[_-]+/g, ' ').replace(/\b\w/g, (letter) => letter.toUpperCase());
}
