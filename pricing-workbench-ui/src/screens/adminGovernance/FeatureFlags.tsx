import type { FeatureFlagSummary } from '../../lib/api/adminGovernance';
import { ChipList } from './shared';

export function FeatureFlags({ flags }: { flags: FeatureFlagSummary[] }) {
  return (
    <section className="panel" aria-labelledby="feature-flags-heading">
      <h2 id="feature-flags-heading">Feature Flags</h2>
      <div className="quote-table" role="table" aria-label="Feature flags">
        <div role="row" className="quote-table__row quote-table__row--head">
          <span role="columnheader">Flag ID</span>
          <span role="columnheader">Environment target</span>
          <span role="columnheader">Enabled</span>
          <span role="columnheader">Activation disabled</span>
          <span role="columnheader">Emergency toggle gate</span>
        </div>
        {flags.map((flag) => (
          <div key={flag.flagId} role="row" className="quote-table__row">
            <span role="cell">{flag.flagId}</span>
            <span role="cell">{flag.environmentTarget}</span>
            <span role="cell">{flag.enabled ? 'yes' : 'no'}</span>
            <span role="cell">{flag.activationDisabled ? 'yes' : 'no'}<ChipList label={`${flag.flagId} unresolved flags`} values={flag.unresolvedFlags} /></span>
            <span role="cell"><strong>Feature flag activation blocked</strong><br />{flag.emergencyToggleGate}<details><summary>Toggle Emergency</summary><button type="button" disabled>Emergency toggle requires approval evidence</button></details></span>
          </div>
        ))}
      </div>
    </section>
  );
}
