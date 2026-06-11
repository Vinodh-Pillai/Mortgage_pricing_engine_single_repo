import { useEffect, useMemo, useState } from 'react';
import { fetchPricingWaterfall, type PricingWaterfallView, type RedactedWaterfallValue, type WaterfallLedgerRow } from '../../lib/api/quoteRuns';
import type { ScreenProps, ScreenVisualState } from '../contract/ScreenProps';
import { deterministicPricingWaterfall } from './fixtures';

export type PricingWaterfallScreenProps = Partial<ScreenProps> & {
  waterfall?: PricingWaterfallView;
  fetchImpl?: typeof fetch;
  onNavigate?: (path: string) => void;
};

type LoadState = { kind: 'loading' } | { kind: 'loaded'; waterfall: PricingWaterfallView } | { kind: 'blocked'; message: string; waterfall: PricingWaterfallView };

const evidenceTarget = '.local-harness/evidence/PII-24-S14/pricing-waterfall.json';

export default function PricingWaterfallScreen({ tenantId = 'tenant-fixture', runId, uiTraceId = 'pii-24-s14-local-trace', waterfall, fetchImpl, onEvidenceCapture, onNavigate }: PricingWaterfallScreenProps) {
  const activeRunId = runId ?? waterfall?.runId ?? deterministicPricingWaterfall.runId;
  const [loadState, setLoadState] = useState<LoadState>(() => ({ kind: 'loaded', waterfall: waterfall ?? deterministicPricingWaterfall }));
  const activeWaterfall = loadState.kind === 'loaded' || loadState.kind === 'blocked' ? loadState.waterfall : deterministicPricingWaterfall;
  const visualState = stateForWaterfall(loadState);

  useEffect(() => {
    if (waterfall || !fetchImpl) return;
    let cancelled = false;
    setLoadState({ kind: 'loading' });
    fetchPricingWaterfall(tenantId, activeRunId, fetchImpl)
      .then((nextWaterfall) => {
        if (!cancelled) setLoadState({ kind: 'loaded', waterfall: nextWaterfall });
      })
      .catch((error: Error) => {
        if (!cancelled) setLoadState({ kind: 'blocked', message: error.message, waterfall: { ...deterministicPricingWaterfall, runId: activeRunId, status: 'BLOCKED' } });
      });
    return () => { cancelled = true; };
  }, [activeRunId, fetchImpl, tenantId, waterfall]);

  useEffect(() => {
    onEvidenceCapture?.({
      screenId: 'pricing-waterfall',
      timestamp: new Date().toISOString(),
      state: visualState,
      dataRefs: [tenantId, activeRunId, activeWaterfall.uiTraceId, uiTraceId, activeWaterfall.replayHash, activeWaterfall.evidenceHash, evidenceTarget],
      blockers: loadState.kind === 'blocked' ? [loadState.message, ...activeWaterfall.blockers.map((blocker) => blocker.code)] : activeWaterfall.blockers.map((blocker) => blocker.code),
    });
  }, [activeRunId, activeWaterfall, loadState, onEvidenceCapture, tenantId, uiTraceId, visualState]);

  if (loadState.kind === 'loading') {
    return <main className="pricing-waterfall-screen" aria-busy="true"><section className="panel"><p role="status">Loading pricing waterfall sections...</p></section></main>;
  }

  return <WaterfallLayout waterfall={activeWaterfall} visualState={visualState} onNavigate={onNavigate} />;
}

export function WaterfallLayout({ waterfall, visualState, onNavigate }: { waterfall: PricingWaterfallView; visualState: ScreenVisualState; onNavigate?: (path: string) => void }) {
  const [sectionFilter, setSectionFilter] = useState('all');
  const [reasonFilter, setReasonFilter] = useState('');
  const [configFilter, setConfigFilter] = useState('');
  const [expandedSteps, setExpandedSteps] = useState<Set<number>>(new Set());
  const [exportText, setExportText] = useState('');
  const groups = useMemo(() => groupLedger(waterfall.finalPrice.ledger), [waterfall.finalPrice.ledger]);
  const filteredRows = useMemo(() => waterfall.finalPrice.ledger.filter((row) => {
    const sectionMatches = sectionFilter === 'all' || sectionFor(row) === sectionFilter;
    const reasonMatches = !reasonFilter || row.reasonCode.toLowerCase().includes(reasonFilter.toLowerCase());
    const configMatches = !configFilter || row.configRef.toLowerCase().includes(configFilter.toLowerCase());
    return sectionMatches && reasonMatches && configMatches;
  }), [configFilter, reasonFilter, sectionFilter, waterfall.finalPrice.ledger]);
  const totalAdjustmentRows = waterfall.finalPrice.ledger.filter((row) => sectionFor(row) === 'Adjustments').length;

  function toggleRow(ordinal: number) {
    setExpandedSteps((current) => {
      const next = new Set(current);
      if (next.has(ordinal)) next.delete(ordinal);
      else next.add(ordinal);
      return next;
    });
  }

  function expandAllVisible() {
    setExpandedSteps(new Set(filteredRows.map((row) => row.ordinal)));
  }

  function navigate(path: string) {
    onNavigate?.(path);
  }

  return (
    <main className="pricing-waterfall-screen" aria-labelledby="pricing-waterfall-title">
      <header className="hero" style={{ position: 'sticky', top: 0, zIndex: 1 }}>
        <p className="eyebrow">Pricing waterfall | PII-24-S14</p>
        <h1 id="pricing-waterfall-title">Pricing Waterfall</h1>
        <p>Run <code>{waterfall.runId}</code>. The screen renders pricing-service data, refs, hashes, and redaction metadata without local pricing calculation.</p>
        <div role="toolbar" aria-label="Waterfall exports">
          <button type="button" onClick={() => setExportText(exportWaterfallCsv(waterfall))}>Export CSV</button>{' '}
          <button type="button" onClick={() => setExportText(exportWaterfallJson(waterfall))}>Export JSON</button>{' '}
          <button type="button" disabled title="PDF export requires approved local PDF tooling">Export PDF unavailable</button>
        </div>
      </header>

      {visualState === 'blocked' ? <div className="banner banner--blocked" role="alert">Pricing waterfall is blocked by backend evidence boundaries.</div> : null}
      {hasRedactions(waterfall) ? <div className="banner banner--info">Redacted backend values include reason and audit refs.</div> : null}

      <section className="quote-detail-layout" aria-label="Pricing waterfall layout" style={{ display: 'grid', gap: '1rem', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 22rem), 1fr))' }}>
        <BaseSelectionPanel waterfall={waterfall} totalAdjustmentRows={totalAdjustmentRows} onNavigate={navigate} />
        <FinalPricePanel waterfall={waterfall} onNavigate={navigate} />
        <BlockersPanel waterfall={waterfall} onNavigate={navigate} />
        <EvidenceRefs waterfall={waterfall} onNavigate={navigate} />
      </section>

      <LedgerPanel groups={groups} rows={filteredRows} expandedSteps={expandedSteps} sectionFilter={sectionFilter} reasonFilter={reasonFilter} configFilter={configFilter} onSectionFilter={setSectionFilter} onReasonFilter={setReasonFilter} onConfigFilter={setConfigFilter} onToggleRow={toggleRow} onExpandAll={expandAllVisible} onCollapseAll={() => setExpandedSteps(new Set())} onNavigate={navigate} />
      {exportText ? <textarea aria-label="Exported pricing waterfall" readOnly value={exportText} rows={12} /> : null}
    </main>
  );
}

function BaseSelectionPanel({ waterfall, totalAdjustmentRows, onNavigate }: { waterfall: PricingWaterfallView; totalAdjustmentRows: number; onNavigate: (path: string) => void }) {
  return (
    <section className="panel" aria-labelledby="base-selection-heading">
      <h2 id="base-selection-heading">Base Selection</h2>
      <dl className="status-grid">
        <dt>Grid version</dt><dd><RefButton value={waterfall.baseSelection.gridVersionRef} onNavigate={onNavigate} /></dd>
        <dt>Selected note rate</dt><dd>{redactedValue(waterfall.baseSelection.selectedNoteRate, onNavigate)}</dd>
        <dt>Base price</dt><dd>{redactedValue(waterfall.baseSelection.basePrice, onNavigate)}</dd>
        <dt>Ledger steps</dt><dd>{waterfall.finalPrice.ledger.length}</dd>
        <dt>Total adjustment rows</dt><dd>{totalAdjustmentRows}</dd>
      </dl>
    </section>
  );
}

function LedgerPanel(props: { groups: Map<string, WaterfallLedgerRow[]>; rows: WaterfallLedgerRow[]; expandedSteps: Set<number>; sectionFilter: string; reasonFilter: string; configFilter: string; onSectionFilter: (value: string) => void; onReasonFilter: (value: string) => void; onConfigFilter: (value: string) => void; onToggleRow: (ordinal: number) => void; onExpandAll: () => void; onCollapseAll: () => void; onNavigate: (path: string) => void }) {
  const { groups, rows, expandedSteps, sectionFilter, reasonFilter, configFilter, onSectionFilter, onReasonFilter, onConfigFilter, onToggleRow, onExpandAll, onCollapseAll, onNavigate } = props;
  return (
    <section className="panel" aria-labelledby="ledger-heading">
      <h2 id="ledger-heading">Ledger</h2>
      <div className="status-grid" aria-label="Ledger grouping summary">
        {Array.from(groups.entries()).map(([section, groupedRows]) => <><dt key={`${section}-label`}>{section}</dt><dd key={`${section}-count`}>{groupedRows.length} rows</dd></>)}
      </div>
      <div role="search" aria-label="Ledger filters">
        <label>Group by section <select value={sectionFilter} onChange={(event) => onSectionFilter(event.target.value)}><option value="all">All</option>{Array.from(groups.keys()).map((section) => <option key={section} value={section}>{section}</option>)}</select></label>{' '}
        <label>Reason code <input value={reasonFilter} onChange={(event) => onReasonFilter(event.target.value)} /></label>{' '}
        <label>Config ref <input value={configFilter} onChange={(event) => onConfigFilter(event.target.value)} /></label>{' '}
        <button type="button" onClick={onExpandAll}>Expand All</button>{' '}
        <button type="button" onClick={onCollapseAll}>Collapse All</button>
      </div>
      <div style={{ maxHeight: '34rem', overflow: 'auto' }} aria-label="Virtualized ledger viewport">
        <table className="ds-table" aria-label="Pricing waterfall ledger">
          <thead><tr><th scope="col">Ordinal</th><th scope="col">Step</th><th scope="col">Input Value</th><th scope="col">Operation</th><th scope="col">Output Value</th><th scope="col">Config Ref</th><th scope="col">Reason Code</th><th scope="col">Rounding Mode</th></tr></thead>
          <tbody>{rows.map((row) => <LedgerRow key={row.ordinal} row={row} expanded={expandedSteps.has(row.ordinal)} onToggle={() => onToggleRow(row.ordinal)} onNavigate={onNavigate} />)}</tbody>
        </table>
      </div>
    </section>
  );
}

function LedgerRow({ row, expanded, onToggle, onNavigate }: { row: WaterfallLedgerRow; expanded: boolean; onToggle: () => void; onNavigate: (path: string) => void }) {
  return <>
    <tr>
      <td><button type="button" aria-expanded={expanded} onClick={onToggle}>{row.ordinal}</button></td>
      <td>{row.step}</td><td>{redactedValue(row.inputValue, onNavigate)}</td><td>{row.operation}</td><td>{redactedValue(row.outputValue, onNavigate)}</td><td><RefButton value={row.configRef} onNavigate={onNavigate} /></td><td>{row.reasonCode}</td><td>{valueText(row.roundingMode)}</td>
    </tr>
    {expanded ? <tr><td colSpan={8}><ChipList label={`Step ${row.ordinal} details`} values={[...(row.inputDetails ?? []), ...(row.outputDetails ?? []), ...(row.adjustmentRefs ?? []), ...(row.marginRefs ?? [])]} /></td></tr> : null}
  </>;
}

function FinalPricePanel({ waterfall, onNavigate }: { waterfall: PricingWaterfallView; onNavigate: (path: string) => void }) {
  return <section className="panel" aria-labelledby="final-price-heading"><h2 id="final-price-heading">Final Price</h2><dl className="status-grid"><dt>Rounded final price</dt><dd>{redactedValue(waterfall.finalPrice.roundedFinalPrice, onNavigate)}</dd><dt>Rounding mode</dt><dd>{valueText(waterfall.finalPrice.roundingMode)}</dd><dt>Precision</dt><dd>{valueText(waterfall.finalPrice.precision)}</dd></dl><ChipList label="Ledger step refs" values={waterfall.finalPrice.ledger.slice(0, 8).map((row) => `step:${row.ordinal}:${row.reasonCode}`)} /><ChipList label="Adjustment refs" values={waterfall.finalPrice.adjustmentRefs} onNavigate={onNavigate} /><ChipList label="Margin refs" values={waterfall.finalPrice.marginRefs ?? []} onNavigate={onNavigate} /><ChipList label="Rounding trace refs" values={waterfall.finalPrice.roundingTraceRefs} onNavigate={onNavigate} /></section>;
}

function BlockersPanel({ waterfall, onNavigate }: { waterfall: PricingWaterfallView; onNavigate: (path: string) => void }) {
  return <section className="panel" aria-labelledby="blockers-heading"><h2 id="blockers-heading">Blockers</h2>{waterfall.blockers.length === 0 ? <p>No blockers returned.</p> : <ul>{waterfall.blockers.map((blocker) => <li key={`${blocker.code}-${blocker.sourceRef}`}><strong>{blocker.code}</strong><p>{blocker.message}</p><RefButton value={blocker.sourceRef} onNavigate={onNavigate} />{blocker.remediation ? <p>{blocker.remediation}</p> : null}</li>)}</ul>}</section>;
}

function EvidenceRefs({ waterfall, onNavigate }: { waterfall: PricingWaterfallView; onNavigate: (path: string) => void }) {
  return <section className="panel" aria-labelledby="evidence-heading"><h2 id="evidence-heading">Evidence Refs</h2><dl className="status-grid"><dt>Replay hash</dt><dd><button type="button" onClick={() => onNavigate(`/audit/replay?ref=${encodeURIComponent(waterfall.replayHash)}`)}>{waterfall.replayHash}</button></dd><dt>Evidence hash</dt><dd><code>{waterfall.evidenceHash}</code></dd><dt>Result hash</dt><dd><code>{waterfall.resultHash}</code></dd><dt>Version graph hash</dt><dd><code>{waterfall.versionGraphHash}</code></dd></dl><ChipList label="Version refs" values={waterfall.versionRefs} onNavigate={onNavigate} /><ChipList label="Audit refs" values={waterfall.auditRefs} onNavigate={onNavigate} /></section>;
}

function ChipList({ label, values, onNavigate }: { label: string; values: string[]; onNavigate?: (path: string) => void }) {
  return <div className="copyable-ref-list" aria-label={label}><strong>{label}</strong>{values.length === 0 ? <p>N/A</p> : <ul>{values.map((value) => <li key={value}>{onNavigate ? <RefButton value={value} onNavigate={onNavigate} /> : <code>{value}</code>}</li>)}</ul>}</div>;
}

function RefButton({ value, onNavigate }: { value: string; onNavigate: (path: string) => void }) {
  return <button type="button" className="button-secondary" onClick={() => onNavigate(routeForRef(value))}><code>{value}</code></button>;
}

function redactedValue(value: RedactedWaterfallValue, onNavigate: (path: string) => void) {
  if (!value.redacted) return <span>{valueText(value.value)}</span>;
  const reason = value.reason ?? 'REDACTED_BY_BACKEND';
  const auditRef = value.auditRef ?? 'audit-ref-unavailable';
  return <span title={`Reason: ${reason}; audit ref: ${auditRef}`}><strong>[REDACTED]</strong> <small>{reason} | <button type="button" onClick={() => onNavigate(`/audit/replay?ref=${encodeURIComponent(auditRef)}`)}>{auditRef}</button></small></span>;
}

export function exportWaterfallJson(waterfall: PricingWaterfallView) {
  return JSON.stringify({ waterfall, redactions: redactionMetadata(waterfall) }, null, 2);
}

export function exportWaterfallCsv(waterfall: PricingWaterfallView) {
  const header = 'ordinal,section,step,input,redactedInputReason,inputAuditRef,operation,output,redactedOutputReason,outputAuditRef,configRef,reasonCode,roundingMode';
  const rows = waterfall.finalPrice.ledger.map((row) => [row.ordinal, sectionFor(row), row.step, valueText(row.inputValue.value), row.inputValue.redacted ? valueText(row.inputValue.reason) : '', valueText(row.inputValue.auditRef), row.operation, valueText(row.outputValue.value), row.outputValue.redacted ? valueText(row.outputValue.reason) : '', valueText(row.outputValue.auditRef), row.configRef, row.reasonCode, valueText(row.roundingMode)].map(csvCell).join(','));
  return [header, ...rows].join('\n');
}

export function stateForWaterfall(loadState: LoadState): ScreenVisualState {
  if (loadState.kind === 'loading') return 'loading';
  if (loadState.kind === 'blocked' || loadState.waterfall.status === 'BLOCKED') return 'blocked';
  if (loadState.waterfall.finalPrice.ledger.length === 0) return 'empty';
  if (hasRedactions(loadState.waterfall) || loadState.waterfall.replayHash) return 'needs-attention';
  return 'ready';
}

function redactionMetadata(waterfall: PricingWaterfallView) {
  return waterfall.finalPrice.ledger.flatMap((row) => [
    { ordinal: row.ordinal, field: 'inputValue', reason: row.inputValue.reason, auditRef: row.inputValue.auditRef, redacted: row.inputValue.redacted },
    { ordinal: row.ordinal, field: 'outputValue', reason: row.outputValue.reason, auditRef: row.outputValue.auditRef, redacted: row.outputValue.redacted },
  ]).filter((entry) => entry.redacted);
}

function hasRedactions(waterfall: PricingWaterfallView) {
  return waterfall.finalPrice.ledger.some((row) => row.inputValue.redacted || row.outputValue.redacted) || waterfall.baseSelection.selectedNoteRate.redacted || waterfall.baseSelection.basePrice.redacted || waterfall.finalPrice.roundedFinalPrice.redacted;
}

function groupLedger(rows: WaterfallLedgerRow[]) {
  return rows.reduce((groups, row) => groups.set(sectionFor(row), [...(groups.get(sectionFor(row)) ?? []), row]), new Map<string, WaterfallLedgerRow[]>());
}

function sectionFor(row: WaterfallLedgerRow) {
  return row.section ?? (row.operation.includes('ROUNDING') ? 'Rounding' : row.operation.includes('MARGIN') ? 'Margins' : row.operation.includes('ADJUSTMENT') ? 'Adjustments' : 'Base Rate');
}

function routeForRef(ref: string) {
  if (ref.startsWith('adjustment-service:')) return `/pricing/adjustments?ref=${encodeURIComponent(ref)}`;
  if (ref.startsWith('margin-service:')) return `/pricing/margins?ref=${encodeURIComponent(ref)}`;
  if (ref.startsWith('audit:')) return `/audit/replay?ref=${encodeURIComponent(ref)}`;
  if (ref.startsWith('pricing-service:rounding')) return `/quote/rounding?ref=${encodeURIComponent(ref)}`;
  return `/governance/config?ref=${encodeURIComponent(ref)}`;
}

function valueText(value: unknown) {
  return value === null || value === undefined || value === '' ? 'N/A' : String(value);
}

function csvCell(value: unknown) {
  return `"${valueText(value).replace(/"/g, '""')}"`;
}
