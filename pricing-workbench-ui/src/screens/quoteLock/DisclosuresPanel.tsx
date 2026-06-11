import type { LockWorkflowDisclosure } from '../../lib/api/quoteRuns';

export function DisclosuresPanel({ disclosures, scrollComplete, checked, signatureName, onScrollComplete, onCheckedChange, onSignatureChange }: { disclosures: LockWorkflowDisclosure[]; scrollComplete: boolean; checked: boolean; signatureName: string; onScrollComplete: (complete: boolean) => void; onCheckedChange: (checked: boolean) => void; onSignatureChange: (name: string) => void }) {
  return (
    <section className="panel" aria-labelledby="disclosures-heading">
      <h2 id="disclosures-heading">Disclosures</h2>
      <div aria-label="Disclosure text" tabIndex={0} onScroll={(event) => {
        const element = event.currentTarget;
        if (element.scrollTop + element.clientHeight >= element.scrollHeight - 2) onScrollComplete(true);
      }} style={{ maxHeight: '12rem', overflow: 'auto', border: '1px solid currentColor', padding: '0.75rem' }}>
        {disclosures.map((disclosure) => (
          <article key={disclosure.disclosureId}>
            <h3>{disclosure.title}</h3>
            <p>{disclosure.text}</p>
            <p><strong>Compliance ref:</strong> <code>{disclosure.complianceRef}</code></p>
          </article>
        ))}
        <p data-testid="disclosure-end">End of disclosures</p>
      </div>
      <p className="trace-badge">Scroll status: {scrollComplete ? 'complete' : 'required'}</p>
      <label>
        <input type="checkbox" checked={checked} disabled={!scrollComplete} onChange={(event) => onCheckedChange(event.currentTarget.checked)} /> I have read and accept the lock disclosures
      </label>
      <label>
        Digital signature
        <input aria-label="Digital signature" value={signatureName} disabled={!scrollComplete || !checked} onChange={(event) => onSignatureChange(event.currentTarget.value)} placeholder="Type signer name" />
      </label>
      <p>Accept Disclosures status: {scrollComplete && checked && signatureName.trim() ? 'complete' : 'incomplete'}</p>
    </section>
  );
}
