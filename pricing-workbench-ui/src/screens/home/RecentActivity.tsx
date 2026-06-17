import type { ActivityRecord } from '../../lib/activity/activity';

type RecentActivityProps = {
  records: ActivityRecord[];
  onNavigate: (route: string) => void;
  onStartPipeline: () => void;
};

export function RecentActivity({ records, onNavigate, onStartPipeline }: RecentActivityProps) {
  return (
    <section className="home-card" aria-labelledby="recent-activity-heading">
      <div className="home-section-heading">
        <p className="eyebrow">Recent activity</p>
        <h2 id="recent-activity-heading">Resume work</h2>
      </div>
      {records.length ? (
        <div className="activity-table-wrap">
          <table className="activity-table">
            <thead>
              <tr>
                <th scope="col">Borrower</th>
                <th scope="col">Property Address</th>
                <th scope="col">Status</th>
                <th scope="col">Last Action</th>
                <th scope="col">Time</th>
              </tr>
            </thead>
            <tbody>
              {records.map((record) => (
                <tr key={record.id}>
                  <td><button className="link-button" type="button" onClick={() => onNavigate(record.route)}>{record.borrowerName}</button></td>
                  <td title={record.propertyAddress}>{record.propertyAddress}</td>
                  <td><span className={`activity-status activity-status--${record.status.toLowerCase().replace('_', '-')}`}>{record.status.replace('_', ' ')}</span></td>
                  <td>{record.lastAction}</td>
                  <td><time dateTime={record.timestamp}>{formatActivityTime(record.timestamp)}</time></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="empty-activity" role="status">
          <p>No recent activity. Start a new pipeline.</p>
          <button className="button primary" type="button" onClick={onStartPipeline}>Start Pipeline</button>
        </div>
      )}
    </section>
  );
}

function formatActivityTime(timestamp: string) {
  const value = new Date(timestamp);
  if (Number.isNaN(value.getTime())) return 'Time pending';
  return new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' }).format(value);
}
