import type { AdminGovernanceView } from '../../lib/api/adminGovernance';
import { ChangeRequests } from './ChangeRequests';
import { Descriptors } from './Descriptors';
import { DriftAlerts } from './DriftAlerts';
import { DynamicRuleEvidence } from './DynamicRuleEvidence';
import { FeatureFlags } from './FeatureFlags';
import { Incidents } from './Incidents';
import { MarketRules } from './MarketRules';
import { OverrideLedger } from './OverrideLedger';
import { PendingReview } from './PendingReview';
import { Policies } from './Policies';
import { ReleaseCandidate } from './ReleaseCandidate';
import { TraceMetadata } from './TraceMetadata';
import { businessFacingText, serviceReadinessText } from './shared';

export function GovernanceLayout({ view }: { view: AdminGovernanceView }) {
  return (
    <div className="admin-governance-screen" data-state="ready">
      <section className="hero hero--admin" aria-labelledby="admin-title">
        <p className="eyebrow">Admin · PII-24-S24</p>
        <h2 id="admin-title">Admin governance and readiness controls</h2>
        <p>
          Manage the governance lifecycle from service-provided descriptors, policy versions, feature flags, market-rule completeness,
          change requests, release gates, drift alerts, incidents, override ledger entries, pending review state, and dynamic rule evidence.
          Actions stay enabled or disabled from the governance response, not from local browser rules.
        </p>
      </section>

      <section className="panel" aria-labelledby="admin-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Governance lifecycle</p>
            <h2 id="admin-heading">Governance Lifecycle</h2>
          </div>
          <TraceMetadata metadata={view.traceMetadata} tenantContext={view.tenantContext} adminRole={view.adminRole} />
        </div>
        <div className="banner banner--blocked" role="alert">
          <strong>Release status {view.releaseCandidate.readinessStatus}</strong>
          <span>{businessFacingText(view.fallbackReason)}</span>
          <span>Setup status: {serviceReadinessText(view.dependencyStatus)}</span>
        </div>
      </section>

      <Descriptors descriptors={view.descriptors} />
      <Policies policies={view.policies} />
      <FeatureFlags flags={view.featureFlags} />
      <MarketRules rules={view.marketRules} />
      <ChangeRequests requests={view.changeRequests} />
      <ReleaseCandidate candidate={view.releaseCandidate} openDecisions={view.openDecisions} />
      <DriftAlerts alerts={view.driftAlerts} />
      <Incidents incidents={view.incidents} />
      <OverrideLedger entries={view.overrideLedger} />
      <PendingReview review={view.pendingReview} />
      <DynamicRuleEvidence evidence={view.dynamicRuleEvidence} events={view.events} />
    </div>
  );
}
