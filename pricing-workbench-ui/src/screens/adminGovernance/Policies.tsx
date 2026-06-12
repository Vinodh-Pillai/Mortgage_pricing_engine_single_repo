import type { PolicyVersionSummary } from '../../lib/api/adminGovernance';
import { ChipList } from './shared';

export function Policies({ policies }: { policies: PolicyVersionSummary[] }) {
  return (
    <section className="panel" aria-labelledby="policy-heading">
      <h2 id="policy-heading">Policies</h2>
      <div className="quote-table" role="table" aria-label="Policy registry">
        <div role="row" className="quote-table__row quote-table__row--head">
          <span role="columnheader">Version ID</span>
          <span role="columnheader">Owner</span>
          <span role="columnheader">Status</span>
          <span role="columnheader">Environment mapping</span>
          <span role="columnheader">Parent</span>
          <span role="columnheader">Hash and diff</span>
        </div>
        {policies.map((policy) => (
          <div key={policy.versionId} role="row" className="quote-table__row">
            <span role="cell">{policy.versionId}</span>
            <span role="cell">{policy.owner}</span>
            <span role="cell">{policy.status}</span>
            <span role="cell">{policy.environmentMapping}</span>
            <span role="cell">{policy.parentVersionId}</span>
            <span role="cell"><code>{policy.hashSignature}</code><details><summary>View Diff</summary><ChipList label={`${policy.versionId} diff impacts`} values={policy.diffImpacts} /></details><button type="button" disabled>Promote</button></span>
          </div>
        ))}
      </div>
    </section>
  );
}
