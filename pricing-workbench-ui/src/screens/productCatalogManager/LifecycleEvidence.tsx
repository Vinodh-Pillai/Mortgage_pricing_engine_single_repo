import type { ProductCatalogLifecycle } from '../../lib/api/products';
import { catalogDisplayText, ChipList } from './CatalogSections';

type LifecycleEvidenceProps = {
  dependencyStatus: string;
  lifecycle: ProductCatalogLifecycle;
  events: string[];
};

export function LifecycleEvidence({ dependencyStatus, lifecycle, events }: LifecycleEvidenceProps) {
  return (
    <section className="panel" aria-labelledby="catalog-lifecycle-heading">
      <div className="panel-heading-row">
        <div>
          <p className="eyebrow">Lifecycle and evidence</p>
          <h2 id="catalog-lifecycle-heading">Approval, publish, rollback, snapshots, and audit</h2>
        </div>
      </div>
      <div className="banner banner--blocked" role="alert">
        <strong>{catalogDisplayText(lifecycle.state)}</strong>
        <span>{catalogDisplayText(lifecycle.blocker)}</span>
      </div>
      <dl className="status-grid">
        <dt>Actions disabled</dt><dd>{lifecycle.actionsDisabled ? 'Yes' : 'No'}</dd>
        <dt>Setup status</dt><dd>{catalogDisplayText(dependencyStatus)}</dd>
      </dl>
      <ChipList label="Lifecycle Actions" values={lifecycle.actions.map(catalogDisplayText)} />
      <ChipList label="Snapshot and Event References" values={lifecycle.snapshotRefs.map(catalogDisplayText)} />
      <ChipList label="Review and processing references" values={lifecycle.auditRefs.map(catalogDisplayText)} />
      <ChipList label="Catalog manager events" values={events.map(catalogDisplayText)} />
      <a href="/admin/governance#validation-runs">Validation Runs</a>
    </section>
  );
}
