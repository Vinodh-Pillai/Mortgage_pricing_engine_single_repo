import type { OpenDecisionGate, ReleaseCandidateReadiness } from '../../lib/api/adminGovernance';
import { ChipList, businessFacingText } from './shared';

export function ReleaseCandidate({ candidate, openDecisions }: { candidate: ReleaseCandidateReadiness; openDecisions: OpenDecisionGate[] }) {
  return (
    <section className="panel" aria-labelledby="release-candidate-heading">
      <h2 id="release-candidate-heading">Release Candidate</h2>
      <dl className="status-grid">
        <dt>Candidate ID</dt><dd>{candidate.candidateId}</dd>
        <dt>Readiness</dt><dd>{candidate.readinessStatus}</dd>
        <dt>Environment target</dt><dd>{candidate.environmentTarget}</dd>
        <dt>Fingerprint</dt><dd>{candidate.releaseFingerprint}</dd>
        <dt>Manifest</dt><dd>{candidate.manifestRef}</dd>
        <dt>Signature</dt><dd>{candidate.signature}</dd>
      </dl>
      <button type="button" disabled={candidate.deployDisabled} aria-describedby="release-blockers">Deploy release candidate</button>
      <button type="button" className="button-secondary" disabled={candidate.rollbackDisabled}>Execute rollback</button>
      <div id="release-blockers"><ChipList label="Release blockers" values={candidate.blockers.map(businessFacingText)} /></div>
      <ChipList label="Affected subsystems" values={candidate.affectedSubsystems} />
      <div className="quote-table" role="table" aria-label="Readiness checks">
        <div role="row" className="quote-table__row quote-table__row--head"><span role="columnheader">Gate</span><span role="columnheader">Status</span><span role="columnheader">Mandatory</span><span role="columnheader">Artifact</span></div>
        {candidate.gates.map((gate) => <div key={gate.gateName} role="row" className="quote-table__row"><span role="cell">{gate.gateName}</span><span role="cell">{gate.status}</span><span role="cell">{gate.mandatory ? 'yes' : 'no'}</span><span role="cell">{gate.artifactRef}</span></div>)}
      </div>
      <section aria-labelledby="open-decisions-heading">
        <h3 id="open-decisions-heading">Open decision blockers</h3>
        <ul className="offer-list" aria-label="Open decisions blocking release">
          {openDecisions.map((decision) => <li key={decision.decisionId} role="alert"><h4>{decision.decisionId} · {decision.status}</h4><p>{businessFacingText(decision.title)}</p><p>Resolution record: {decision.resolutionRef}</p></li>)}
        </ul>
      </section>
    </section>
  );
}
