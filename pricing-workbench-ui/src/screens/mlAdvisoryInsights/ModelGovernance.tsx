import type { ModelVersionGovernanceInsight } from '../../lib/api/mlAdvisoryInsights';
import { ChipList, businessFacingText } from './shared';

export function ModelGovernance({ modelVersions, events }: { modelVersions: ModelVersionGovernanceInsight[]; events: string[] }) {
  return (
    <section className="panel" aria-labelledby="ml-governance-heading">
      <h2 id="ml-governance-heading">Model governance grouped by model version</h2>
      <div className="quote-table" role="table" aria-label="Model version governance">
        <div role="row" className="quote-table__row quote-table__row--head">
          <span role="columnheader">Model version</span>
          <span role="columnheader">Drift status</span>
          <span role="columnheader">Alert state</span>
          <span role="columnheader">Feedback loops</span>
          <span role="columnheader">Export evidence</span>
        </div>
        {modelVersions.map((modelVersion) => (
          <details key={modelVersion.modelVersion} className="quote-table__row" role="row">
            <summary role="cell">{modelVersion.modelVersion}</summary>
            <span role="cell"><strong>{modelVersion.driftStatus}</strong></span>
            <span role="cell">{modelVersion.alertState}</span>
            <span role="cell"><ChipList label={`${modelVersion.modelVersion} feedback loops`} values={modelVersion.feedbackLoops} /></span>
            <span role="cell"><ChipList label={`${modelVersion.modelVersion} export evidence`} values={modelVersion.exportEvidenceRefs} /><a href={`/audit/replay?ref=${encodeURIComponent(modelVersion.exportEvidenceRefs[0] ?? modelVersion.modelVersion)}`}>View Export Evidence</a></span>
          </details>
        ))}
      </div>
      <button type="button" disabled aria-describedby="ml-retrain-disabled">Trigger Retrain</button>
      <p id="ml-retrain-disabled" className="field-help">Retraining is a data-science governed action and remains disabled until the API supplies an authorized action contract.</p>
      <ChipList label="ML advisory events" values={events.map(businessFacingText)} />
    </section>
  );
}
