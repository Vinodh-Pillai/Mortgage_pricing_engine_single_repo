import type { ChangeRequestSummary } from '../../lib/api/adminGovernance';
import { ChipList } from './shared';

export function ChangeRequests({ requests }: { requests: ChangeRequestSummary[] }) {
  return (
    <section className="panel" aria-labelledby="change-requests-heading">
      <h2 id="change-requests-heading">Change Requests</h2>
      <div className="quote-table" role="table" aria-label="Change requests">
        <div role="row" className="quote-table__row quote-table__row--head">
          <span role="columnheader">Request ID</span>
          <span role="columnheader">Type</span>
          <span role="columnheader">State</span>
          <span role="columnheader">Risk</span>
          <span role="columnheader">Owner</span>
          <span role="columnheader">Sequence and blockers</span>
        </div>
        {requests.map((request) => (
          <div key={request.requestId} role="row" className="quote-table__row">
            <span role="cell">{request.requestId}</span>
            <span role="cell">{request.requestType}</span>
            <span role="cell">{request.state}</span>
            <span role="cell">{request.riskLevel}</span>
            <span role="cell">{request.owner}</span>
            <span role="cell"><details><summary>View Sequence</summary><ChipList label={`${request.requestId} required state sequence`} values={request.requiredStateSequence} /></details><ChipList label={`${request.requestId} blockers`} values={request.blockers} /><button type="button" disabled={request.promotionDisabled}>Approve change request</button><button type="button" disabled>Reject change request</button></span>
          </div>
        ))}
      </div>
    </section>
  );
}
