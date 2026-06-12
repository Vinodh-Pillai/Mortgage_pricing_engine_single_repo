import { useMemo, useState } from 'react';
import type { OverrideLedgerEntry } from '../../lib/api/adminGovernance';

export function OverrideLedger({ entries }: { entries: OverrideLedgerEntry[] }) {
  const [actorFilter, setActorFilter] = useState('');
  const [fieldFilter, setFieldFilter] = useState('');
  const [approvalOnly, setApprovalOnly] = useState(false);
  const filteredEntries = useMemo(() => entries.filter((entry) => {
    const actorMatches = entry.actor.toLowerCase().includes(actorFilter.toLowerCase());
    const fieldMatches = entry.fieldPath.toLowerCase().includes(fieldFilter.toLowerCase());
    const approvalMatches = !approvalOnly || entry.approvalRequired;
    return actorMatches && fieldMatches && approvalMatches;
  }), [actorFilter, approvalOnly, entries, fieldFilter]);

  return (
    <section className="panel" aria-labelledby="override-ledger-heading">
      <h2 id="override-ledger-heading">Override Ledger</h2>
      <div className="filter-row" aria-label="Override ledger filters">
        <label>Actor <input value={actorFilter} onChange={(event) => setActorFilter(event.target.value)} /></label>
        <label>Field path <input value={fieldFilter} onChange={(event) => setFieldFilter(event.target.value)} /></label>
        <label><input type="checkbox" checked={approvalOnly} onChange={(event) => setApprovalOnly(event.target.checked)} /> Approval required only</label>
      </div>
      <div className="quote-table" role="table" aria-label="Change audit history">
        <div role="row" className="quote-table__row quote-table__row--head"><span role="columnheader">Ledger ID</span><span role="columnheader">Actor</span><span role="columnheader">Timestamp</span><span role="columnheader">Field path</span><span role="columnheader">Old/new value</span><span role="columnheader">Policy and audit</span></div>
        {filteredEntries.map((entry) => <div key={entry.ledgerId} role="row" className="quote-table__row"><span role="cell">{entry.ledgerId}</span><span role="cell">{entry.actor}</span><span role="cell">{entry.timestamp}</span><span role="cell">{entry.fieldPath}</span><span role="cell"><code>{entry.oldValue}</code> → <code>{entry.newValue}</code><br />Reason: {entry.reason}</span><span role="cell">{entry.policyRef}<br />Approval required: {entry.approvalRequired ? 'yes' : 'no'}<br />{entry.auditRef}</span></div>)}
      </div>
    </section>
  );
}
