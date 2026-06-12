import type { GovernanceDescriptor } from '../../lib/api/adminGovernance';
import { ChipList, businessFacingText } from './shared';

export function Descriptors({ descriptors }: { descriptors: GovernanceDescriptor[] }) {
  return (
    <section className="panel" aria-labelledby="descriptors-heading">
      <h2 id="descriptors-heading">Descriptors</h2>
      <div className="filter-row" aria-label="Descriptor filters">
        <span>Filter by type and decision quality using backend-supplied labels.</span>
      </div>
      <div className="quote-table" role="table" aria-label="Governance descriptors">
        <div role="row" className="quote-table__row quote-table__row--head">
          <span role="columnheader">Stable ID</span>
          <span role="columnheader">Label</span>
          <span role="columnheader">Type</span>
          <span role="columnheader">Allowed operators</span>
          <span role="columnheader">Value sources</span>
          <span role="columnheader">Decision quality and validation</span>
        </div>
        {descriptors.map((descriptor) => (
          <div key={descriptor.stableId} role="row" className="quote-table__row">
            <span role="cell"><strong>{descriptor.stableId}</strong><br /><code>{descriptor.versionRef}</code></span>
            <span role="cell">{descriptor.label}</span>
            <span role="cell">{businessFacingText(descriptor.type)}</span>
            <span role="cell"><ChipList label={`${descriptor.stableId} allowed operators`} values={descriptor.allowedOperators} /></span>
            <span role="cell"><ChipList label={`${descriptor.stableId} value sources`} values={descriptor.valueSources} /></span>
            <span role="cell"><strong>{descriptor.decisionQualityRequirement}</strong><details><summary>Validation messages detail</summary><ChipList label={`${descriptor.stableId} validation messages`} values={descriptor.validationMessages} /></details></span>
          </div>
        ))}
      </div>
    </section>
  );
}
