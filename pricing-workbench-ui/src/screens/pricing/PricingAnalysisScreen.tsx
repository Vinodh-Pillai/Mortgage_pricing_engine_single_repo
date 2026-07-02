import { useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';
import { fetchPricingWaterfall, type PricingWaterfallView, type WaterfallLedgerRow } from '../../lib/api/quoteRuns';
import { useOptionalTenantId } from '../../lib/data/tenant';
import type { EvidenceCapture } from '../shared/MajorFunctionalityPage';

type PricingAnalysisState =
  | { kind: 'blocked'; message: string }
  | { kind: 'loading'; runId: string }
  | { kind: 'loaded'; view: PricingWaterfallView }
  | { kind: 'unreachable'; runId: string; message: string };

export const pricingAnalysisEvidenceTarget = '.local-harness/evidence/PII-25-S04/pricing-analysis.json';

export function PricingAnalysisScreen({ runId: propRunId, onEvidenceCapture }: { runId?: string; onEvidenceCapture?: EvidenceCapture }) {
  const params = useParams();
  const runId = propRunId ?? params.runId ?? '';
  const tenantId = useOptionalTenantId();
  const [state, setState] = useState<PricingAnalysisState>(() => {
    if (!runId) return { kind: 'blocked', message: 'Select a pricing run before opening pricing analysis.' };
    if (!tenantId) return { kind: 'blocked', message: 'Select a tenant context before loading pricing analysis.' };
    return { kind: 'loading', runId };
  });

  useEffect(() => {
    if (!runId) {
      setState({ kind: 'blocked', message: 'Select a pricing run before opening pricing analysis.' });
      return undefined;
    }
    if (!tenantId) {
      setState({ kind: 'blocked', message: 'Select a tenant context before loading pricing analysis.' });
      return undefined;
    }
    let active = true;
    setState({ kind: 'loading', runId });
    fetchPricingWaterfall(tenantId, runId)
      .then((view) => {
        if (active) setState({ kind: 'loaded', view: { ...view, runId: view.runId || runId } });
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'Pricing analysis records are unavailable.';
        if (active) setState({ kind: 'unreachable', runId, message });
      });
    return () => { active = false; };
  }, [runId, tenantId]);

  useEffect(() => {
    const refs = state.kind === 'loaded'
      ? [pricingAnalysisEvidenceTarget, state.view.runId, state.view.uiTraceId, state.view.replayHash, state.view.evidenceHash, ...state.view.auditRefs]
      : [pricingAnalysisEvidenceTarget, runId || 'run-required'];
    onEvidenceCapture?.({
      screenId: 'pricing-analysis',
      timestamp: new Date().toISOString(),
      state: state.kind === 'loaded' ? (state.view.finalPrice.ledger.length > 0 ? 'ready' : 'empty') : state.kind === 'loading' ? 'loading' : 'blocked',
      dataRefs: refs,
      blockers: state.kind === 'blocked' || state.kind === 'unreachable' ? [state.message] : state.kind === 'loaded' ? state.view.blockers.map((blocker) => blocker.code) : [],
      evidenceTarget: pricingAnalysisEvidenceTarget,
      refs,
    });
  }, [onEvidenceCapture, runId, state]);

  if (state.kind === 'blocked') return <PricingAnalysisBlocked message={state.message} />;
  if (state.kind === 'loading') return <main className="functionality-page"><section className="panel"><h1>Pricing Analysis</h1><p role="status">Loading pricing-service records for run <code>{state.runId}</code>...</p></section></main>;
  if (state.kind === 'unreachable') return <PricingAnalysisBlocked message={state.message} runId={state.runId} />;

  return <PricingAnalysisLoaded view={state.view} />;
}

function PricingAnalysisBlocked({ message, runId }: { message: string; runId?: string }) {
  return (
    <main className="functionality-page" data-screen-id="pricing-analysis">
      <section className="hero" aria-labelledby="pricing-analysis-title">
        <p className="eyebrow">Pricing analysis</p>
        <h1 id="pricing-analysis-title">Pricing Analysis</h1>
        <p>Pricing analysis is run-specific. Open a completed quote run to review pricing-service records.</p>
      </section>
      <section className="panel" aria-labelledby="pricing-analysis-blocked-heading">
        <h2 id="pricing-analysis-blocked-heading">Live backend required</h2>
        <div className="banner banner--blocked" role="alert">
          <strong>Pricing analysis blocked</strong>
          <span>{message}</span>
          {runId ? <span>Run: <code>{runId}</code></span> : <span>Expected route: <code>/pricing/analysis/&lt;runId&gt;</code></span>}
        </div>
      </section>
    </main>
  );
}

function PricingAnalysisLoaded({ view }: { view: PricingWaterfallView }) {
  const rows = useMemo(() => analysisRows(view), [view]);
  const hasRows = rows.length > 0;
  return (
    <main className="functionality-page" data-screen-id="pricing-analysis" aria-labelledby="pricing-analysis-title">
      <section className="hero" aria-labelledby="pricing-analysis-title">
        <p className="eyebrow">Pricing-service records</p>
        <h1 id="pricing-analysis-title">Pricing Analysis for run {view.runId}</h1>
        <p>Review backend pricing waterfall, adjustment, margin, rounding, blocker, and audit records. The browser does not calculate prices or render static fallback rows.</p>
      </section>

      {view.status === 'BLOCKED' || view.blockers.length > 0 ? (
        <div className="banner banner--blocked" role="alert">
          <strong>{view.status}</strong>
          <span>{view.fallbackReason || 'Pricing-service returned blockers for this run.'}</span>
        </div>
      ) : null}

      <section className="page-metrics" aria-label="Pricing analysis metrics">
        <div className="page-metric"><span>Run</span><strong>{view.runId}</strong><small>{view.dependencyStatus}</small></div>
        <div className="page-metric"><span>Ledger rows</span><strong>{String(view.finalPrice.ledger.length)}</strong><small>pricing-service supplied</small></div>
        <div className="page-metric"><span>Restricted values</span><strong>{view.restrictedValuesVisible ? 'Visible' : 'Redacted'}</strong><small>backend redaction policy</small></div>
      </section>

      <section className="panel" aria-labelledby="pricing-analysis-summary-heading">
        <h2 id="pricing-analysis-summary-heading">Run summary</h2>
        <dl className="status-grid">
          <dt>Base selection</dt><dd>{valueText(view.baseSelection.selectionId)}</dd>
          <dt>Grid version</dt><dd>{valueText(view.baseSelection.gridVersionRef)}</dd>
          <dt>Final price record</dt><dd>{valueText(view.finalPrice.finalPriceId)}</dd>
          <dt>Replay record</dt><dd>{valueText(view.replayHash)}</dd>
          <dt>Evidence hash</dt><dd>{valueText(view.evidenceHash)}</dd>
        </dl>
      </section>

      <section className="panel" aria-labelledby="pricing-analysis-records-heading">
        <h2 id="pricing-analysis-records-heading">Pricing analysis records</h2>
        {!hasRows ? <p role="status">No pricing-service ledger rows are available for this run.</p> : (
          <div className="quote-table" role="table" aria-label="Pricing analysis records">
            <div role="row" className="quote-table__row quote-table__row--head">
              <span role="columnheader">Section</span>
              <span role="columnheader">Step</span>
              <span role="columnheader">Input</span>
              <span role="columnheader">Output</span>
              <span role="columnheader">Config ref</span>
              <span role="columnheader">Reason</span>
            </div>
            {rows.map((row) => <PricingAnalysisRow key={`${row.ordinal}-${row.step}`} row={row} />)}
          </div>
        )}
      </section>

      <section className="panel" aria-labelledby="pricing-analysis-blockers-heading">
        <h2 id="pricing-analysis-blockers-heading">Blockers and audit refs</h2>
        {view.blockers.length === 0 ? <p role="status">No pricing-service blockers returned for this run.</p> : (
          <ul className="offer-list" aria-label="Pricing analysis blockers">
            {view.blockers.map((blocker) => <li key={`${blocker.code}-${blocker.sourceRef}`}><strong>{blocker.code}</strong><p>{blocker.message}</p><code>{blocker.sourceRef}</code></li>)}
          </ul>
        )}
        <RefList label="Audit refs" values={view.auditRefs} />
        <RefList label="Version refs" values={view.versionRefs} />
        <RefList label="Adjustment refs" values={view.finalPrice.adjustmentRefs} />
        <RefList label="Margin refs" values={view.finalPrice.marginRefs ?? []} />
      </section>
    </main>
  );
}

function PricingAnalysisRow({ row }: { row: WaterfallLedgerRow }) {
  return (
    <div role="row" className="quote-table__row">
      <span role="cell">{valueText(row.section ?? 'Base Rate')}</span>
      <span role="cell">{row.ordinal}. {valueText(row.step)}</span>
      <span role="cell">{waterfallValueText(row.inputValue)}</span>
      <span role="cell">{waterfallValueText(row.outputValue)}</span>
      <span role="cell"><code>{valueText(row.configRef)}</code></span>
      <span role="cell">{valueText(row.reasonCode)}</span>
    </div>
  );
}

function analysisRows(view: PricingWaterfallView) {
  return view.finalPrice.ledger;
}

function RefList({ label, values }: { label: string; values: string[] }) {
  return <div className="copyable-ref-list" aria-label={label}><strong>{label}</strong>{values.length === 0 ? <p>None supplied.</p> : <ul>{values.map((value) => <li key={value}><code>{value}</code></li>)}</ul>}</div>;
}

function waterfallValueText(value: { value: string | null; redacted: boolean; reason: string | null } | undefined) {
  if (!value) return 'Not provided';
  if (value.redacted) return `Redacted: ${valueText(value.reason)}`;
  return valueText(value.value);
}

function valueText(value: unknown) {
  if (value === null || value === undefined || value === '') return 'Not supplied';
  return String(value);
}

export default PricingAnalysisScreen;
