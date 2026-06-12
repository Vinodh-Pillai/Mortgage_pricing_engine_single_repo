import { useEffect, useMemo, useState } from 'react';
import { DiagnosticsDetails } from '../../../components/DiagnosticsDetails';
import {
  fetchFicoSensitivity,
  type FicoSensitivityBand,
  type FicoSensitivityEligibilityCell,
  type FicoSensitivityView,
  type ScenarioAnalysisBlocker,
} from '../../../lib/api/scenarioAnalysis';

type FicoSensitivityState =
  | { kind: 'loading' }
  | { kind: 'loaded'; view: FicoSensitivityView }
  | { kind: 'unreachable'; message: string };

type ChartTab = 'rate' | 'price' | 'heatmap';

const CHART_WIDTH = 720;
const CHART_HEIGHT = 280;
const CHART_PADDING = 36;

export function FicoSensitivityScreen({ runId, tenantContext }: { runId: string; tenantContext: string }) {
  const [state, setState] = useState<FicoSensitivityState>({ kind: 'loading' });
  const [activeChart, setActiveChart] = useState<ChartTab>('rate');
  const [sortKey, setSortKey] = useState<'band' | 'rate' | 'price' | 'eligibility'>('band');
  const [selectedCell, setSelectedCell] = useState<FicoSensitivityEligibilityCell | null>(null);
  const [csvPreview, setCsvPreview] = useState('');

  useEffect(() => {
    let active = true;
    fetchFicoSensitivity(tenantContext, runId)
      .then((view) => {
        if (active) setState({ kind: 'loaded', view });
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'FICO sensitivity analysis is unavailable.';
        if (active) setState({ kind: 'unreachable', message });
      });
    return () => {
      active = false;
    };
  }, [runId, tenantContext]);

  if (state.kind === 'loading') {
    return (
      <section className="panel" aria-labelledby="fico-sensitivity-heading">
        <h2 id="fico-sensitivity-heading">FICO Sensitivity Analysis</h2>
        <p role="status">Loading FICO sensitivity analysis...</p>
      </section>
    );
  }

  if (state.kind === 'unreachable') {
    return (
      <section className="panel" aria-labelledby="fico-sensitivity-heading">
        <h2 id="fico-sensitivity-heading">FICO Sensitivity Analysis</h2>
        <div className="banner banner--blocked" role="alert">{state.message}</div>
      </section>
    );
  }

  const view = state.view;
  const currentBand = findCurrentBand(view.bands, view.currentFico);
  const outsideModeledRange = typeof view.currentFico === 'number' && view.bands.length > 0 && !currentBand;
  const csv = () => buildCsv(view.bands);

  return (
    <>
      <section className="hero" aria-labelledby="fico-sensitivity-title">
        <p className="eyebrow">FICO sensitivity · PII-24-S30</p>
        <h2 id="fico-sensitivity-title">FICO Sensitivity Analysis</h2>
        <p>
          Review backend-supplied FICO score bands, note rate, final price, payment, APR, eligibility, and evidence refs for run {runId}.
          The workbench does not calculate mortgage pricing, eligibility, or score-band rules in the browser.
        </p>
        <a href={`/quote/${encodeURIComponent(runId)}/what-if`}>Back to Scenario Analysis</a>
      </section>

      <FicoLayout view={view} currentBand={currentBand} outsideModeledRange={outsideModeledRange} />

      <section className="panel" aria-labelledby="fico-bands-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Backend bands</p>
            <h2 id="fico-bands-heading">Bands Table</h2>
          </div>
          <div className="offer-toolbar" aria-label="FICO band actions">
            <label htmlFor="fico-sort">Sort by</label>
            <select id="fico-sort" value={sortKey} onChange={(event) => setSortKey(event.target.value as typeof sortKey)}>
              <option value="band">Band</option>
              <option value="rate">Rate</option>
              <option value="price">Price</option>
              <option value="eligibility">Eligibility</option>
            </select>
            <button type="button" onClick={() => setCsvPreview(csv())}>Export CSV</button>
          </div>
        </div>
        <BandsTable bands={view.bands} currentFico={view.currentFico} sortKey={sortKey} />
        {csvPreview ? <pre className="diagnostics-details" aria-label="FICO sensitivity CSV preview">{csvPreview}</pre> : null}
      </section>

      <section className="panel" aria-labelledby="fico-chart-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Visual analysis</p>
            <h2 id="fico-chart-heading">Rate Chart, Price Chart, and Eligibility Heatmap</h2>
          </div>
          <div className="offer-toolbar" role="tablist" aria-label="FICO sensitivity views">
            <button type="button" role="tab" aria-selected={activeChart === 'rate'} onClick={() => setActiveChart('rate')}>Rate Chart</button>
            <button type="button" role="tab" aria-selected={activeChart === 'price'} onClick={() => setActiveChart('price')}>Price Chart</button>
            <button type="button" role="tab" aria-selected={activeChart === 'heatmap'} onClick={() => setActiveChart('heatmap')}>Eligibility Heatmap</button>
          </div>
        </div>
        {activeChart === 'rate' ? <RateChart bands={view.bands} currentFico={view.currentFico} /> : null}
        {activeChart === 'price' ? <PriceChart bands={view.bands} currentFico={view.currentFico} /> : null}
        {activeChart === 'heatmap' ? <EligibilityHeatmap bands={view.bands} onSelect={setSelectedCell} selectedCell={selectedCell} runId={runId} /> : null}
      </section>
    </>
  );
}

function FicoLayout({ view, currentBand, outsideModeledRange }: { view: FicoSensitivityView; currentBand: FicoSensitivityBand | undefined; outsideModeledRange: boolean }) {
  return (
    <section className="panel sticky-header" aria-labelledby="fico-sensitivity-heading">
      <div className="panel-heading-row">
        <div>
          <p className="eyebrow">Run {view.runId}</p>
          <h2 id="fico-sensitivity-heading">FICO sensitivity summary</h2>
        </div>
        <DiagnosticsDetails items={[`Support reference: ${view.uiTraceId}`, `Dependency: ${view.dependencyStatus}`]} />
      </div>
      <dl className="status-grid">
        <dt>Current borrower FICO</dt><dd>{formatValue(view.currentFicoLabel ?? view.currentFico)}</dd>
        <dt>Current borrower band</dt><dd>{currentBand ? formatBandLabel(currentBand) : outsideModeledRange ? 'Outside modeled range' : 'Not supplied'}</dd>
        <dt>FICO source</dt><dd>{formatValue(view.currentFicoSourceRef ?? view.metadata?.currentFicoSourceRef)}</dd>
        <dt>Score band source</dt><dd>{formatValue(view.metadata?.scoreBandSourceRef)}</dd>
      </dl>
      {outsideModeledRange ? <div className="banner banner--blocked" role="alert">Current borrower FICO is outside modeled range supplied by the backend.</div> : null}
      {view.fallbackReason ? <div className="banner banner--blocked" role="alert"><strong>Backend FICO sensitivity contract required</strong><span>{view.fallbackReason}</span></div> : null}
      <ScenarioBlockerList blockers={view.blockers ?? []} label="FICO sensitivity blockers" />
      <ChipList label="FICO sensitivity export refs" values={view.exportRefs ?? []} />
      <ChipList label="FICO sensitivity processing refs" values={view.replayRefs ?? []} />
      <ChipList label="FICO sensitivity audit refs" values={view.auditRefs ?? []} />
      <ChipList label="FICO sensitivity events" values={(view.events ?? []).map(businessFacingText)} />
    </section>
  );
}

function BandsTable({ bands, currentFico, sortKey }: { bands: FicoSensitivityBand[]; currentFico?: number | null; sortKey: 'band' | 'rate' | 'price' | 'eligibility' }) {
  const sortedBands = useMemo(() => [...bands].sort((left, right) => compareBands(left, right, sortKey)), [bands, sortKey]);
  if (sortedBands.length === 0) {
    return <div className="banner banner--blocked" role="alert">No backend FICO bands are available. The UI will not create local score-band ranges.</div>;
  }
  return (
    <div className="quote-table" role="table" aria-label="FICO sensitivity bands">
      <div role="row" className="quote-table__row quote-table__row--head">
        <span role="columnheader">FICO Band</span>
        <span role="columnheader">Note Rate</span>
        <span role="columnheader">Final Price (bps)</span>
        <span role="columnheader">Payment</span>
        <span role="columnheader">APR</span>
        <span role="columnheader">Eligibility</span>
        <span role="columnheader">Blocker Reason</span>
        <span role="columnheader">Source Ref</span>
        <span role="columnheader">Evidence Refs</span>
      </div>
      {sortedBands.map((band) => {
        const current = isCurrentBand(band, currentFico);
        return (
          <div key={band.bandId} role="row" className={current ? 'quote-table__row quote-table__row--selected' : 'quote-table__row'} aria-label={`${formatBandLabel(band)} ${current ? 'current borrower band' : 'modeled band'}`}>
            <span role="cell"><strong>{formatBandLabel(band)}</strong>{current ? <span className="chip">Current borrower band</span> : null}</span>
            <span role="cell">{formatPricingValue(band.noteRate, band.pricingUnavailableReason)}</span>
            <span role="cell">{formatPricingValue(band.finalPriceBps, band.pricingUnavailableReason)}</span>
            <span role="cell">{formatPricingValue(band.payment, band.pricingUnavailableReason)}</span>
            <span role="cell">{formatPricingValue(band.apr, band.pricingUnavailableReason)}</span>
            <span role="cell"><EligibilityBadge eligibility={band.eligibility} /></span>
            <span role="cell">{formatValue(band.blockerReason ?? band.pricingUnavailableReason)}</span>
            <span role="cell">{formatValue(band.sourceRef)}</span>
            <span role="cell"><ChipList label={`${formatBandLabel(band)} evidence refs`} values={band.evidenceRefs ?? []} /></span>
          </div>
        );
      })}
    </div>
  );
}

function RateChart({ bands, currentFico }: { bands: FicoSensitivityBand[]; currentFico?: number | null }) {
  const points = chartPoints(bands, 'noteRate', currentFico);
  if (points.length === 0) return <div className="banner banner--blocked" role="alert">No backend rate chart points are available. The UI will not invent note-rate values.</div>;
  const currentX = currentMarkerX(points, currentFico);
  const linePoints = points.map((point) => `${point.x},${point.y}`).join(' ');
  return (
    <div role="img" aria-label="Rate Chart: note rate by backend FICO band">
      <p className="field-help">Line chart data is rendered from backend note-rate values; missing or non-numeric rate refs are still labeled without browser-side pricing math.</p>
      <svg viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`} aria-label="Line chart plotting backend note rate by FICO midpoint" data-testid="rate-line-chart">
        <line x1={CHART_PADDING} y1={CHART_HEIGHT - CHART_PADDING} x2={CHART_WIDTH - CHART_PADDING} y2={CHART_HEIGHT - CHART_PADDING} stroke="currentColor" strokeWidth="1" />
        <line x1={CHART_PADDING} y1={CHART_PADDING} x2={CHART_PADDING} y2={CHART_HEIGHT - CHART_PADDING} stroke="currentColor" strokeWidth="1" />
        {points.map((point) => (
          <rect key={`${point.band.bandId}-eligibility`} x={point.x - 18} y={CHART_PADDING} width="36" height={CHART_HEIGHT - CHART_PADDING * 2} className={eligibilityClass(point.band.eligibility)} opacity="0.14" data-testid={`rate-eligibility-${point.band.bandId}`}>
            <title>{`${formatBandLabel(point.band)} eligibility ${businessFacingText(point.band.eligibility)}`}</title>
          </rect>
        ))}
        <polyline points={linePoints} fill="none" stroke="currentColor" strokeWidth="3" data-testid="rate-line-chart-series" />
        {currentX !== null ? <line x1={currentX} y1={CHART_PADDING} x2={currentX} y2={CHART_HEIGHT - CHART_PADDING} stroke="currentColor" strokeDasharray="6 4" strokeWidth="2" data-testid="rate-current-fico-marker" /> : null}
        {points.map((point) => (
          <g key={point.band.bandId} data-current={point.current ? 'true' : 'false'}>
            <circle cx={point.x} cy={point.y} r={point.current ? 7 : 5} fill="currentColor" data-testid={`rate-point-${point.band.bandId}`} />
            <text x={point.x} y={CHART_HEIGHT - 12} textAnchor="middle" fontSize="11">{formatValue(point.band.midpoint ?? formatBandLabel(point.band))}</text>
            <text x={point.x} y={Math.max(14, point.y - 10)} textAnchor="middle" fontSize="11">{formatPricingValue(point.band.noteRate, point.band.pricingUnavailableReason)}</text>
          </g>
        ))}
      </svg>
      <ChartLegend points={points} valueLabel="Note rate" />
    </div>
  );
}

function PriceChart({ bands, currentFico }: { bands: FicoSensitivityBand[]; currentFico?: number | null }) {
  const bars = chartPoints(bands, 'finalPriceBps', currentFico);
  const paymentPoints = chartPoints(bands, 'payment', currentFico);
  if (bars.length === 0) return <div className="banner banner--blocked" role="alert">No backend price chart bars are available. The UI will not invent price values.</div>;
  const currentX = currentMarkerX(bars, currentFico);
  const barWidth = Math.max(20, (CHART_WIDTH - CHART_PADDING * 2) / Math.max(bars.length, 1) - 14);
  const paymentLinePoints = paymentPoints.map((point) => `${point.x},${point.y}`).join(' ');
  return (
    <div role="img" aria-label="Price Chart: final price and payment by backend FICO band">
      <p className="field-help">Bar chart values and payment overlay labels are backend supplied; the UI does not price loans locally.</p>
      <svg viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`} aria-label="Bar chart plotting backend final price with payment overlay" data-testid="price-bar-chart">
        <line x1={CHART_PADDING} y1={CHART_HEIGHT - CHART_PADDING} x2={CHART_WIDTH - CHART_PADDING} y2={CHART_HEIGHT - CHART_PADDING} stroke="currentColor" strokeWidth="1" />
        <line x1={CHART_PADDING} y1={CHART_PADDING} x2={CHART_PADDING} y2={CHART_HEIGHT - CHART_PADDING} stroke="currentColor" strokeWidth="1" />
        {bars.map((point) => {
          const height = CHART_HEIGHT - CHART_PADDING - point.y;
          return (
            <g key={point.band.bandId} data-current={point.current ? 'true' : 'false'}>
              <rect x={point.x - barWidth / 2} y={point.y} width={barWidth} height={height} className={point.current ? 'quote-table__row--selected' : eligibilityClass(point.band.eligibility)} opacity={point.current ? '0.72' : '0.38'} data-current={point.current ? 'true' : 'false'} data-testid={`price-bar-${point.band.bandId}`}>
                <title>{`${formatBandLabel(point.band)} final price ${formatPricingValue(point.band.finalPriceBps, point.band.pricingUnavailableReason)}`}</title>
              </rect>
              <text x={point.x} y={CHART_HEIGHT - 12} textAnchor="middle" fontSize="11">{formatBandLabel(point.band)}</text>
              <text x={point.x} y={Math.max(14, point.y - 10)} textAnchor="middle" fontSize="11">{formatPricingValue(point.band.finalPriceBps, point.band.pricingUnavailableReason)}</text>
            </g>
          );
        })}
        {paymentLinePoints ? <polyline points={paymentLinePoints} fill="none" stroke="currentColor" strokeDasharray="4 3" strokeWidth="3" data-testid="price-payment-overlay" /> : null}
        {paymentPoints.map((point) => (
          <text key={`${point.band.bandId}-payment`} x={point.x} y={Math.min(CHART_HEIGHT - CHART_PADDING - 8, point.y + 18)} textAnchor="middle" fontSize="11">{formatPricingValue(point.band.payment, point.band.pricingUnavailableReason)}</text>
        ))}
        {currentX !== null ? <line x1={currentX} y1={CHART_PADDING} x2={currentX} y2={CHART_HEIGHT - CHART_PADDING} stroke="currentColor" strokeDasharray="6 4" strokeWidth="2" data-testid="price-current-fico-highlight" /> : null}
      </svg>
      <ChartLegend points={bars} valueLabel="Final price" />
    </div>
  );
}

function ChartLegend({ points, valueLabel }: { points: ChartPoint[]; valueLabel: string }) {
  return (
    <ul className="chip-list" aria-label={`${valueLabel} chart backend values`}>
      {points.map((point) => (
        <li key={point.band.bandId}>{formatBandLabel(point.band)} · {valueLabel}: {point.displayValue} · {businessFacingText(point.band.eligibility)}{point.current ? ' · Current FICO' : ''}</li>
      ))}
    </ul>
  );
}

function EligibilityHeatmap({ bands, onSelect, selectedCell, runId }: { bands: FicoSensitivityBand[]; onSelect: (cell: FicoSensitivityEligibilityCell) => void; selectedCell: FicoSensitivityEligibilityCell | null; runId?: string }) {
  const cells = bands.flatMap((band) => heatmapCellsForBand(band));
  if (cells.length === 0) return <div className="banner banner--blocked" role="alert">No backend eligibility cells are available for the heatmap.</div>;
  return (
    <>
      <div className="quote-table" role="table" aria-label="Eligibility heatmap">
        <div role="row" className="quote-table__row quote-table__row--head">
          <span role="columnheader">FICO Band</span>
          <span role="columnheader">Product/Channel</span>
          <span role="columnheader">Eligibility</span>
          <span role="columnheader">Blocker Reason</span>
        </div>
        {cells.map(({ band, cell }) => (
          <button key={`${band.bandId}-${cell.cellId}`} type="button" role="row" className={`quote-table__row ${eligibilityClass(cell.eligibility)}`} onClick={() => onSelect(cell)} aria-label={`${formatBandLabel(band)} ${cell.productLabel} ${cell.channelLabel} ${cell.eligibility}`}>
            <span role="cell">{formatBandLabel(band)}</span>
            <span role="cell">{businessFacingText(cell.productLabel)} / {businessFacingText(cell.channelLabel)}</span>
            <span role="cell"><EligibilityBadge eligibility={cell.eligibility} /></span>
            <span role="cell">{formatValue(cell.blockerReason)}</span>
          </button>
        ))}
      </div>
      {selectedCell ? (
        <div className="banner banner--info" role="status">
          <strong>Eligibility detail</strong>
          <span>{businessFacingText(selectedCell.productLabel)} / {businessFacingText(selectedCell.channelLabel)} · {businessFacingText(selectedCell.eligibility)}</span>
          <span>{formatValue(selectedCell.blockerReason)}</span>
          <a href={`/quote/${encodeURIComponent(runId ?? '')}/eligibility`}>View Eligibility Detail</a>
        </div>
      ) : null}
    </>
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

function ChipList({ label, values }: { label: string; values: string[] }) {
  if (!values.length) return null;
  return <ul className="chip-list" aria-label={label}>{values.map((value) => <li key={value}>{value}</li>)}</ul>;
}

function EligibilityBadge({ eligibility }: { eligibility: string }) {
  return <span className={`chip ${eligibilityClass(eligibility)}`}>{businessFacingText(eligibility)}</span>;
}

function findCurrentBand(bands: FicoSensitivityBand[], currentFico?: number | null) {
  return bands.find((band) => isCurrentBand(band, currentFico));
}

function isCurrentBand(band: FicoSensitivityBand, currentFico?: number | null) {
  if (band.currentBorrowerBand) return true;
  if (typeof currentFico !== 'number' || typeof band.minScore !== 'number' || typeof band.maxScore !== 'number') return false;
  return currentFico >= band.minScore && currentFico <= band.maxScore;
}

function compareBands(left: FicoSensitivityBand, right: FicoSensitivityBand, sortKey: 'band' | 'rate' | 'price' | 'eligibility') {
  if (sortKey === 'rate') return compareValues(left.noteRate, right.noteRate);
  if (sortKey === 'price') return compareValues(left.finalPriceBps, right.finalPriceBps);
  if (sortKey === 'eligibility') return businessFacingText(left.eligibility).localeCompare(businessFacingText(right.eligibility));
  return compareValues(left.minScore ?? left.midpoint ?? left.label, right.minScore ?? right.midpoint ?? right.label);
}

function compareValues(left: string | number | null | undefined, right: string | number | null | undefined) {
  const leftNumber = typeof left === 'number' ? left : Number.parseFloat(String(left ?? '').replace(/[^0-9.-]+/g, ''));
  const rightNumber = typeof right === 'number' ? right : Number.parseFloat(String(right ?? '').replace(/[^0-9.-]+/g, ''));
  if (Number.isFinite(leftNumber) && Number.isFinite(rightNumber)) return leftNumber - rightNumber;
  return formatValue(left).localeCompare(formatValue(right));
}

type ChartValueKey = 'noteRate' | 'finalPriceBps' | 'payment';

type ChartPoint = {
  band: FicoSensitivityBand;
  x: number;
  y: number;
  current: boolean;
  displayValue: string;
};

function chartPoints(bands: FicoSensitivityBand[], valueKey: ChartValueKey, currentFico?: number | null): ChartPoint[] {
  const drawableBands = bands.filter((band) => band[valueKey] !== null && band[valueKey] !== undefined && band[valueKey] !== '');
  const scores = drawableBands.map((band, index) => scorePositionValue(band, index));
  const values = drawableBands.map((band, index) => chartNumericValue(band[valueKey], index));
  const scoreDomain = domain(scores);
  const valueDomain = domain(values);
  return drawableBands.map((band, index) => ({
    band,
    x: scale(scores[index], scoreDomain[0], scoreDomain[1], CHART_PADDING, CHART_WIDTH - CHART_PADDING),
    y: scale(values[index], valueDomain[0], valueDomain[1], CHART_HEIGHT - CHART_PADDING, CHART_PADDING),
    current: isCurrentBand(band, currentFico),
    displayValue: formatPricingValue(band[valueKey], band.pricingUnavailableReason),
  }));
}

function currentMarkerX(points: ChartPoint[], currentFico?: number | null) {
  const explicitCurrent = points.find((point) => point.band.currentBorrowerBand);
  if (explicitCurrent) return explicitCurrent.x;
  if (typeof currentFico !== 'number') return null;
  const scores = points.map((point, index) => scorePositionValue(point.band, index));
  const scoreDomain = domain(scores);
  return scale(currentFico, scoreDomain[0], scoreDomain[1], CHART_PADDING, CHART_WIDTH - CHART_PADDING);
}

function scorePositionValue(band: FicoSensitivityBand, index: number) {
  if (typeof band.midpoint === 'number') return band.midpoint;
  if (typeof band.minScore === 'number' && typeof band.maxScore === 'number') return (band.minScore + band.maxScore) / 2;
  return index;
}

function chartNumericValue(value: string | number | null | undefined, fallbackIndex: number) {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  if (typeof value === 'string' && /^-?\d+(\.\d+)?%?$/.test(value.trim())) return Number.parseFloat(value);
  return fallbackIndex;
}

function domain(values: number[]): [number, number] {
  const finiteValues = values.filter(Number.isFinite);
  if (finiteValues.length === 0) return [0, 1];
  const min = Math.min(...finiteValues);
  const max = Math.max(...finiteValues);
  return min === max ? [min - 1, max + 1] : [min, max];
}

function scale(value: number, domainMin: number, domainMax: number, rangeMin: number, rangeMax: number) {
  if (!Number.isFinite(value) || domainMin === domainMax) return (rangeMin + rangeMax) / 2;
  return rangeMin + ((value - domainMin) / (domainMax - domainMin)) * (rangeMax - rangeMin);
}

function heatmapCellsForBand(band: FicoSensitivityBand) {
  const cells = band.eligibilityCells && band.eligibilityCells.length > 0
    ? band.eligibilityCells
    : [{ cellId: `${band.bandId}-default`, productLabel: band.productLabel ?? 'Backend product not supplied', channelLabel: band.channelLabel ?? 'Backend channel not supplied', eligibility: band.eligibility, blockerReason: band.blockerReason, sourceRef: band.sourceRef }];
  return cells.map((cell) => ({ band, cell }));
}

function eligibilityClass(value: string) {
  const normalized = value.toUpperCase();
  if (normalized === 'ELIGIBLE') return 'eligibility--eligible';
  if (normalized === 'INELIGIBLE') return 'eligibility--ineligible';
  if (normalized === 'CONDITIONAL') return 'eligibility--conditional';
  return 'eligibility--unknown';
}

function formatBandLabel(band: FicoSensitivityBand) {
  if (band.label) return band.label;
  if (typeof band.minScore === 'number' && typeof band.maxScore === 'number') return `${band.minScore}-${band.maxScore}`;
  return businessFacingText(band.bandId);
}

function formatPricingValue(value: string | number | null | undefined, unavailableReason?: string | null) {
  if (value === null || value === undefined || value === '') return unavailableReason ? `N/A · ${unavailableReason}` : 'N/A';
  return formatValue(value);
}

function formatValue(value: string | number | null | undefined) {
  if (value === null || value === undefined || value === '') return 'Not supplied';
  return String(value);
}

function businessFacingText(value: string | number | null | undefined) {
  if (value === null || value === undefined || value === '') return 'Not supplied';
  return String(value).replace(/[_-]+/g, ' ');
}

function buildCsv(bands: FicoSensitivityBand[]) {
  const rows = [
    ['FICO Band', 'Note Rate', 'Final Price (bps)', 'Payment', 'APR', 'Eligibility', 'Blocker Reason', 'Source Ref', 'Evidence Refs'],
    ...bands.map((band) => [
      formatBandLabel(band),
      formatPricingValue(band.noteRate, band.pricingUnavailableReason),
      formatPricingValue(band.finalPriceBps, band.pricingUnavailableReason),
      formatPricingValue(band.payment, band.pricingUnavailableReason),
      formatPricingValue(band.apr, band.pricingUnavailableReason),
      businessFacingText(band.eligibility),
      formatValue(band.blockerReason ?? band.pricingUnavailableReason),
      formatValue(band.sourceRef),
      (band.evidenceRefs ?? []).join(' | '),
    ]),
  ];
  return rows.map((row) => row.map(csvEscape).join(',')).join('\n');
}

function csvEscape(value: string) {
  return `"${value.replace(/"/g, '""')}"`;
}
