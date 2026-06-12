import type { IncidentReviewSummary } from '../../lib/api/adminGovernance';

export function Incidents({ incidents }: { incidents: IncidentReviewSummary[] }) {
  return (
    <section className="panel" aria-labelledby="incidents-heading">
      <h2 id="incidents-heading">Incidents</h2>
      <div className="quote-table" role="table" aria-label="Incidents">
        <div role="row" className="quote-table__row quote-table__row--head"><span role="columnheader">Incident ID</span><span role="columnheader">Status</span><span role="columnheader">Rollback target</span><span role="columnheader">RCA linked</span><span role="columnheader">Corrective action</span><span role="columnheader">Closure gate</span></div>
        {incidents.map((incident) => <div key={incident.incidentId} role="row" className="quote-table__row"><span role="cell">{incident.incidentId}</span><span role="cell">{incident.status}</span><span role="cell">{incident.rollbackTarget}</span><span role="cell">{incident.rcaLinked ? 'true' : 'false'}<a href={`#${incident.incidentId}-rca`}>View RCA</a></span><span role="cell">{incident.correctiveActionDone ? 'done' : 'not done'}</span><span role="cell">{incident.closureGate}<button type="button" disabled={incident.closeDisabled}>Close incident</button></span></div>)}
      </div>
    </section>
  );
}
