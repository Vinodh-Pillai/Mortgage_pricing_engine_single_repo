import { useEffect, useState } from 'react';
import { DiagnosticsDetails } from '../../components/DiagnosticsDetails';
import { ChipList, businessFacingText, serviceReadinessText } from '../mlAdvisoryInsights/shared';
import { Alerts } from './Alerts';
import { FeatureDrift } from './FeatureDrift';
import { Investigation } from './Investigation';
import { PopulationStability } from './PopulationStability';
import { PredictionDrift } from './PredictionDrift';
import { fetchDriftMonitoringView } from './api';
import { blockedDriftMonitoringView } from './fixtures';
import type { DriftMonitoringView } from './types';

type DriftState =
  | { kind: 'loading' }
  | { kind: 'loaded'; view: DriftMonitoringView }
  | { kind: 'unreachable'; message: string };

type DriftTab = 'feature' | 'prediction' | 'population' | 'alerts' | 'investigation';

const tabs: Array<{ id: DriftTab; label: string }> = [
  { id: 'feature', label: 'Feature Drift' },
  { id: 'prediction', label: 'Prediction Drift' },
  { id: 'population', label: 'Population Stability' },
  { id: 'alerts', label: 'Alerts' },
  { id: 'investigation', label: 'Investigation' },
];

export function DriftMonitoringScreen() {
  const [state, setState] = useState<DriftState>({ kind: 'loading' });
  const [activeTab, setActiveTab] = useState<DriftTab>('feature');

  useEffect(() => {
    let active = true;
    fetchDriftMonitoringView()
      .then((view) => {
        if (active) setState({ kind: 'loaded', view });
      })
      .catch((error: unknown) => {
        if (active) setState({ kind: 'unreachable', message: error instanceof Error ? error.message : 'ML advisory drift monitoring is unavailable.' });
      });
    return () => {
      active = false;
    };
  }, []);

  if (state.kind === 'loading') {
    return <section className="panel" aria-labelledby="drift-loading-heading"><h2 id="drift-loading-heading">Drift monitoring loading state</h2><p role="status">Loading drift monitoring data...</p></section>;
  }

  const view = state.kind === 'loaded' ? state.view : blockedDriftMonitoringView;

  return (
    <div className="ml-advisory-shell drift-monitoring-shell">
      <section className="hero" aria-labelledby="drift-monitoring-title">
        <p className="eyebrow">ML monitoring · PII-24-S27</p>
        <h2 id="drift-monitoring-title">Drift Monitoring</h2>
        <p>
          Deep-dive model drift workspace for feature drift, prediction drift, population stability, actionable alerts, and investigation evidence.
          The screen renders story-approved thresholds and connected-service values only; it does not implement drift detection algorithms.
        </p>
      </section>

      <section className="panel sticky-header" aria-labelledby="drift-context-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Tenant context</p>
            <h2 id="drift-context-heading">Drift workspace</h2>
          </div>
          <DiagnosticsDetails items={[`Support reference: ${view.uiTraceId}`, `Workspace ${view.tenantContext}`, `Route /advisory/ml/drift`]} />
        </div>
        <div className="module-rail__grid" role="list" aria-label="Drift monitoring selectors">
          <article className="module-card" role="listitem"><strong>Model version</strong><p>{view.modelVersion}</p><p>{view.modelVersionStatus}</p></article>
          <article className="module-card" role="listitem"><strong>Time range</strong><select aria-label="Time range" defaultValue={view.timeRange}>{['1h', '6h', '24h', '7d', '30d'].map((range) => <option key={range}>{range}</option>)}</select></article>
          <article className="module-card" role="listitem"><strong>Setup status</strong><p>{serviceReadinessText(view.dependencyStatus)}</p></article>
        </div>
        <div className={state.kind === 'unreachable' ? 'banner banner--blocked' : 'banner banner--info'} role={state.kind === 'unreachable' ? 'alert' : 'status'}>
          <strong>{state.kind === 'unreachable' ? 'Drift API unavailable' : 'Drift data loaded'}</strong>
          <span>{state.kind === 'unreachable' ? state.message : businessFacingText(view.fallbackReason)}</span>
        </div>
      </section>

      <section className="panel" aria-labelledby="drift-tabs-heading">
        <div className="panel-heading-row sticky-header">
          <div>
            <p className="eyebrow">Investigation tabs</p>
            <h2 id="drift-tabs-heading">Drift monitoring tabs</h2>
          </div>
          <ChipList label="Drift monitoring events" values={view.events.map(businessFacingText)} />
        </div>
        <div role="tablist" aria-label="Drift monitoring tabs" className="module-rail__grid">
          {tabs.map((tab) => <button key={tab.id} type="button" role="tab" aria-selected={activeTab === tab.id} onClick={() => setActiveTab(tab.id)}>{tab.label}</button>)}
        </div>
        {activeTab === 'feature' ? <FeatureDrift rows={view.featureDrift} /> : null}
        {activeTab === 'prediction' ? <PredictionDrift prediction={view.predictionDrift} /> : null}
        {activeTab === 'population' ? <PopulationStability population={view.populationStability} /> : null}
        {activeTab === 'alerts' ? <Alerts alerts={view.alerts} /> : null}
        {activeTab === 'investigation' ? <Investigation alerts={view.alerts} investigation={view.investigation} /> : null}
      </section>
    </div>
  );
}
