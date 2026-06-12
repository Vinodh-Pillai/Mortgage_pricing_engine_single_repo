import { useEffect, useState } from 'react';
import { fetchAdminGovernance, type AdminGovernanceView } from '../../lib/api/adminGovernance';
import { GovernanceLayout } from './GovernanceLayout';

type AdminGovernanceState =
  | { kind: 'loading' }
  | { kind: 'loaded'; view: AdminGovernanceView }
  | { kind: 'unreachable'; message: string };

export function AdminGovernanceScreen() {
  const [state, setState] = useState<AdminGovernanceState>({ kind: 'loading' });

  useEffect(() => {
    let active = true;
    fetchAdminGovernance()
      .then((view) => {
        if (active) setState({ kind: 'loaded', view });
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'Admin governance lifecycle is unavailable.';
        if (active) setState({ kind: 'unreachable', message });
      });
    return () => {
      active = false;
    };
  }, []);

  if (state.kind === 'loading') {
    return <section className="panel" aria-labelledby="admin-heading"><h2 id="admin-heading">Admin governance and readiness controls</h2><p role="status">Loading admin governance lifecycle...</p></section>;
  }

  if (state.kind === 'unreachable') {
    return (
      <section className="panel" aria-labelledby="admin-heading">
        <h2 id="admin-heading">Admin governance and readiness controls</h2>
        <div className="banner banner--blocked" role="alert">{state.message}</div>
      </section>
    );
  }

  return <GovernanceLayout view={state.view} />;
}
