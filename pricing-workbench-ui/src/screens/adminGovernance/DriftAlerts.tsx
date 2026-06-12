import type { DriftAlertSummary } from '../../lib/api/adminGovernance';

export function DriftAlerts({ alerts }: { alerts: DriftAlertSummary[] }) {
  return (
    <section className="panel" aria-labelledby="drift-alerts-heading">
      <h2 id="drift-alerts-heading">Drift Alerts</h2>
      <div className="quote-table" role="table" aria-label="Drift alerts">
        <div role="row" className="quote-table__row quote-table__row--head"><span role="columnheader">Alert ID</span><span role="columnheader">Severity</span><span role="columnheader">Environment</span><span role="columnheader">Owner</span><span role="columnheader">Summary</span><span role="columnheader">Acknowledged</span></div>
        {alerts.map((alert) => <div key={alert.alertId} role="row" className="quote-table__row"><span role="cell">{alert.alertId}</span><span role="cell">{alert.severity}</span><span role="cell">{alert.environment}</span><span role="cell">{alert.owner}</span><span role="cell">{alert.summary}</span><span role="cell">{alert.acknowledged ? 'yes' : 'no'}<button type="button" disabled>Acknowledge</button><button type="button" disabled>Investigate</button></span></div>)}
      </div>
    </section>
  );
}
