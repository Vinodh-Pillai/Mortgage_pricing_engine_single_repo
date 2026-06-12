import type { PendingConfigReview } from '../../lib/api/adminGovernance';
import { ChipList, businessFacingText } from './shared';

export function PendingReview({ review }: { review: PendingConfigReview }) {
  return (
    <section className="panel" aria-labelledby="pending-review-heading">
      <h2 id="pending-review-heading">Pending config review impact</h2>
      <div className="banner banner--blocked" role="alert">
        <strong>{review.reviewId} · {businessFacingText(review.state)}</strong>
        <span>Simulation, approval, publish, and rollback visibility is supplied by governance records.</span>
        <span>Review record: {review.auditRef}</span>
      </div>
      <dl className="status-grid">
        <dt>Simulation</dt><dd>{review.simulationVisible ? 'Visible' : 'Blocked'}</dd>
        <dt>Approval</dt><dd>{review.approvalVisible ? 'Visible' : 'Blocked'}</dd>
        <dt>Publish</dt><dd>{review.publishVisible ? 'Visible' : 'Blocked'}</dd>
        <dt>Rollback</dt><dd>{review.rollbackVisible ? 'Visible' : 'Blocked'}</dd>
      </dl>
      <button type="button" disabled={!review.simulationVisible}>Run simulation review</button>
      <button type="button" disabled={!review.approvalVisible}>Open approval review</button>
      <button type="button" disabled={!review.publishVisible}>Publish review</button>
      <button type="button" disabled={!review.rollbackVisible}>Rollback review</button>
      <ChipList label="Downstream consumer impact" values={review.downstreamConsumers} />
      <ChipList label="Pending config review blockers" values={review.blockers} />
    </section>
  );
}
