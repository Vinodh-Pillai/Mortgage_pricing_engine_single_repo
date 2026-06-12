import type { DynamicRuleEvidenceSnapshot } from '../../lib/api/adminGovernance';
import { ChipList, RuleEvidenceTable } from './shared';

export function DynamicRuleEvidence({ evidence, events }: { evidence: DynamicRuleEvidenceSnapshot; events: string[] }) {
  return (
    <section className="panel" aria-labelledby="dynamic-rule-evidence-heading">
      <h2 id="dynamic-rule-evidence-heading">Dynamic rule evidence</h2>
      <dl className="status-grid">
        <dt>Precision metadata</dt><dd>{evidence.precisionMetadataRef}</dd>
        <dt>Replay hash</dt><dd>{evidence.replayHashRef}</dd>
      </dl>
      <RuleEvidenceTable label="Governance matched rules" rows={evidence.matchedRules} />
      <RuleEvidenceTable label="Governance skipped rules" rows={evidence.skippedRules} />
      <ChipList label="Action outputs" values={evidence.actionOutputs} />
      <ChipList label="Fact references" values={evidence.factRefs} />
      <ChipList label="Admin governance events" values={events} />
    </section>
  );
}
