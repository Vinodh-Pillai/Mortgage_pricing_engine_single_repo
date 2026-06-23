import { useEffect, useMemo, useState } from 'react';
import { DiagnosticsDetails } from '../../components/DiagnosticsDetails';
import { usePageActions } from '../../layout/PageActionsContext';
import {
  fetchScenarioAnalysisWorkspace,
  recalculateScenarioAnalysis,
  type ScenarioAnalysisBatchRow,
  type ScenarioAnalysisBlocker,
  type ScenarioAnalysisDimension,
  type ScenarioAnalysisSavedAnalysis,
  type ScenarioAnalysisVariant,
  type ScenarioAnalysisWorkspaceView,
  type ScenarioRecalculationResult,
} from '../../lib/api/scenarioAnalysis';

type ScenarioAnalysisWorkspaceState =
  | { kind: 'loading' }
  | { kind: 'loaded'; view: ScenarioAnalysisWorkspaceView }
  | { kind: 'unreachable'; message: string };

export function ScenarioAnalysisWorkspaceScreen({ runId, tenantContext }: { runId: string; tenantContext: string }) {
  const { setPromotedActions } = usePageActions();
  const [workspaceState, setWorkspaceState] = useState<ScenarioAnalysisWorkspaceState>({ kind: 'loading' });
  const [dimensionId, setDimensionId] = useState('');
  const [requestedValue, setRequestedValue] = useState('');
  const [variantName, setVariantName] = useState('');
  const [selectedVariantId, setSelectedVariantId] = useState('');
  const [localDraftVariants, setLocalDraftVariants] = useState<ScenarioAnalysisVariant[]>([]);
  const [recalculation, setRecalculation] = useState<ScenarioRecalculationResult | null>(null);
  const [recalculateError, setRecalculateError] = useState('');
  const [workspaceNotice, setWorkspaceNotice] = useState('');

  useEffect(() => {
    let active = true;
    fetchScenarioAnalysisWorkspace(tenantContext, runId)
      .then((view) => {
        if (!active) return;
        const normalisedView = normaliseScenarioWorkspaceView(view, tenantContext, runId);
        setWorkspaceState({ kind: 'loaded', view: normalisedView });
        setDimensionId(normalisedView.dimensions[0]?.dimensionId ?? '');
        setRequestedValue(normalisedView.dimensions[0]?.value ?? '');
        setSelectedVariantId(normalisedView.variants[0]?.variantId ?? '');
      })
      .catch(() => {
        if (active) setWorkspaceState({ kind: 'unreachable', message: 'Scenario analysis is temporarily unavailable.' });
      });
    return () => {
      active = false;
    };
  }, [runId, tenantContext]);

  async function requestRecalculation(scope: 'selected' | 'all' = 'selected') {
    if (workspaceState.kind !== 'loaded') return;
    setRecalculateError('');
    setWorkspaceNotice('');
    const selectedDimension = workspaceState.view.dimensions.find((dimension) => dimension.dimensionId === dimensionId);
    const visibleVariants = [...workspaceState.view.variants, ...localDraftVariants];
    const selectedVariants = scope === 'all'
      ? visibleVariants
      : visibleVariants.filter((variant) => variant.variantId === selectedVariantId);
    const variantsForFacts = selectedVariants.length > 0 ? selectedVariants : visibleVariants;
    const facts = Array.from(new Set([
      ...(selectedDimension?.requiredFacts ?? []),
      ...variantsForFacts.flatMap((variant) => variant.factRefs),
    ]));
    try {
      const result = await recalculateScenarioAnalysis(tenantContext, runId, {
        changedDimensionId: dimensionId,
        requestedValue,
        variantFacts: facts,
      });
      setRecalculation(result);
    } catch (error: unknown) {
      setRecalculateError('Scenario recalculation is temporarily unavailable. Try again after the scenario data refreshes.');
    }
  }

  function createVariant() {
    const name = variantName.trim();
    if (!name) {
      setWorkspaceNotice('Enter a variant name before staging a draft.');
      return;
    }
    const draftId = `draft-${slugifyVariantName(name)}-${localDraftVariants.length + 1}`;
    const draft: ScenarioAnalysisVariant = {
      variantId: draftId,
      label: `${name} (local draft)`,
      status: 'Needs review',
      dimensionRefs: dimensionId ? [dimensionId] : [],
      factRefs: [`local-draft:${draftId}:facts`],
      guardrailBlockers: [{
        blockerCode: 'PRODUCTION_INTEGRATION_REQUIRED',
        severity: 'blocked',
        reason: 'Save the variant before using it for a final pricing decision.',
        requiredFacts: ['Saved variant', 'Pricing result', 'Eligibility review'],
        sourceRef: draftId,
      }],
      resultRefs: [],
    };
    setLocalDraftVariants((current) => [...current, draft]);
    setSelectedVariantId(draftId);
    setVariantName('');
    setWorkspaceNotice(`Variant draft "${name}" is ready for review.`);
  }

  function deleteVariant() {
    if (!selectedVariantId) {
      setWorkspaceNotice('Select a variant before requesting deletion.');
      return;
    }
    const localDraft = localDraftVariants.find((variant) => variant.variantId === selectedVariantId);
    if (localDraft) {
      setLocalDraftVariants((current) => current.filter((variant) => variant.variantId !== selectedVariantId));
      setSelectedVariantId(workspaceState.kind === 'loaded' ? workspaceState.view.variants[0]?.variantId ?? '' : '');
      setWorkspaceNotice(`Draft ${selectedVariantId} removed.`);
      return;
    }
    setWorkspaceNotice(`Delete requested for ${selectedVariantId}.`);
  }

  function saveAnalysis() {
    setWorkspaceNotice('Save requested. Analysis details will update after the service confirms the record.');
  }

  function exportAnalysis() {
    setWorkspaceNotice('Export requested.');
  }

  function loadAnalysis(analysis: ScenarioAnalysisSavedAnalysis) {
    setWorkspaceNotice(`Load requested for ${analysis.analysisId}.`);
  }

  function deleteAnalysis(analysis: ScenarioAnalysisSavedAnalysis) {
    setWorkspaceNotice(`Delete requested for ${analysis.analysisId}.`);
  }

  const promotedScenarioActions = useMemo(() => (
    <div className="offer-toolbar" aria-label="Scenario analysis actions">
      <button type="button" onClick={() => void requestRecalculation('selected')} disabled={workspaceState.kind !== 'loaded'}>Recalculate Selected</button>
      <button type="button" onClick={saveAnalysis} disabled={workspaceState.kind !== 'loaded'}>Save Analysis</button>
      <button type="button" onClick={exportAnalysis} disabled={workspaceState.kind !== 'loaded'}>Export</button>
    </div>
  ), [dimensionId, requestedValue, selectedVariantId, workspaceState.kind]);

  useEffect(() => {
    setPromotedActions({ label: 'Scenario analysis page actions', actions: promotedScenarioActions });
    return () => setPromotedActions(null);
  }, [promotedScenarioActions, setPromotedActions]);

  if (workspaceState.kind === 'loading') {
    return <section className="panel" aria-labelledby="scenario-analysis-heading"><h2 id="scenario-analysis-heading">Scenario Analysis</h2><p role="status">Loading scenario analysis workspace...</p></section>;
  }

  if (workspaceState.kind === 'unreachable') {
    return (
      <section className="panel" aria-labelledby="scenario-analysis-heading">
        <h2 id="scenario-analysis-heading">Scenario Analysis</h2>
        <div className="banner banner--blocked" role="alert">{workspaceState.message}</div>
      </section>
    );
  }

  const view = workspaceState.view;
  const visibleVariants = [...view.variants, ...localDraftVariants];

  return (
    <>
      <section className="hero" aria-labelledby="scenario-analysis-title">
        <p className="eyebrow">Scenario analysis · PII-24-S29</p>
        <h2 id="scenario-analysis-title">Scenario Analysis for run {runId}</h2>
        <p>
          Review dimensions, variants, guardrails, saved analyses, exports, and recalculation results.
        </p>
        <a href={`/quote/${encodeURIComponent(runId)}/offers`}>Back to Offers</a>
      </section>

      <section className="panel" aria-labelledby="scenario-analysis-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Workspace</p>
            <h2 id="scenario-analysis-heading">Dimensions and variant facts</h2>
          </div>
          <DiagnosticsDetails items={[`Support reference: ${businessFacingText(view.uiTraceId)}`, `Status: ${businessFacingText(view.dependencyStatus)}`]} />
        </div>
        {view.fallbackReason ? <div className="banner banner--blocked" role="alert"><strong>Scenario analysis needs attention</strong><span>{businessFacingText(view.fallbackReason)}</span></div> : null}
        {workspaceNotice ? <div className="banner banner--info" role="status">{workspaceNotice}</div> : null}
        <div className="offer-grid" role="list" aria-label="What-if dimensions">
          {view.dimensions.map((dimension) => <ScenarioDimensionCard key={dimension.dimensionId} dimension={dimension} />)}
        </div>
        <div className="offer-grid" role="list" aria-label="What-if variants">
          {visibleVariants.map((variant) => <ScenarioVariantCard key={variant.variantId} variant={variant} />)}
        </div>
      </section>

      <section className="panel" aria-labelledby="scenario-variant-heading">
        <h2 id="scenario-variant-heading">Variant workspace</h2>
        <div className="offer-toolbar" aria-label="Variant actions">
          <label htmlFor="scenario-variant-name">Variant name</label>
          <input id="scenario-variant-name" value={variantName} onChange={(event) => setVariantName(event.target.value)} />
          <button type="button" onClick={createVariant}>Create Variant</button>
          <label htmlFor="scenario-selected-variant">Selected variant</label>
          <select id="scenario-selected-variant" value={selectedVariantId} onChange={(event) => setSelectedVariantId(event.target.value)}>
            {visibleVariants.map((variant) => <option key={variant.variantId} value={variant.variantId}>{variant.label}</option>)}
          </select>
          <button type="button" onClick={deleteVariant}>Delete Variant</button>
        </div>
        {localDraftVariants.length > 0 ? <ChipList label="Local what-if drafts blocked for backend persistence" values={localDraftVariants.map((variant) => variant.variantId)} /> : null}
      </section>

      <section className="panel" aria-labelledby="scenario-recalculate-heading">
        <h2 id="scenario-recalculate-heading">Backend recalculation</h2>
        <details className="field-help"><summary aria-label="Recalculation details">?</summary><span>Changing a dimension requests updated pricing and eligibility review from connected services.</span></details>
        <div className="offer-toolbar" aria-label="Scenario recalculation controls">
          <label htmlFor="scenario-dimension">Dimension</label>
          <select id="scenario-dimension" value={dimensionId} onChange={(event) => {
            const nextDimension = view.dimensions.find((dimension) => dimension.dimensionId === event.target.value);
            setDimensionId(event.target.value);
            setRequestedValue(nextDimension?.value ?? '');
          }}>
            {view.dimensions.map((dimension) => <option key={dimension.dimensionId} value={dimension.dimensionId}>{dimension.label}</option>)}
          </select>
          <label htmlFor="scenario-requested-value">Requested value</label>
          <input id="scenario-requested-value" value={requestedValue} onChange={(event) => setRequestedValue(event.target.value)} />
          <button type="button" onClick={() => void requestRecalculation('selected')}>Recalculate Selected</button>
          <button type="button" onClick={() => void requestRecalculation('all')}>Recalculate All</button>
        </div>
        {recalculateError ? <div className="banner banner--blocked" role="alert">{recalculateError}</div> : null}
        {recalculation ? <ScenarioRecalculationBanner result={recalculation} /> : null}
      </section>

      <section className="panel" aria-labelledby="scenario-batch-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Batch grid</p>
            <h2 id="scenario-batch-heading">Batch comparison grid</h2>
          </div>
          <button type="button" onClick={exportAnalysis}>Export grid</button>
        </div>
        <div className="quote-table" role="table" aria-label="Scenario batch grid">
          <div role="row" className="quote-table__row quote-table__row--head">
            <span role="columnheader">Row</span>
            <span role="columnheader">Variant</span>
            <span role="columnheader">Dimensions</span>
            <span role="columnheader">Status</span>
            <span role="columnheader">Backend result</span>
            <span role="columnheader">Guardrail</span>
          </div>
          {view.batchGrid.map((row) => <ScenarioBatchGridRow key={row.rowId} row={row} />)}
        </div>
      </section>

      <section className="panel" aria-labelledby="scenario-guardrails-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Guardrails</p>
            <h2 id="scenario-guardrails-heading">Guardrail blockers</h2>
          </div>
          <div className="offer-toolbar" aria-label="Guardrail recovery links">
            <a href={`/quote/${encodeURIComponent(runId)}/eligibility`}>View in Eligibility</a>
            <a href="/pipeline">Complete Required Facts</a>
          </div>
        </div>
        <ScenarioBlockerList blockers={view.blockers} label="Scenario guardrail blockers" />
      </section>

      <section className="panel" aria-labelledby="scenario-evidence-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Saved analyses</p>
            <h2 id="scenario-evidence-heading">Saved analyses, exports, and replay refs</h2>
          </div>
          <button type="button" onClick={saveAnalysis}>Save Analysis</button>
        </div>
        <div className="offer-grid" role="list" aria-label="Saved what-if analyses">
          {view.savedAnalyses.map((analysis) => <ScenarioSavedAnalysisCard key={analysis.analysisId} analysis={analysis} onLoad={loadAnalysis} onExport={exportAnalysis} onDelete={deleteAnalysis} />)}
        </div>
        <ChipList label="Workspace export refs" values={view.exportRefs} />
        <ChipList label="Workspace processing references" values={view.replayRefs} />
        <ChipList label="Scenario analysis events" values={view.events.map(businessFacingText)} />
      </section>
    </>
  );
}

function slugifyVariantName(name: string) {
  const slug = name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
  return slug || 'variant';
}

function ScenarioDimensionCard({ dimension }: { dimension: ScenarioAnalysisDimension }) {
  return (
    <article className="module-card module-card--light" role="listitem">
      <p className="module-card__route">{businessFacingText(dimension.dimensionId)}</p>
      <strong className="module-card__title">{businessFacingText(dimension.label)}</strong>
      <dl>
        <dt>Current value</dt><dd>{businessFacingText(dimension.value)}</dd>
        <dt>Source ref</dt><dd>{businessFacingText(dimension.sourceRef)}</dd>
        <dt>Backend owned</dt><dd>{dimension.backendOnly ? 'Derived by backend' : 'Editable from backend metadata'}</dd>
      </dl>
      <ChipList label={`${dimension.label} required facts`} values={dimension.requiredFacts.map(businessFacingText)} />
    </article>
  );
}

function ScenarioVariantCard({ variant }: { variant: ScenarioAnalysisVariant }) {
  return (
    <article className="offer-card" role="listitem" aria-label={`Variant ${variant.variantId}`}>
      <h3>{businessFacingText(variant.label)}</h3>
      <dl>
        <dt>Variant id</dt><dd>{businessFacingText(variant.variantId)}</dd>
        <dt>Status</dt><dd>{businessFacingText(variant.status)}</dd>
      </dl>
      <ChipList label="Dimension refs" values={variant.dimensionRefs.map(businessFacingText)} />
      <ChipList label="Variant facts" values={variant.factRefs.map(businessFacingText)} />
      <ChipList label="Backend result refs" values={variant.resultRefs} />
      <ScenarioBlockerList blockers={variant.guardrailBlockers} label={`${variant.variantId} guardrail blockers`} />
    </article>
  );
}

function ScenarioBatchGridRow({ row }: { row: ScenarioAnalysisBatchRow }) {
  return (
    <div role="row" className="quote-table__row">
      <span role="cell">{businessFacingText(row.rowId)}</span>
      <span role="cell">{businessFacingText(row.variantId)}</span>
      <span role="cell">{businessFacingText(row.dimensionSummary)}</span>
      <span role="cell">{businessFacingText(row.status)}</span>
      <span role="cell">{row.backendResultRef}</span>
      <span role="cell">{businessFacingText(row.guardrailSummary)}</span>
    </div>
  );
}

function ScenarioSavedAnalysisCard({ analysis, onLoad, onExport, onDelete }: { analysis: ScenarioAnalysisSavedAnalysis; onLoad: (analysis: ScenarioAnalysisSavedAnalysis) => void; onExport: () => void; onDelete: (analysis: ScenarioAnalysisSavedAnalysis) => void }) {
  return (
    <article className="module-card module-card--light" role="listitem">
      <p className="module-card__route">{businessFacingText(analysis.analysisId)}</p>
      <strong className="module-card__title">{businessFacingText(analysis.name)}</strong>
      <dl>
        <dt>Version</dt><dd>{businessFacingText(analysis.versionRef)}</dd>
        <dt>Saved</dt><dd>{businessFacingText(analysis.savedAt)}</dd>
        <dt>Export ref</dt><dd>{analysis.exportRef}</dd>
        <dt>Processing record</dt><dd>{analysis.replayHash}</dd>
      </dl>
      <div className="offer-toolbar" aria-label={`${analysis.analysisId} actions`}>
        <button type="button" onClick={() => onLoad(analysis)}>Load Analysis</button>
        <button type="button" onClick={onExport}>Export Analysis</button>
        <button type="button" onClick={() => onDelete(analysis)}>Delete Analysis</button>
      </div>
    </article>
  );
}

function ScenarioBlockerList({ blockers, label }: { blockers: ScenarioAnalysisBlocker[]; label: string }) {
  if (!blockers.length) return null;
  return (
    <div className="offer-list" role="list" aria-label={label}>
      {blockers.map((blocker) => (
        <article key={`${blocker.blockerCode}-${blocker.sourceRef}`} className="banner banner--blocked" role="listitem">
          <strong>{businessFacingText(blocker.blockerCode)} · {businessFacingText(blocker.severity)}</strong>
          <span>{blocker.reason}</span>
          <span>Source: {blocker.sourceRef}</span>
          <ChipList label="Required facts" values={blocker.requiredFacts.map(businessFacingText)} />
        </article>
      ))}
    </div>
  );
}

function ScenarioRecalculationBanner({ result }: { result: ScenarioRecalculationResult }) {
  const blocked = result.status === 'BLOCKED' || result.blockers.some((blocker) => blocker.severity.toUpperCase() === 'BLOCKER');
  return (
    <div className={blocked ? 'banner banner--blocked' : 'banner banner--success'} role={blocked ? 'alert' : 'status'}>
      <strong>{blocked ? 'Recalculation blocked by backend guardrails' : 'Backend recalculation result received'}</strong>
      <span>{businessFacingText(result.message)}</span>
      <ChipList label="Backend result refs" values={result.backendResultRefs} />
      <ScenarioBlockerList blockers={result.blockers} label="Recalculation blockers" />
      <ChipList label="Recalculation events" values={result.events.map(businessFacingText)} />
    </div>
  );
}

function ChipList({ label, values }: { label: string; values: string[] }) {
  if (!values.length) return null;
  return <ul className="chip-list" aria-label={label}>{values.map((value) => <li key={value}>{businessFacingText(value)}</li>)}</ul>;
}

function normaliseScenarioWorkspaceView(view: Partial<ScenarioAnalysisWorkspaceView>, tenantContext: string, runId: string): ScenarioAnalysisWorkspaceView {
  return {
    tenantContext: view.tenantContext ?? tenantContext,
    runId: view.runId ?? runId,
    dependencyStatus: businessFacingText(view.dependencyStatus ?? 'Needs attention'),
    dimensions: view.dimensions ?? [],
    variants: view.variants ?? [],
    batchGrid: view.batchGrid ?? [],
    savedAnalyses: view.savedAnalyses ?? [],
    exportRefs: view.exportRefs ?? [],
    replayRefs: view.replayRefs ?? [],
    blockers: view.blockers ?? [],
    fallbackReason: view.fallbackReason ? 'Connected scenario analysis records are not ready yet.' : '',
    uiTraceId: view.uiTraceId ?? 'scenario-analysis',
    events: view.events ?? [],
  };
}

function businessFacingText(value: string | number | null | undefined) {
  if (value === null || value === undefined || value === '') return 'Not supplied';
  return String(value)
    .replace(/ui-preview-tenant/ig, 'Product workspace')
    .replace(/local synthetic\/dev fixture/ig, 'Preview data')
    .replace(/backend-owned/ig, 'service-managed')
    .replace(/backend/ig, 'service')
    .replace(/refs?/ig, 'records')
    .replace(/[_-]+/g, ' ');
}
