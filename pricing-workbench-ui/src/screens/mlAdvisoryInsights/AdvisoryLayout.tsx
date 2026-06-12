import { useEffect, useState } from 'react';
import { DiagnosticsDetails } from '../../components/DiagnosticsDetails';
import { fetchMlAdvisoryInsights, type MlAdvisoryInsightsView } from '../../lib/api/mlAdvisoryInsights';
import { AdvisoryUnavailable } from './AdvisoryUnavailable';
import { ModelGovernance } from './ModelGovernance';
import { Recommendations } from './Recommendations';
import { businessFacingText, serviceReadinessText } from './shared';

type MlAdvisoryInsightsState =
  | { kind: 'loading' }
  | { kind: 'loaded'; view: MlAdvisoryInsightsView }
  | { kind: 'unreachable'; message: string };

export function MlAdvisoryInsightsScreen() {
  const [insightsState, setInsightsState] = useState<MlAdvisoryInsightsState>({ kind: 'loading' });

  function loadInsights() {
    setInsightsState({ kind: 'loading' });
    fetchMlAdvisoryInsights()
      .then((view) => setInsightsState({ kind: 'loaded', view }))
      .catch((error: unknown) => setInsightsState({ kind: 'unreachable', message: error instanceof Error ? error.message : 'ML advisory insights are unavailable.' }));
  }

  useEffect(() => {
    let active = true;
    fetchMlAdvisoryInsights()
      .then((view) => {
        if (active) setInsightsState({ kind: 'loaded', view });
      })
      .catch((error: unknown) => {
        if (active) setInsightsState({ kind: 'unreachable', message: error instanceof Error ? error.message : 'ML advisory insights are unavailable.' });
      });
    return () => {
      active = false;
    };
  }, []);

  if (insightsState.kind === 'loading') {
    return <section className="panel" aria-labelledby="ml-advisory-loading-heading"><h2 id="ml-advisory-loading-heading">ML advisory loading state</h2><p role="status">Loading ML advisory insights...</p></section>;
  }

  if (insightsState.kind === 'unreachable') {
    return (
      <section className="panel" aria-labelledby="ml-advisory-unreachable-heading">
        <h2 id="ml-advisory-unreachable-heading">ML advisory unavailable state</h2>
        <div className="banner banner--blocked" role="alert">{insightsState.message}</div>
      </section>
    );
  }

  const view = insightsState.view;
  return (
    <div className="ml-advisory-shell">
      <section className="hero" aria-labelledby="ml-advisory-title">
        <p className="eyebrow">ML advisory · PII-24-S25</p>
        <h2 id="ml-advisory-title">ML Advisory Insights</h2>
        <p>
          Inspect API-supplied recommendations, explanations, model governance, feedback, and export refs without applying browser-side pricing rules or inferring model thresholds.
        </p>
      </section>

      <section className="panel" aria-labelledby="ml-advisory-context-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Tenant and model context</p>
            <h2 id="ml-advisory-context-heading">Model version selector</h2>
          </div>
          <DiagnosticsDetails items={[`Support reference: ${view.uiTraceId}`, `Workspace ${view.tenantContext}`]} />
        </div>
        <label htmlFor="ml-model-version-selector">Model version</label>
        <select id="ml-model-version-selector" defaultValue={view.modelVersions[0]?.modelVersion ?? ''}>
          {view.modelVersions.map((modelVersion) => <option key={modelVersion.modelVersion} value={modelVersion.modelVersion}>{modelVersion.modelVersion}</option>)}
        </select>
        <div className={view.advisoryUnavailable ? 'banner banner--blocked' : 'banner banner--info'} role={view.advisoryUnavailable ? 'alert' : 'status'}>
          <strong>{view.advisoryUnavailable ? 'Advisory unavailable' : 'Advisory evidence visible'}</strong>
          <span>{businessFacingText(view.fallbackReason)}</span>
          <span>Setup status: {serviceReadinessText(view.dependencyStatus)}</span>
        </div>
      </section>

      {view.advisoryUnavailable ? <AdvisoryUnavailable reason={view.fallbackReason || view.dependencyStatus} onRetry={loadInsights} /> : null}
      <Recommendations recommendations={view.recommendations} />
      <ModelGovernance modelVersions={view.modelVersions} events={view.events} />
    </div>
  );
}
