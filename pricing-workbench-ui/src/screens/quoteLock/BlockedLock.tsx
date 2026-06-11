import type { LockWorkflowView } from '../../lib/api/quoteRuns';

export function BlockedLock({ workflow, onReturn }: { workflow: LockWorkflowView; onReturn: () => void }) {
  return (
    <main className="quote-lock-screen" aria-labelledby="blocked-lock-heading">
      <section className="panel" role="alert" aria-labelledby="blocked-lock-heading">
        <h1 id="blocked-lock-heading">Lock workflow blocked</h1>
        <p>{workflow.lockDisabledReason ?? 'Backend lock workflow is unavailable.'}</p>
        <ul>
          {workflow.blockers.map((blocker) => (
            <li key={blocker.code}>
              <strong>{blocker.code}</strong>: {blocker.message}
              <p>{blocker.remediation}</p>
              <code>{blocker.sourceRef}</code>
            </li>
          ))}
        </ul>
        <button type="button" onClick={onReturn}>Return to Offers</button>
      </section>
    </main>
  );
}
