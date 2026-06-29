import type { LockWorkflowView } from '../../lib/api/quoteRuns';
import { dateTimeText, valueText } from './lockWorkflowUtils';

export function LockTermsPanel({ workflow, disabled, lockActionLabel = 'Lock This Rate', onLock }: { workflow: LockWorkflowView; disabled: boolean; lockActionLabel?: string; onLock: () => void }) {
  const { terms } = workflow;
  return (
    <section className="panel" aria-labelledby="lock-terms-heading">
      <h2 id="lock-terms-heading">Lock Terms</h2>
      <dl className="status-grid">
        <dt>Selected offer</dt><dd><code>{workflow.selectedOfferId}</code></dd>
        <dt>Product</dt><dd>{terms.productLabel}</dd>
        <dt>Investor</dt><dd>{terms.investor}</dd>
        <dt>Channel</dt><dd>{terms.channel}</dd>
        <dt>Note rate</dt><dd>{terms.noteRate}</dd>
        <dt>Final price</dt><dd>{terms.finalPriceBps} bps</dd>
        <dt>Lock period</dt><dd>{terms.lockPeriodDays} days</dd>
        <dt>Expiration</dt><dd>{dateTimeText(terms.expiresAt)}</dd>
        <dt>Investor confirmation</dt><dd>{terms.investorConfirmationRequired ? 'Required by backend' : 'Not required by backend'}</dd>
        <dt>Waterfall ref</dt><dd><code>{terms.waterfallRef}</code></dd>
      </dl>
      <RefList label="Adjustment refs" values={terms.adjustmentRefs} />
      <RefList label="Margin refs" values={terms.marginRefs} />
      {workflow.blockers.length ? <RefList label="Backend lock blockers" values={workflow.blockers.map((blocker) => `${blocker.code}: ${blocker.message}`)} /> : null}
      <button type="button" disabled={disabled} onClick={onLock}>{lockActionLabel}</button>
    </section>
  );
}

function RefList({ label, values }: { label: string; values: string[] }) {
  return <div className="copyable-ref-list" aria-label={label}><strong>{label}</strong>{values.length ? <ul>{values.map((value) => <li key={value}><code>{value}</code></li>)}</ul> : <p>{valueText(null)}</p>}</div>;
}
