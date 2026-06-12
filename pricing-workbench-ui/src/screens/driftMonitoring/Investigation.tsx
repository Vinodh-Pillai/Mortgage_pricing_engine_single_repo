import { ChipList } from '../mlAdvisoryInsights/shared';
import type { DriftAlertView, DriftInvestigationView } from './types';

export function Investigation({ alerts, investigation }: { alerts: DriftAlertView[]; investigation: DriftInvestigationView }) {
  return (
    <div role="tabpanel" aria-label="Investigation">
      <div className="panel-heading-row">
        <div>
          <h3>Investigation canvas</h3>
          <p className="field-help">Select alert or continue a manual investigation without an alert. Export includes charts, data, alert history, and replay hash evidence refs.</p>
        </div>
        <button type="button" onClick={() => window.dispatchEvent(new CustomEvent('drift-export', { detail: 'investigation' }))}>Export Investigation</button>
      </div>
      <label htmlFor="investigation-alert">Alert context</label>
      <select id="investigation-alert" defaultValue={investigation.selectedAlertId ?? 'manual'}>
        <option value="manual">Manual investigation</option>
        {alerts.map((alert) => <option key={alert.alertId} value={alert.alertId}>{alert.alertId} · {alert.severity}</option>)}
      </select>
      <div className="module-rail__grid" role="list" aria-label="Investigation workspace">
        <article className="module-card" role="listitem"><strong>Root Cause Analysis</strong><ChipList label="5 Whys and fishbone notes" values={investigation.rootCauseNotes} /></article>
        <article className="module-card" role="listitem"><strong>Data Quality Checks</strong><ChipList label="Data quality checks" values={investigation.dataQualityChecks} /></article>
        <article className="module-card" role="listitem"><strong>Feature Attribution</strong><ChipList label="Feature attribution" values={investigation.featureAttribution} /></article>
      </div>
      <div className="quote-table" role="table" aria-label="Remediation plan">
        <div role="row" className="quote-table__row quote-table__row--head"><span role="columnheader">Action</span><span role="columnheader">Owner</span><span role="columnheader">Due Date</span><span role="columnheader">Status</span></div>
        {investigation.remediationPlan.map((item) => <div key={item.action} role="row" className="quote-table__row"><span role="cell">{item.action}</span><span role="cell">{item.owner}</span><span role="cell">{item.dueDate}</span><span role="cell">{item.status}</span></div>)}
      </div>
      <dl><dt>Export evidence</dt><dd><code>{investigation.exportEvidenceRef}</code></dd><dt>Replay hash</dt><dd><code>{investigation.replayHashRef}</code></dd></dl>
    </div>
  );
}
