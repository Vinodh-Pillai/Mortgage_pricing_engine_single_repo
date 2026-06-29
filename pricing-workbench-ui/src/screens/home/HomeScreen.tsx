import { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { getRecentActivity, seedRecentActivity, type ActivityRecord } from '../../lib/activity/activity';
import { hasPermission, personaForRole, quickActions, QuickActions, type HomeRole } from './QuickActions';
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
  const persona = personaForRole(role);

  const recentActivity = useMemo(() => {
    if (initialActivity) seedRecentActivity(userId, initialActivity);
    return getRecentActivity(userId, 5);
  }, [initialActivity, userId]);
  const widgets = widgetsForRole(role);
  const visualState = persona ? (recentActivity.length ? 'ready' : 'empty') : 'role-metadata-unavailable';

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
          {recentActivity.length > 0 ? <><dt>Recent</dt><dd>{recentActivity.length}</dd></> : null}
          <dt>Role</dt><dd>{formatRole(role)}</dd>
        </dl>
      </header>
      {!persona ? (
        <div className="home-card" role="alert" aria-label="Role metadata unavailable">
          <p className="eyebrow">Access control</p>
          <h2>Role metadata unavailable</h2>
          <p>Persona and scope metadata could not be resolved. The workbench is failing safe by hiding restricted actions until the session can be refreshed.</p>
        </div>
      ) : null}

      <section className="home-layout" aria-label="Quick launch and activity dashboard">
        <div className="home-main-column">
          <QuickActions role={role} onNavigate={navigateTo} />
          <AdminAuditNotice role={role} />
          <OperationsJobStatePanel role={role} onNavigate={navigateTo} />
          <RecentActivity records={recentActivity} onNavigate={navigateTo} onStartPipeline={() => navigateTo('/pipeline')} />
        </div>
        <aside className="home-widget-column" aria-label="Role widgets">
          {widgets.map((widget) => {
            const visibleItems = widget.items.filter((item) => item.value !== '0');
            return (
            <section className="home-card home-widget" key={widget.title} aria-labelledby={`${widget.id}-heading`}>
              <p className="eyebrow">{widget.kicker}</p>
              <h2 id={`${widget.id}-heading`}>{widget.title}</h2>
              {visibleItems.length > 0 ? (
                <ul>
                  {visibleItems.map((item) => <li key={item.label}><span>{item.label}</span><strong>{item.value}</strong></li>)}
                </ul>
              ) : <p className="home-widget__empty">{widget.emptyText}</p>}
            </section>
            );
          })}
        </aside>
      </section>
    </main>
  );
}

function AdminAuditNotice({ role }: { role: HomeRole | string }) {
  if (normalizeHomeRole(role) !== 'admin') return null;

  return (
    <section className="home-card" aria-labelledby="admin-audit-warning-heading">
      <p className="eyebrow">Audit warning</p>
      <h2 id="admin-audit-warning-heading">Tenant, product, and access changes are audited</h2>
      <p>
        Administrative tenant settings, product catalog setup, and user access changes must be reviewed with audit evidence before restricted changes are activated.
      </p>
    </section>
  );
}

function OperationsJobStatePanel({ role, onNavigate }: { role: HomeRole | string; onNavigate: (route: string) => void }) {
  const remediationAction = quickActions.find((action) => action.id === 'ops-remediation');
  const rateSheetAction = quickActions.find((action) => action.id === 'rate-sheet-upload');
  if (!remediationAction || !rateSheetAction || !hasPermission(role, remediationAction)) return null;

  return (
    <section className="home-card" aria-labelledby="operations-job-state-heading">
      <p className="eyebrow">Operations job state</p>
      <h2 id="operations-job-state-heading">Async quote callbacks and ratesheet jobs</h2>
      <ul>
        <li><span>Async quote callback queue</span><strong>Review required</strong></li>
        <li><span>Ratesheet import job</span><strong>Ready for retry</strong></li>
      </ul>
      <div className="quick-action-grid" aria-label="Allowed operations remediation actions">
        <button className="quick-action-card" type="button" onClick={() => onNavigate(remediationAction.route)}>
          <span className="quick-action-icon" aria-hidden="true">{remediationAction.icon}</span>
          <span>Review callback exceptions</span>
        </button>
        {hasPermission(role, rateSheetAction) ? (
          <button className="quick-action-card" type="button" onClick={() => onNavigate(rateSheetAction.route)}>
            <span className="quick-action-icon" aria-hidden="true">{rateSheetAction.icon}</span>
            <span>Retry ratesheet job</span>
          </button>
        ) : null}
      </div>
    </section>
  );
}

function widgetsForRole(role: HomeRole | string) {
  if (!personaForRole(role)) {
    return [{ id: 'role-metadata-unavailable', kicker: 'Access control', title: 'Recover session', emptyText: 'Refresh the session to restore role-specific actions.', items: [{ label: 'Restricted actions', value: 'Hidden' }, { label: 'Next step', value: 'Refresh session' }] }];
  }
  const normalized = normalizeHomeRole(role);
  if (normalized === 'loan_officer' || normalized === 'borrower') {
    return [{ id: 'my-pipeline', kicker: 'Pipeline', title: 'My Pipeline', emptyText: 'No active pipeline items need attention.', items: [{ label: 'Active', value: '0' }, { label: 'Pending lock', value: '0' }, { label: 'Expiring soon', value: '0' }] }];
  }
  if (normalized === 'ops' || normalized === 'operations_lead') {
    return [{ id: 'operations', kicker: 'Operations', title: 'Queues', emptyText: 'No operations queues need attention.', items: [{ label: 'Lock queue', value: '0' }, { label: 'Exception queue', value: '0' }, { label: 'Partner alerts', value: '0' }] }];
  }
  if (normalized === 'compliance' || normalized === 'governance' || normalized === 'governance_reviewer') {
    return [{ id: 'governance', kicker: 'Governance', title: 'Reviews', emptyText: 'No governance reviews need attention.', items: [{ label: 'Pending reviews', value: '0' }, { label: 'Audit flags', value: '0' }] }];
  }
  if (normalized === 'admin') {
    return [{ id: 'admin', kicker: 'Admin', title: 'System', emptyText: 'System status is available after tenant context loads.', items: [{ label: 'System health', value: 'Ready' }, { label: 'User activity', value: '0' }, { label: 'Tenant status', value: 'Ready' }] }];
  }
  if (normalized === 'partner_manager') {
    return [{ id: 'partners', kicker: 'Partners', title: 'Partner Work', emptyText: 'No partner work needs attention.', items: [{ label: 'Quote queue', value: '0' }, { label: 'Integration health', value: 'Ready' }] }];
  }
  return [{ id: 'pricing', kicker: 'Pricing', title: 'Pricing Desk', emptyText: 'No pricing alerts need attention.', items: [{ label: 'Margin alerts', value: '0' }, { label: 'Rate notifications', value: '0' }] }];
}

function normalizeHomeRole(role: HomeRole | string) {
  return String(role).trim().toLowerCase().replace(/[\s-]+/g, '_');
}

function formatRole(role: HomeRole | string) {
  return String(role).replace(/[_-]+/g, ' ').replace(/\b\w/g, (letter) => letter.toUpperCase());
}
