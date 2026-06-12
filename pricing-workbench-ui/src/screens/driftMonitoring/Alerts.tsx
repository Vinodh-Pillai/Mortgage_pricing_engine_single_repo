import { useState } from 'react';
import { ChipList } from '../mlAdvisoryInsights/shared';
import type { DriftAlertView } from './types';

export function Alerts({ alerts }: { alerts: DriftAlertView[] }) {
  const [selectedAlert, setSelectedAlert] = useState<DriftAlertView | null>(null);
  return (
    <div role="tabpanel" aria-label="Alerts">
      <h3>Alerts</h3>
      <p className="field-help">Escalation: UNACKNOWLEDGED &gt; 15 min escalates to team lead; UNRESOLVED &gt; 1 hour escalates to manager; CRITICAL unacknowledged &gt; 5 min pages on-call.</p>
      <div className="quote-table" role="table" aria-label="Drift alerts">
        <div role="row" className="quote-table__row quote-table__row--head"><span role="columnheader">Alert ID</span><span role="columnheader">Rule</span><span role="columnheader">Severity</span><span role="columnheader">Model Version</span><span role="columnheader">Feature/Metric</span><span role="columnheader">Triggered At</span><span role="columnheader">Status</span><span role="columnheader">Owner</span><span role="columnheader">Actions</span></div>
        {alerts.map((alert) => <div key={alert.alertId} role="row" className="quote-table__row"><span role="cell"><strong>{alert.alertId}</strong></span><span role="cell">{alert.rule}</span><span role="cell">{alert.severity}</span><span role="cell">{alert.modelVersion}</span><span role="cell">{alert.metric}</span><span role="cell">{alert.triggeredAt}</span><span role="cell">{alert.status}</span><span role="cell">{alert.owner}</span><span role="cell"><button type="button">ACKNOWLEDGE</button> <button type="button" onClick={() => setSelectedAlert(alert)}>INVESTIGATE</button> <button type="button">RESOLVE</button> <button type="button">SUPPRESS</button> <button type="button" onClick={() => setSelectedAlert(alert)}>View Alert Detail</button></span></div>)}
      </div>
      {selectedAlert ? <div role="dialog" aria-modal="true" aria-labelledby="alert-detail-heading" className="panel"><h3 id="alert-detail-heading">Alert detail {selectedAlert.alertId}</h3><dl><dt>Rule</dt><dd>{selectedAlert.rule}</dd><dt>Threshold</dt><dd>{selectedAlert.threshold}</dd><dt>Current value</dt><dd>{selectedAlert.currentValue}</dd></dl><ChipList label="Alert history" values={selectedAlert.history} /><button type="button" onClick={() => setSelectedAlert(null)}>Close</button></div> : null}
    </div>
  );
}
