import type { LockWorkflowHistoryEvent } from '../../lib/api/quoteRuns';

function historyCsv(events: LockWorkflowHistoryEvent[]) {
  const rows = [
    ['eventId', 'eventType', 'timestamp', 'actor', 'terms', 'approvalRef', 'auditRef'],
    ...events.map((event) => [event.eventId, event.eventType, event.timestamp, event.actor, event.terms, event.approvalRef ?? '', event.auditRef]),
  ];

  return rows.map((row) => row.map((cell) => `"${String(cell).replace(/"/g, '""')}"`).join(',')).join('\n');
}

function exportHistoryCsv(events: LockWorkflowHistoryEvent[]) {
  const csv = historyCsv(events);
  const href = `data:text/csv;charset=utf-8,${encodeURIComponent(csv)}`;
  const link = document.createElement('a');
  link.href = href;
  link.download = 'lock-history.csv';
  link.click();
}

export function LockHistory({ events }: { events: LockWorkflowHistoryEvent[] }) {
  return (
    <section className="panel" aria-labelledby="lock-history-heading">
      <h2 id="lock-history-heading">Lock History</h2>
      <ol>
        {events.map((event) => (
          <li key={event.eventId}>
            <strong>{event.eventType}</strong> at {new Date(event.timestamp).toLocaleString()} by {event.actor}
            <p>{event.terms}</p>
            <p>Approval: {event.approvalRef ?? 'N/A'} | Audit: <code>{event.auditRef}</code></p>
          </li>
        ))}
      </ol>
      <button type="button" onClick={() => exportHistoryCsv(events)}>Export History CSV</button>
    </section>
  );
}

export { historyCsv };
