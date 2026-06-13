import { useEffect, useMemo, useState } from 'react';
import { fetchRateFeedPipeline, type RateFeedPipelineRow, type RateFeedPipelineView } from '../../lib/api/rateFeedPipeline';

type PipelineState = { kind: 'loading' } | { kind: 'ready'; view: RateFeedPipelineView; selected: RateFeedPipelineRow } | { kind: 'blocked'; message: string };

export function RateFeedPipelineScreen({ tenantId = 'ui-preview-tenant' }: { tenantId?: string }) {
  const [state, setState] = useState<PipelineState>({ kind: 'loading' });

  useEffect(() => {
    let active = true;
    fetchRateFeedPipeline(tenantId)
      .then((view) => {
        if (!active) return;
        if (!view.pipelines.length) setState({ kind: 'blocked', message: 'No mapped rate feed pipeline entries are available yet.' });
        else setState({ kind: 'ready', view, selected: view.pipelines[0] });
      })
      .catch(() => active && setState({ kind: 'blocked', message: 'Rate feed pipeline monitor is temporarily unavailable.' }));
    return () => { active = false; };
  }, [tenantId]);

  if (state.kind === 'loading') {
    return <section className="panel" aria-labelledby="ratefeed-pipeline-heading"><h2 id="ratefeed-pipeline-heading">Rate Feed → Rule Book Pipeline</h2><p role="status">Loading pipeline status...</p></section>;
  }
  if (state.kind === 'blocked') {
    return <section className="panel" aria-labelledby="ratefeed-pipeline-heading"><h2 id="ratefeed-pipeline-heading">Rate Feed → Rule Book Pipeline</h2><div className="banner banner--blocked" role="alert">{state.message}</div></section>;
  }

  return <RateFeedPipelineReady view={state.view} selected={state.selected} onSelect={(row) => setState({ kind: 'ready', view: state.view, selected: row })} />;
}

function RateFeedPipelineReady({ view, selected, onSelect }: { view: RateFeedPipelineView; selected: RateFeedPipelineRow; onSelect: (row: RateFeedPipelineRow) => void }) {
  const summary = useMemo(() => `${view.count} pipeline entries · generated ${view.generatedAt}`, [view]);
  return (
    <section className="panel" aria-labelledby="ratefeed-pipeline-heading">
      <header className="hero hero--admin">
        <p className="eyebrow">Rate feed automation</p>
        <h2 id="ratefeed-pipeline-heading">Rate Feed → Rule Book Pipeline</h2>
        <p>{summary}</p>
        <button type="button" onClick={() => window.location.reload()}>Refresh</button>
      </header>

      <div role="table" aria-label="Rate feed to rule book pipeline status" className="module-card-grid">
        <div role="row" className="module-card module-card--header">
          <span role="columnheader">Rate Sheet</span><span role="columnheader">Investor</span><span role="columnheader">Status</span><span role="columnheader">Rules</span><span role="columnheader">Last Action</span>
        </div>
        {view.pipelines.map((row) => (
          <button type="button" role="row" className="module-card" key={row.sheetId} onClick={() => onSelect(row)} aria-pressed={row.sheetId === selected.sheetId}>
            <span role="cell">{row.rateSheet}</span><span role="cell">{row.investor}</span><span role="cell">{row.status}</span><span role="cell">{row.ruleCount.toLocaleString()}</span><span role="cell">{row.lastAction}</span>
          </button>
        ))}
      </div>

      <section className="module-card" aria-labelledby="pipeline-detail-heading">
        <h3 id="pipeline-detail-heading">Pipeline Detail: {selected.rateSheet}</h3>
        <dl className="diagnostics-list">
          <dt>Grid Hash</dt><dd>{selected.gridHash}</dd>
          <dt>Rows</dt><dd>{selected.sourceRowCount.toLocaleString()}</dd>
          <dt>Warnings</dt><dd>{selected.warningCount}</dd>
          <dt>Mapped Rules</dt><dd>{selected.ruleCount.toLocaleString()}</dd>
          <dt>Dimensions Used</dt><dd>{selected.dimensionsUsed.length}/58 · {selected.dimensionsUsed.join(', ')}</dd>
        </dl>
        <h4>Governance</h4>
        <ol>
          {selected.governanceHistory.map((stage) => <li key={`${stage.stage}-${stage.status}`}>{stage.stage}: {stage.status}{stage.actorRef ? ` (${stage.actorRef})` : ''}</li>)}
        </ol>
        <h4>Sample Simulation</h4>
        <p>{selected.sampleSimulation.factsSummary}</p>
        <p>Expected: {selected.sampleSimulation.expectedAdjustment} · Actual: {selected.sampleSimulation.actualAdjustment} · {selected.sampleSimulation.status}</p>
      </section>
    </section>
  );
}
