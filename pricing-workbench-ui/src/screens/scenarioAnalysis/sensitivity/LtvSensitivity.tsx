import { useEffect, useMemo, useState } from 'react';
import { DiagnosticsDetails } from '../../../components/DiagnosticsDetails';
import {
  fetchLtvSensitivity,
  type LtvSensitivityBand,
  type LtvSensitivityEligibilityCell,
  type LtvSensitivityView,
  type ScenarioAnalysisBlocker,
} from '../../../lib/api/scenarioAnalysis';

type LtvSensitivityState =
  | { kind: 'loading' }
  | { kind: 'loaded'; view: LtvSensitivityView }
  | { kind: 'unreachable'; message: string };

type ChartTab = 'rate' | 'price' | 'mip' | 'heatmap';

const CHART_WIDTH = 720;
const CHART_HEIGHT = 280;
const CHART_PADDING = 36;

export function LtvSensitivityScreen({ runId, tenantContext }: { runId: string; tenantContext: string }) {
  const [state, setState] = useState<LtvSensitivityState>({ kind: 'loading' });
  const [activeChart, setActiveChart] = useState<ChartTab>('rate');
  const [sortKey, setSortKey] = useState<'band' | 'rate' | 'price' | 'eligibility'>('band');
  const [selectedCell, setSelectedCell] = useState<LtvSensitivityEligibilityCell | null>(null);
  const [csvPreview, setCsvPreview] = useState('');

  useEffect(() => {
    let active = true;
    fetchLtvSensitivity(tenantContext, runId)
      .then((view) => {
        if (active) setState({ kind: 'loaded', view });
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'LTV sensitivity analysis is unavailable.';
        if (active) setState({ kind: 'unreachable', message });
      });
    return () => {
      active = false;
    };
  }, [runId, tenantContext]);

  if (state.kind === 'loading') {
    return (
      <section className="panel" aria-labelledby="ltv-sensitivity-heading">
        <h2 id="ltv-sensitivity-heading">LTV/Down Payment Sensitivity</h2>
        <p role="status">Loading LTV/down payment sensitivity analysis...</p>
      </section>
    );
  }

  if (state.kind === 'unreachable') {
    return (
      <section className="panel" aria-labelledby="ltv-sensitivity-heading">
        <h2 id="ltv-sensitivity-heading">LTV/Down Payment Sensitivity</h2>
        <div className="banner banner--blocked" role="alert">{state.message}</div>
      </section>
    );
  }

  const view = state.view;
  const currentBand = findCurrentBand(view.bands, view.currentLtv);
  const outsideModeledRange = typeof view.currentLtv === 'number' && view.bands.length > 0 && !currentBand;
  const csv = () => buildCsv(view.bands);

  return (
    <>
      <section className="hero" aria-labelledby="ltv-sensitivity-title">
        <p className="eyebrow">LTV sensitivity - PII-24-S31</p>
        <h2 id="ltv-sensitivity-title">LTV/Down Payment Sensitivity</h2>
        <p>
          Review backend-supplied LTV bands, down payment, note rate, final price, payment, MI premium, CLTV/HCLTV,
          eligibility, and evidence refs for run {runId}. The workbench does not calculate mortgage pricing, MI,
          eligibility, or LTV policy rules in the browser.
        </p>
        <a href={`/quote/${encodeURIComponent(runId)}/what-if`}>Back to Scenario Analysis</a>
      </section>

      <LtvLayout view={view} currentBand={currentBand} outsideModeledRange={outsideModeledRange} />

      <section className="panel" aria-labelledby="ltv-bands-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Backend bands</p>
            <h2 id="ltv-bands-heading">Bands Table</h2>
          </div>
          <div className="offer-toolbar" aria-label="LTV band actions">
            <label htmlFor="ltv-sort">Sort by</label>
            <select id="ltv-sort" value={sortKey} onChange={(event) => setSortKey(event.target.value as typeof sortKey)}>
              <option value="band">Band</option>
              <option value="rate">Rate</option>
              <option value="price">Price</option>
              <option value="eligibility">Eligibility</option>
            </select>
            <button type="button" onClick={() => setCsvPreview(csv())}>Export CSV</button>
          </div>
        </div>
        <LtvBandsTable bands={view.bands} currentLtv={view.currentLtv} sortKey={sortKey} />
        {csvPreview ? <pre className="diagnostics-details" aria-label="LTV sensitivity CSV preview">{csvPreview}</pre> : null}
      </section>

      <section className="panel" aria-labelledby="ltv-chart-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Visual analysis</p>
            <h2 id="ltv-chart-heading">Rate Chart, Price Chart, MIP Chart, and Eligibility Heatmap</h2>
          </div>
          <div className="offer-toolbar" role="tablist" aria-label="LTV sensitivity views">
            <button type="button" role="tab" aria-selected={activeChart === 'rate'} onClick={() => setActiveChart('rate')}>Rate Chart</button>
            <button type="button" role="tab" aria-selected={activeChart === 'price'} onClick={() => setActiveChart('price')}>Price Chart</button>
            <button type="button" role="tab" aria-selected={activeChart === 'mip'} onClick={() => setActiveChart('mip')}>MIP Chart</button>
            <button type="button" role="tab" aria-selected={activeChart === 'heatmap'} onClick={() => setActiveChart('heatmap')}>Eligibility Heatmap</button>
          </div>
        </div>
        {activeChart === 'rate' ? <RateChart bands={view.bands} currentLtv={view.currentLtv} /> : null}
        {activeChart === 'price' ? <PriceChart bands={view.bands} currentLtv={view.currentLtv} /> : null}
        {activeChart === 'mip' ? <MipChart bands={view.bands} currentLtv={view.currentLtv} /> : null}
        {activeChart === 'heatmap' ? <EligibilityHeatmap bands={view.bands} onSelect={setSelectedCell} selectedCell={selectedCell} runId={runId} /> : null}
      </section>
    </>
  );
}

function LtvLayout({ view, currentBand, outsideModeledRange }: { view: LtvSensitivityView; currentBand: LtvSensitivityBand | undefined; outsideModeledRange: boolean }) {
  return (
    <section className="panel sticky-header" aria-labelledby="ltv-sensitivity-heading">
      <div className="panel-heading-row">
        <div>
          <p className="eyebrow">Run {view.runId}</p>
          <h2 id="ltv-sensitivity-heading">LTV sensitivity summary</h2>
        </div>
        <DiagnosticsDetails items={[`Support reference: ${view.uiTraceId}`, `Dependency: ${view.dependencyStatus}`]} />
      </div>
      <dl className="status-grid">
        <dt>Current LTV</dt><dd>{formatValue(view.currentLtvLabel ?? view.currentLtv)}</dd>
        <dt>Current down payment</dt><dd>{formatValue(view.currentDownPaymentLabel ?? view.currentDownPaymentPct)}</dd>
        <dt>Current LTV band</dt><dd>{currentBand ? formatBandLabel(currentBand) : outsideModeledRange ? 'Outside modeled range' : 'Not supplied'}</dd>
        <dt>LTV source</dt><dd>{formatValue(view.currentLtvSourceRef ?? view.metadata?.currentLtvSourceRef)}</dd>
        <dt>Down payment source</dt><dd>{formatValue(view.currentDownPaymentSourceRef ?? view.metadata?.currentDownPaymentSourceRef)}</dd>
        <dt>Band source</dt><dd>{formatValue(view.metadata?.ltvBandSourceRef)}</dd>
      </dl>
      {outsideModeledRange ? <div className="banner banner--blocked" role="alert">Current LTV is outside modeled range supplied by the backend.</div> : null}
      {view.fallbackReason ? <div className="banner banner--blocked" role="alert"><strong>Backend LTV sensitivity contract required</strong><span>{view.fallbackReason}</span></div> : null}
      <ScenarioBlockerList blockers={view.blockers ?? []} label="LTV sensitivity blockers" />
      <ChipList label="LTV sensitivity export refs" values={view.exportRefs ?? []} />
      <ChipList label="LTV sensitivity processing refs" values={view.replayRefs ?? []} />
      <ChipList label="LTV sensitivity audit refs" values={view.auditRefs ?? []} />
      <ChipList label="LTV sensitivity events" values={(view.events ?? []).map(businessFacingText)} />
    </section>
  );
}

function LtvBandsTable({ bands, currentLtv, sortKey }: { bands: LtvSensitivityBand[]; currentLtv?: number | null; sortKey: 'band' | 'rate' | 'price' | 'eligibility' }) {
  const sortedBands = useMemo(() => [...bands].sort((left, right) => compareBands(left, right, sortKey)), [bands, sortKey]);
  if (sortedBands.length === 0) {
    return <div className="banner banner--blocked" role="alert">No backend LTV bands are available. The UI will not create local LTV ranges.</div>;
  }
  return (
    <div className="quote-table" role="table" aria-label="LTV sensitivity bands">
      <div role="row" className="quote-table__row quote-table__row--head">
        <span role="columnheader">LTV Band</span>
        <span role="columnheader">Down Payment %</span>
        <span role="columnheader">Note Rate</span>
        <span role="columnheader">Final Price (bps)</span>
        <span role="columnheader">Payment</span>
        <span role="columnheader">MI Premium</span>
        <span role="columnheader">CLTV</span>
        <span role="columnheader">HCLTV</span>
        <span role="columnheader">Eligibility</span>
        <span role="columnheader">Blocker Reason</span>
        <span role="columnheader">Source Ref</span>
        <span role="columnheader">Evidence Refs</span>
      </div>
      {sortedBands.map((band) => {
        const current = isCurrentBand(band, currentLtv);
        return (
          <div key={band.bandId} role="row" className={current ? 'quote-table__row quote-table__row--selected' : 'quote-table__row'} aria-label={`${formatBandLabel(band)} ${current ? 'current LTV band' : 'modeled band'}`}>
            <span role="cell"><strong>{formatBandLabel(band)}</strong>{current ? <span className="chip">Current LTV band</span> : null}</span>
            <span role="cell">{formatValue(band.downPaymentPct)}</span>
            <span role="cell">{formatPricingValue(band.noteRate, band.pricingUnavailableReason)}</span>
            <span role="cell">{formatPricingValue(band.finalPriceBps, band.pricingUnavailableReason)}</span>
            <span role="cell">{formatPricingValue(band.payment, band.pricingUnavailableReason)}</span>
            <span role="cell">{formatPricingValue(band.miPremium, band.pricingUnavailableReason)}{band.miType ? <span className="chip">{businessFacingText(band.miType)}</span> : null}</span>
            <span role="cell">{formatValue(band.cltv)}</span>
            <span role="cell">{formatValue(band.hcltv)}</span>
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

function RateChart({ bands, currentLtv }: { bands: LtvSensitivityBand[]; currentLtv?: number | null }) {
  const points = chartPoints(bands, 'noteRate', currentLtv);
  if (points.length === 0) return <div className="banner banner--blocked" role="alert">No backend rate chart points are available. The UI will not invent note-rate values.</div>;
  const currentX = currentMarkerX(points, currentLtv);
  return (
    <div role="img" aria-label="Rate Chart: note rate by backend LTV band">
      <p className="field-help">Line chart data is rendered from backend note-rate values; missing or non-numeric rate refs are labeled without browser-side pricing math.</p>
      <svg viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`} aria-label="Line chart plotting backend note rate by LTV band" data-testid="ltv-rate-line-chart">
        <ChartAxes />
        {points.map((point) => <EligibilityBoundary key={`${point.band.bandId}-eligibility`} point={point} />)}
        <polyline points={points.map((point) => `${point.x},${point.y}`).join(' ')} fill="none" stroke="currentColor" strokeWidth="3" data-testid="ltv-rate-line-chart-series" />
        {currentX !== null ? <line x1={currentX} y1={CHART_PADDING} x2={currentX} y2={CHART_HEIGHT - CHART_PADDING} stroke="currentColor" strokeDasharray="6 4" strokeWidth="2" data-testid="ltv-rate-current-marker" /> : null}
        {points.map((point) => <ChartPointMarker key={point.band.bandId} point={point} value={point.band.noteRate} />)}
      </svg>
      <ChartLegend points={points} valueLabel="Note rate" />
    </div>
  );
}

function PriceChart({ bands, currentLtv }: { bands: LtvSensitivityBand[]; currentLtv?: number | null }) {
  const bars = chartPoints(bands, 'finalPriceBps', currentLtv);
  const paymentPoints = chartPoints(bands, 'payment', currentLtv);
  if (bars.length === 0) return <div className="banner banner--blocked" role="alert">No backend price chart bars are available. The UI will not invent price values.</div>;
  const currentX = currentMarkerX(bars, currentLtv);
  const barWidth = Math.max(20, (CHART_WIDTH - CHART_PADDING * 2) / Math.max(bars.length, 1) - 14);
  return (
    <div role="img" aria-label="Price Chart: final price and payment by backend LTV band">
      <p className="field-help">Bar chart values and payment overlay labels are backend supplied; the UI does not price loans locally.</p>
      <svg viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`} aria-label="Bar chart plotting backend final price with payment overlay" data-testid="ltv-price-bar-chart">
        <ChartAxes />
        {bars.map((point) => {
          const height = CHART_HEIGHT - CHART_PADDING - point.y;
          return (
            <g key={point.band.bandId} data-current={point.current ? 'true' : 'false'}>
              <rect x={point.x - barWidth / 2} y={point.y} width={barWidth} height={height} className={point.current ? 'quote-table__row--selected' : eligibilityClass(point.band.eligibility)} opacity={point.current ? '0.72' : '0.38'} data-current={point.current ? 'true' : 'false'} data-testid={`ltv-price-bar-${point.band.bandId}`}>
                <title>{`${formatBandLabel(point.band)} final price ${formatPricingValue(point.band.finalPriceBps, point.band.pricingUnavailableReason)}`}</title>
              </rect>
              <text x={point.x} y={CHART_HEIGHT - 12} textAnchor="middle" fontSize="11">{formatBandLabel(point.band)}</text>
              <text x={point.x} y={Math.max(14, point.y - 10)} textAnchor="middle" fontSize="11">{formatPricingValue(point.band.finalPriceBps, point.band.pricingUnavailableReason)}</text>
            </g>
          );
        })}
        {paymentPoints.length > 0 ? <polyline points={paymentPoints.map((point) => `${point.x},${point.y}`).join(' ')} fill="none" stroke="currentColor" strokeDasharray="4 3" strokeWidth="3" data-testid="ltv-price-payment-overlay" /> : null}
        {paymentPoints.map((point) => <text key={`${point.band.bandId}-payment`} x={point.x} y={Math.min(CHART_HEIGHT - CHART_PADDING - 8, point.y + 18)} textAnchor="middle" fontSize="11">{formatPricingValue(point.band.payment, point.band.pricingUnavailableReason)}</text>)}
        {currentX !== null ? <line x1={currentX} y1={CHART_PADDING} x2={currentX} y2={CHART_HEIGHT - CHART_PADDING} stroke="currentColor" strokeDasharray="6 4" strokeWidth="2" data-testid="ltv-price-current-highlight" /> : null}
      </svg>
      <ChartLegend points={bars} valueLabel="Final price" />
    </div>
  );
}

function MipChart({ bands, currentLtv }: { bands: LtvSensitivityBand[]; currentLtv?: number | null }) {
  const points = chartPoints(bands, 'miPremium', currentLtv);
  if (points.length === 0) return <div className="banner banner--blocked" role="alert">No backend MIP chart points are available. The UI will not infer MI premiums or MI requirements.</div>;
  const currentX = currentMarkerX(points, currentLtv);
  return (
    <div role="img" aria-label="MIP Chart: MI premium by backend LTV band">
      <p className="field-help">MI premium and type badges are backend supplied. Missing MI values remain explicit blocked or not-supplied states.</p>
      <svg viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`} aria-label="Line chart plotting backend MI premium by LTV band" data-testid="ltv-mip-line-chart">
        <ChartAxes />
        <polyline points={points.map((point) => `${point.x},${point.y}`).join(' ')} fill="none" stroke="currentColor" strokeWidth="3" data-testid="ltv-mip-line-chart-series" />
        {currentX !== null ? <line x1={currentX} y1={CHART_PADDING} x2={currentX} y2={CHART_HEIGHT - CHART_PADDING} stroke="currentColor" strokeDasharray="6 4" strokeWidth="2" data-testid="ltv-mip-current-highlight" /> : null}
        {points.map((point) => <ChartPointMarker key={point.band.bandId} point={point} value={point.band.miPremium} suffix={point.band.miType ? businessFacingText(point.band.miType) : undefined} />)}
      </svg>
      <ChartLegend points={points} valueLabel="MI premium" />
    </div>
  );
}

function ChartAxes() {
  return (
    <>
      <line x1={CHART_PADDING} y1={CHART_HEIGHT - CHART_PADDING} x2={CHART_WIDTH - CHART_PADDING} y2={CHART_HEIGHT - CHART_PADDING} stroke="currentColor" strokeWidth="1" />
      <line x1={CHART_PADDING} y1={CHART_PADDING} x2={CHART_PADDING} y2={CHART_HEIGHT - CHART_PADDING} stroke="currentColor" strokeWidth="1" />
    </>
  );
}

function EligibilityBoundary({ point }: { point: ChartPoint }) {
  return (
    <rect x={point.x - 18} y={CHART_PADDING} width="36" height={CHART_HEIGHT - CHART_PADDING * 2} className={eligibilityClass(point.band.eligibility)} opacity="0.14" data-testid={`ltv-rate-eligibility-${point.band.bandId}`}>
      <title>{`${formatBandLabel(point.band)} eligibility ${businessFacingText(point.band.eligibility)}`}</title>
    </rect>
  );
}

function ChartPointMarker({ point, value, suffix }: { point: ChartPoint; value: string | number | null | undefined; suffix?: string }) {
  const label = [formatPricingValue(value, point.band.pricingUnavailableReason), suffix].filter(Boolean).join(' - ');
  return (
    <g data-current={point.current ? 'true' : 'false'}>
      <circle cx={point.x} cy={point.y} r={point.current ? 7 : 5} fill="currentColor" data-testid={`ltv-chart-point-${point.band.bandId}`} />
      <text x={point.x} y={CHART_HEIGHT - 12} textAnchor="middle" fontSize="11">{formatValue(point.band.midpointLtv ?? formatBandLabel(point.band))}</text>
      <text x={point.x} y={Math.max(14, point.y - 10)} textAnchor="middle" fontSize="11">{label}</text>
    </g>
  );
}

function ChartLegend({ points, valueLabel }: { points: ChartPoint[]; valueLabel: string }) {
  return (
    <ul className="chip-list" aria-label={`${valueLabel} chart backend values`}>
      {points.map((point) => (
        <li key={point.band.bandId}>{formatBandLabel(point.band)} - {valueLabel}: {point.displayValue} - {businessFacingText(point.band.eligibility)}{point.band.miType ? ` - ${businessFacingText(point.band.miType)}` : ''}{point.current ? ' - Current LTV' : ''}</li>
      ))}
    </ul>
  );
}

function EligibilityHeatmap({ bands, onSelect, selectedCell, runId }: { bands: LtvSensitivityBand[]; onSelect: (cell: LtvSensitivityEligibilityCell) => void; selectedCell: LtvSensitivityEligibilityCell | null; runId?: string }) {
  const cells = bands.flatMap((band) => heatmapCellsForBand(band));
  if (cells.length === 0) return <div className="banner banner--blocked" role="alert">No backend eligibility cells are available for the heatmap.</div>;
  return (
    <>
      <div className="quote-table" role="table" aria-label="LTV eligibility heatmap">
        <div role="row" className="quote-table__row quote-table__row--head">
          <span role="columnheader">LTV Band</span>
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
          <span>{businessFacingText(selectedCell.productLabel)} / {businessFacingText(selectedCell.channelLabel)} - {businessFacingText(selectedCell.eligibility)}</span>
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
          <strong>{businessFacingText(blocker.blockerCode)} - {businessFacingText(blocker.severity)}</strong>
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

function findCurrentBand(bands: LtvSensitivityBand[], currentLtv?: number | null) {
  return bands.find((band) => isCurrentBand(band, currentLtv));
}

function isCurrentBand(band: LtvSensitivityBand, currentLtv?: number | null) {
  if (band.currentBorrowerBand) return true;
  if (typeof currentLtv !== 'number' || typeof band.minLtv !== 'number' || typeof band.maxLtv !== 'number') return false;
  return currentLtv >= band.minLtv && currentLtv <= band.maxLtv;
}

function compareBands(left: LtvSensitivityBand, right: LtvSensitivityBand, sortKey: 'band' | 'rate' | 'price' | 'eligibility') {
  if (sortKey === 'rate') return compareValues(left.noteRate, right.noteRate);
  if (sortKey === 'price') return compareValues(left.finalPriceBps, right.finalPriceBps);
  if (sortKey === 'eligibility') return businessFacingText(left.eligibility).localeCompare(businessFacingText(right.eligibility));
  return compareValues(left.minLtv ?? left.midpointLtv ?? left.label, right.minLtv ?? right.midpointLtv ?? right.label);
}

function compareValues(left: string | number | null | undefined, right: string | number | null | undefined) {
  const leftNumber = typeof left === 'number' ? left : Number.parseFloat(String(left ?? '').replace(/[^0-9.-]+/g, ''));
  const rightNumber = typeof right === 'number' ? right : Number.parseFloat(String(right ?? '').replace(/[^0-9.-]+/g, ''));
  if (Number.isFinite(leftNumber) && Number.isFinite(rightNumber)) return leftNumber - rightNumber;
  return formatValue(left).localeCompare(formatValue(right));
}

type ChartValueKey = 'noteRate' | 'finalPriceBps' | 'payment' | 'miPremium';

type ChartPoint = {
  band: LtvSensitivityBand;
  x: number;
  y: number;
  current: boolean;
  displayValue: string;
};

function chartPoints(bands: LtvSensitivityBand[], valueKey: ChartValueKey, currentLtv?: number | null): ChartPoint[] {
  const drawableBands = bands.filter((band) => band[valueKey] !== null && band[valueKey] !== undefined && band[valueKey] !== '');
  const ltvValues = drawableBands.map((band, index) => ltvPositionValue(band, index));
  const values = drawableBands.map((band, index) => chartNumericValue(band[valueKey], index));
  const ltvDomain = domain(ltvValues);
  const valueDomain = domain(values);
  return drawableBands.map((band, index) => ({
    band,
    x: scale(ltvValues[index], ltvDomain[0], ltvDomain[1], CHART_PADDING, CHART_WIDTH - CHART_PADDING),
    y: scale(values[index], valueDomain[0], valueDomain[1], CHART_HEIGHT - CHART_PADDING, CHART_PADDING),
    current: isCurrentBand(band, currentLtv),
    displayValue: formatPricingValue(band[valueKey], band.pricingUnavailableReason),
  }));
}

function currentMarkerX(points: ChartPoint[], currentLtv?: number | null) {
  const explicitCurrent = points.find((point) => point.band.currentBorrowerBand);
  if (explicitCurrent) return explicitCurrent.x;
  if (typeof currentLtv !== 'number') return null;
  const ltvValues = points.map((point, index) => ltvPositionValue(point.band, index));
  const ltvDomain = domain(ltvValues);
  return scale(currentLtv, ltvDomain[0], ltvDomain[1], CHART_PADDING, CHART_WIDTH - CHART_PADDING);
}

function ltvPositionValue(band: LtvSensitivityBand, index: number) {
  if (typeof band.midpointLtv === 'number') return band.midpointLtv;
  if (typeof band.minLtv === 'number' && typeof band.maxLtv === 'number') return (band.minLtv + band.maxLtv) / 2;
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

function heatmapCellsForBand(band: LtvSensitivityBand) {
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

function formatBandLabel(band: LtvSensitivityBand) {
  if (band.label) return band.label;
  if (typeof band.minLtv === 'number' && typeof band.maxLtv === 'number') return `${band.minLtv}-${band.maxLtv}`;
  return businessFacingText(band.bandId);
}

function formatPricingValue(value: string | number | null | undefined, unavailableReason?: string | null) {
  if (value === null || value === undefined || value === '') return unavailableReason ? `N/A - ${unavailableReason}` : 'N/A';
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

function buildCsv(bands: LtvSensitivityBand[]) {
  const rows = [
    ['LTV Band', 'Down Payment %', 'Note Rate', 'Final Price (bps)', 'Payment', 'MI Premium', 'MI Type', 'CLTV', 'HCLTV', 'Eligibility', 'Blocker Reason', 'Source Ref', 'Evidence Refs'],
    ...bands.map((band) => [
      formatBandLabel(band),
      formatValue(band.downPaymentPct),
      formatPricingValue(band.noteRate, band.pricingUnavailableReason),
      formatPricingValue(band.finalPriceBps, band.pricingUnavailableReason),
      formatPricingValue(band.payment, band.pricingUnavailableReason),
      formatPricingValue(band.miPremium, band.pricingUnavailableReason),
      formatValue(band.miType),
      formatValue(band.cltv),
      formatValue(band.hcltv),
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
