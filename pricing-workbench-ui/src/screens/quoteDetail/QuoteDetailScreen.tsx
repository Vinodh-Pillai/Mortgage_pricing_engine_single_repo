import { useEffect, useMemo, useState } from 'react';
import type { QuoteDetailRedaction, QuoteDetailView } from '../../lib/api/offers';
import type { PricingWaterfallView, RedactedWaterfallValue } from '../../lib/api/quoteRuns';
import type { ScreenProps } from '../contract/ScreenProps';
import { deterministicQuoteDetail } from './fixtures';

export type QuoteDetailScreenProps = Partial<ScreenProps> & {
  detail?: QuoteDetailView;
  onNavigate?: (path: string) => void;
};

type PanelId = 'summary' | 'waterfall' | 'compliance' | 'audit';

const panelOrder: Array<{ id: PanelId; label: string }> = [
  { id: 'summary', label: 'Summary' },
  { id: 'waterfall', label: 'Waterfall' },
  { id: 'compliance', label: 'Compliance' },
  { id: 'audit', label: 'Audit / Replay' },
];

export default function QuoteDetailScreen({ tenantId = 'tenant-fixture', runId, optionId, uiTraceId = 'pii-24-s11-local-trace', detail = deterministicQuoteDetail, onEvidenceCapture, onNavigate }: QuoteDetailScreenProps) {
  const activeRunId = runId ?? detail.runId;
  const activeOfferId = optionId ?? detail.offerId;
  const [activePanel, setActivePanel] = useState<PanelId>('summary');
  const [exportText, setExportText] = useState('');
  const visualState = stateForDetail(detail);

  useEffect(() => {
    onEvidenceCapture?.({
      screenId: 'quote-detail',
      timestamp: new Date().toISOString(),
      state: visualState,
      dataRefs: [tenantId, activeRunId, activeOfferId, detail.uiTraceId, uiTraceId, detail.replayHash, detail.evidenceHash],
      blockers: visualState === 'blocked' ? detail.panels.flatMap((panel) => panel.blockers) : [],
    });
  }, [activeOfferId, activeRunId, detail, onEvidenceCapture, tenantId, uiTraceId, visualState]);

  const panelStatus = useMemo(() => Object.fromEntries(detail.panels.map((panel) => [panel.panelId, panel.status])), [detail.panels]);

  function navigate(path: string) {
    onNavigate?.(path);
  }

  return (
    <main className="quote-detail-screen" aria-labelledby="quote-detail-title">
      <section className="hero" aria-labelledby="quote-detail-title">
        <p className="eyebrow">Quote detail | PII-24-S11</p>
        <h1 id="quote-detail-title">Quote Detail Waterfall</h1>
        <p>Backend evidence for one quote option. The UI renders returned values, references, hashes, and redaction metadata without pricing calculations.</p>
        <div className="status-grid" aria-label="Offer summary header">
          <dt>Run</dt><dd><code>{activeRunId}</code></dd>
          <dt>Offer</dt><dd><code>{activeOfferId}</code></dd>
          <dt>Product</dt><dd>{valueText(detail.summary.productLabel)}</dd>
          <dt>Rate / Price</dt><dd>{valueText(detail.summary.rate)} / {redactedValue(detail.waterfall.finalPrice.roundedFinalPrice, redactionFor(detail.redactions, 'waterfall.finalPrice.roundedFinalPrice'), navigate)}</dd>
        </div>
        <button type="button" className="button-secondary" onClick={() => navigate(`/quote/${encodeURIComponent(activeRunId)}/offers`)}>Back to offers</button>
      </section>

      {visualState === 'blocked' ? <div className="banner banner--blocked" role="alert">Quote detail is blocked by backend evidence boundaries.</div> : null}
      {visualState === 'empty' ? <div className="banner banner--info">No quote detail data is available for this option.</div> : null}

      <nav className="panel" aria-label="Quote detail panel navigation">
        <div className="ds-tab-list" role="tablist" aria-label="Quote detail panels">
          {panelOrder.map((panel) => (
            <button key={panel.id} type="button" role="tab" aria-selected={activePanel === panel.id} aria-controls={`${panel.id}-panel`} onClick={() => setActivePanel(panel.id)}>
              {panel.label} <span className="trace-badge">{panelStatus[panel.id] ?? 'READY'}</span>
            </button>
          ))}
        </div>
      </nav>

      <section className="quote-detail-layout" aria-label="Responsive quote detail layout" style={{ display: 'grid', gap: '1rem', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 20rem), 1fr))' }}>
        <div id="summary-panel" role="tabpanel" aria-label="Summary and explanation panel" hidden={false}>
          <SummaryPanel detail={detail} active={activePanel === 'summary'} onNavigate={navigate} />
          <ExplanationPanel detail={detail} />
        </div>
        <div id="waterfall-panel" role="tabpanel" aria-label="Pricing waterfall panel" hidden={false}>
          <WaterfallPanel waterfall={detail.waterfall} redactions={detail.redactions} exportText={exportText} onExport={setExportText} onNavigate={navigate} />
        </div>
        <div id="compliance-panel" role="tabpanel" aria-label="Compliance and audit panel" hidden={false}>
          <CompliancePanel flags={detail.complianceFlags} onNavigate={navigate} />
          <AuditReplayPanel detail={detail} onNavigate={navigate} />
        </div>
      </section>
    </main>
  );
}

function SummaryPanel({ detail, onNavigate }: { detail: QuoteDetailView; active: boolean; onNavigate: (path: string) => void }) {
  const { summary } = detail;
  return (
    <section className="panel" aria-labelledby="summary-heading">
      <h2 id="summary-heading">Summary</h2>
      <dl className="status-grid">
        <dt>Product</dt><dd>{valueText(summary.productLabel)}</dd>
        <dt>Investor</dt><dd>{valueText(summary.investor)}</dd>
        <dt>Channel</dt><dd>{valueText(summary.productFamily)}</dd>
        <dt>Lock period</dt><dd>{valueText(summary.lockPeriodDays)}</dd>
        <dt>Note rate</dt><dd>{valueText(summary.rate)}</dd>
        <dt>APR</dt><dd>{valueText(summary.apr)}</dd>
        <dt>Payment</dt><dd>{valueText(summary.payment)}</dd>
        <dt>Final price</dt><dd>{redactedValue(detail.waterfall.finalPrice.roundedFinalPrice, redactionFor(detail.redactions, 'waterfall.finalPrice.roundedFinalPrice'), onNavigate)}</dd>
        <dt>Confidence</dt><dd>{valueText(summary.confidence)}</dd>
        <dt>Rank / score</dt><dd>{summary.rank} / {valueText(summary.rankScore)}</dd>
        <dt>Source scenario</dt><dd>{valueText(summary.sourceScenarioId)} v{valueText(summary.scenarioVersion)}</dd>
      </dl>
      <ChipList label="Scenario flags" values={summary.scenarioFlags} />
      <ChipList label="Backend adjustment and margin summaries" values={summary.explanationSections ?? []} />
    </section>
  );
}

function ExplanationPanel({ detail }: { detail: QuoteDetailView }) {
  return (
    <section className="panel" aria-labelledby="explanation-heading">
      <h2 id="explanation-heading">Explanation</h2>
      <p>{detail.explanation.message}</p>
      <ul>
        {detail.explanation.rationaleLines.map((line) => <li key={line}>{line}</li>)}
      </ul>
      <ChipList label="Upstream refs" values={detail.explanation.upstreamRefs ?? []} />
      <ChipList label="Snapshot refs" values={detail.explanation.snapshotRefs ?? []} />
      <ChipList label="Audit IDs" values={detail.explanation.auditIds ?? []} />
      <button type="button" onClick={() => void navigator.clipboard?.writeText(detail.explanation.rationaleLines.join('\n'))}>Copy Explanation</button>
    </section>
  );
}

function WaterfallPanel({ waterfall, redactions, exportText, onExport, onNavigate }: { waterfall: PricingWaterfallView; redactions: QuoteDetailRedaction[]; exportText: string; onExport: (text: string) => void; onNavigate: (path: string) => void }) {
  return (
    <section className="panel" aria-labelledby="waterfall-heading">
      <h2 id="waterfall-heading">Pricing Waterfall</h2>
      <details open>
        <summary>Base selection</summary>
        <dl className="status-grid">
          <dt>Grid version</dt><dd><code>{waterfall.baseSelection.gridVersionRef}</code></dd>
          <dt>Selected note rate</dt><dd>{redactedValue(waterfall.baseSelection.selectedNoteRate, redactionFor(redactions, 'waterfall.baseSelection.selectedNoteRate'), onNavigate)}</dd>
          <dt>Base price</dt><dd>{redactedValue(waterfall.baseSelection.basePrice, redactionFor(redactions, 'waterfall.baseSelection.basePrice'), onNavigate)}</dd>
        </dl>
      </details>
      <details open>
        <summary>Ledger rows</summary>
        <table className="ds-table" aria-label="Pricing waterfall ledger">
          <thead>
            <tr>
              <th scope="col">#</th>
              <th scope="col">Step</th>
              <th scope="col">Input</th>
              <th scope="col">Operation</th>
              <th scope="col">Output</th>
              <th scope="col">Config ref</th>
              <th scope="col">Reason</th>
              <th scope="col">Rounding</th>
            </tr>
          </thead>
          <tbody>
            {waterfall.finalPrice.ledger.map((row) => (
              <tr key={row.ordinal}>
                <td>{row.ordinal}</td>
                <td>{row.step}</td>
                <td>{redactedValue(row.inputValue, redactionFor(redactions, `waterfall.finalPrice.ledger[${row.ordinal - 1}].inputValue`), onNavigate)}</td>
                <td>{row.operation}</td>
                <td>{redactedValue(row.outputValue, redactionFor(redactions, `waterfall.finalPrice.ledger[${row.ordinal - 1}].outputValue`), onNavigate)}</td>
                <td><code>{row.configRef}</code></td>
                <td>{row.reasonCode}</td>
                <td>{valueText(row.roundingMode)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
      <details open>
        <summary>Final price and trace refs</summary>
        <p>Rounded final price: {redactedValue(waterfall.finalPrice.roundedFinalPrice, redactionFor(redactions, 'waterfall.finalPrice.roundedFinalPrice'), onNavigate)}</p>
        <ChipList label="Adjustment refs" values={waterfall.finalPrice.adjustmentRefs} />
        <ChipList label="Rounding trace refs" values={waterfall.finalPrice.roundingTraceRefs} />
      </details>
      <button type="button" onClick={() => onExport(exportWaterfall(waterfall, redactions))}>Export Waterfall</button>
      {exportText ? <textarea aria-label="Exported waterfall data" readOnly value={exportText} /> : null}
    </section>
  );
}

function CompliancePanel({ flags, onNavigate }: { flags: string[]; onNavigate: (path: string) => void }) {
  return (
    <section className="panel" aria-labelledby="compliance-heading">
      <h2 id="compliance-heading">Compliance</h2>
      {flags.length === 0 ? <p>No compliance flags returned.</p> : (
        <ul className="offer-list">
          {flags.map((flag) => {
            const parsed = parseComplianceFlag(flag);
            return (
              <li key={flag}>
                <strong>{parsed.code}</strong> <span className="trace-badge">{parsed.severity}</span>
                <p>{parsed.jurisdiction}</p>
                <button type="button" onClick={() => onNavigate(parsed.target)}>Open compliance evidence</button>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}

function AuditReplayPanel({ detail, onNavigate }: { detail: QuoteDetailView; onNavigate: (path: string) => void }) {
  return (
    <section className="panel" aria-labelledby="audit-heading">
      <h2 id="audit-heading">Audit / Replay</h2>
      <dl className="status-grid">
        <dt>Replay hash</dt><dd><button type="button" className="button-secondary" onClick={() => void navigator.clipboard?.writeText(detail.replayHash)}>{detail.replayHash}</button></dd>
        <dt>Evidence hash</dt><dd><code>{detail.evidenceHash}</code></dd>
        <dt>Result hash</dt><dd><code>{detail.waterfall.resultHash}</code></dd>
        <dt>Version graph hash</dt><dd><code>{detail.waterfall.versionGraphHash}</code></dd>
      </dl>
      <ChipList label="Version refs" values={detail.waterfall.versionRefs} />
      <ChipList label="Audit refs" values={detail.auditRefs} />
      <ChipList label="Events timeline" values={detail.events} />
      <button type="button" onClick={() => onNavigate(`/audit/replay?ref=${encodeURIComponent(detail.replayHash)}`)}>Open audit replay</button>
    </section>
  );
}

function ChipList({ label, values }: { label: string; values: string[] }) {
  return (
    <div className="copyable-ref-list" aria-label={label}>
      <strong>{label}</strong>
      {values.length === 0 ? <p>N/A</p> : <ul>{values.map((value) => <li key={value}><code>{value}</code></li>)}</ul>}
    </div>
  );
}

function redactedValue(value: RedactedWaterfallValue, redaction: QuoteDetailRedaction | undefined, onNavigate: (path: string) => void) {
  if (!value.redacted) return <span>{valueText(value.value)}</span>;
  const reason = redaction?.reason ?? value.reason ?? 'REDACTED_BY_BACKEND';
  const auditRef = redaction?.auditRef ?? 'audit-ref-unavailable';
  return (
    <span title={`Reason: ${reason}; audit ref: ${auditRef}`}>
      <strong>[REDACTED]</strong>{' '}
      <small>{reason} | <code>{auditRef}</code></small>{' '}
      <button type="button" className="button-secondary" onClick={() => onNavigate(`/audit/replay?ref=${encodeURIComponent(auditRef)}`)}>Request Access</button>
    </span>
  );
}

function redactionFor(redactions: QuoteDetailRedaction[], fieldPath: string) {
  return redactions.find((redaction) => redaction.fieldPath === fieldPath);
}

function parseComplianceFlag(flag: string) {
  const [code = flag, severity = 'BACKEND_SUPPLIED', jurisdiction = 'Evidence target from backend flag', target = `/compliance/evidence/${encodeURIComponent(flag)}`] = flag.split('|');
  return { code, severity, jurisdiction, target };
}

function exportWaterfall(waterfall: PricingWaterfallView, redactions: QuoteDetailRedaction[]) {
  const rows = waterfall.finalPrice.ledger.map((row) => [row.ordinal, row.step, valueText(row.inputValue.value), row.inputValue.redacted ? row.inputValue.reason : '', row.operation, valueText(row.outputValue.value), row.outputValue.redacted ? row.outputValue.reason : '', row.configRef, row.reasonCode, valueText(row.roundingMode)].join(','));
  return [`# JSON`, JSON.stringify({ waterfall, redactions }, null, 2), `# CSV`, 'ordinal,step,input,redactedInputReason,operation,output,redactedOutputReason,configRef,reasonCode,roundingMode', ...rows].join('\n');
}

function stateForDetail(detail: QuoteDetailView) {
  if (!detail.summary || !detail.waterfall) return 'empty';
  if (detail.status === 'BLOCKED' || detail.panels.some((panel) => panel.status === 'BLOCKED')) return 'blocked';
  if (detail.redactions.length > 0 || detail.complianceFlags.length > 0) return 'needs-attention';
  return 'ready';
}

function valueText(value: string | number | null | undefined) {
  if (value === null || value === undefined || value === '') return 'N/A';
  return String(value);
}
