import { useEffect, useMemo, useState } from 'react';
import { DiagnosticsDetails } from '../../../components/DiagnosticsDetails';
import {
  fetchLockPeriodComparison,
  type LockPeriodComparisonPeriod,
  type LockPeriodComparisonView,
  type ScenarioAnalysisBlocker,
} from '../../../lib/api/scenarioAnalysis';

type LockPeriodComparisonState =
  | { kind: 'loading' }
  | { kind: 'loaded'; view: LockPeriodComparisonView }
  | { kind: 'unreachable'; message: string };

type ActiveTab = 'table' | 'charts' | 'extension' | 'float-down';

type SortKey = 'days' | 'rate' | 'price' | 'float-down';

export function LockPeriodComparisonScreen({ runId, tenantContext }: { runId: string; tenantContext: string }) {
  const [state, setState] = useState<LockPeriodComparisonState>({ kind: 'loading' });
  const [activeTab, setActiveTab] = useState<ActiveTab>('table');
  const [sortKey, setSortKey] = useState<SortKey>('days');
  const [csvPreview, setCsvPreview] = useState('');

  useEffect(() => {
    let active = true;
    fetchLockPeriodComparison(tenantContext, runId)
      .then((view) => {
        if (active) setState({ kind: 'loaded', view });
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'Lock period comparison is unavailable.';
        if (active) setState({ kind: 'unreachable', message });
      });
    return () => {
      active = false;
    };
  }, [runId, tenantContext]);

  if (state.kind === 'loading') {
    return (
      <section className="panel" aria-labelledby="lock-period-comparison-heading">
        <h2 id="lock-period-comparison-heading">Lock Period Comparison</h2>
        <p role="status">Loading lock period comparison...</p>
      </section>
    );
  }

  if (state.kind === 'unreachable') {
    return (
      <section className="panel" aria-labelledby="lock-period-comparison-heading">
        <h2 id="lock-period-comparison-heading">Lock Period Comparison</h2>
        <div className="banner banner--blocked" role="alert">{state.message}</div>
      </section>
    );
  }

  const view = state.view;
  const sortedPeriods = sortPeriods(view.periods, sortKey);
  const currentPeriod = findCurrentPeriod(view.periods, view.currentLockPeriod);

  return (
    <>
      <section className="hero" aria-labelledby="lock-period-comparison-title">
        <p className="eyebrow">Lock period comparison · PII-24-S33</p>
        <h2 id="lock-period-comparison-title">Lock Period Comparison</h2>
        <p>
          Compare backend-supplied lock periods, rate and price refs, extension metadata, and float-down eligibility for run {runId}.
          The workbench does not calculate lock pricing, extension costs, float-down costs, or investor policy in the browser.
        </p>
        <a href={`/quote/${encodeURIComponent(runId)}/what-if`}>Back to Scenario Analysis</a>
      </section>

      <LockPeriodLayout view={view} currentPeriod={currentPeriod} />

      {view.fallbackReason ? <div className="banner banner--blocked" role="alert"><strong>Backend lock period comparison contract required</strong><span>{view.fallbackReason}</span></div> : null}
      {view.periods.length === 0 ? <div className="banner banner--blocked" role="alert">No lock periods supplied by the backend.</div> : null}
      <ScenarioBlockerList blockers={view.blockers ?? []} label="Lock period comparison blockers" />

      <section className="panel" aria-labelledby="lock-period-tabs-heading">
        <div className="panel-heading-row sticky-header">
          <div>
            <p className="eyebrow">Analysis</p>
            <h2 id="lock-period-tabs-heading">Periods Table, Rate/Price Chart, Extension Analysis, and Float-Down</h2>
          </div>
          <div className="offer-toolbar" role="tablist" aria-label="Lock period comparison views">
            <button type="button" role="tab" aria-selected={activeTab === 'table'} onClick={() => setActiveTab('table')}>Periods Table</button>
            <button type="button" role="tab" aria-selected={activeTab === 'charts'} onClick={() => setActiveTab('charts')}>Rate/Price Chart</button>
            <button type="button" role="tab" aria-selected={activeTab === 'extension'} onClick={() => setActiveTab('extension')}>Extension Analysis</button>
            <button type="button" role="tab" aria-selected={activeTab === 'float-down'} onClick={() => setActiveTab('float-down')}>Float-Down</button>
          </div>
        </div>
        {activeTab === 'table' ? <LockPeriodsTable periods={sortedPeriods} sortKey={sortKey} onSort={setSortKey} onExport={(csv) => setCsvPreview(csv)} /> : null}
        {activeTab === 'charts' ? <LockPeriodCharts periods={sortedPeriods} currentPeriod={currentPeriod} /> : null}
        {activeTab === 'extension' ? <ExtensionAnalysis periods={sortedPeriods} currentPeriod={currentPeriod} /> : null}
        {activeTab === 'float-down' ? <FloatDownAnalysis periods={sortedPeriods} /> : null}
        {csvPreview ? <pre className="diagnostics-details" aria-label="Lock period comparison CSV preview">{csvPreview}</pre> : null}
      </section>
    </>
  );
}

function LockPeriodLayout({ view, currentPeriod }: { view: LockPeriodComparisonView; currentPeriod: LockPeriodComparisonPeriod | undefined }) {
  return (
    <section className="panel sticky-header" aria-labelledby="lock-period-summary-heading">
      <div className="panel-heading-row">
        <div>
          <p className="eyebrow">Run {view.runId}</p>
          <h2 id="lock-period-summary-heading">Lock period comparison summary</h2>
        </div>
        <DiagnosticsDetails items={[`Support reference: ${view.uiTraceId}`, `Dependency: ${view.dependencyStatus}`]} />
      </div>
      <dl className="status-grid">
        <dt>Current lock period</dt><dd>{formatValue(view.currentLockPeriod)}</dd>
        <dt>Current period source</dt><dd>{formatValue(view.currentLockPeriodSourceRef)}</dd>
        <dt>Periods supplied</dt><dd>{view.periods.length}</dd>
        <dt>Current period row</dt><dd>{formatValue(currentPeriod?.label)}</dd>
        <dt>Lock period source</dt><dd>{formatValue(view.metadata?.lockPeriodSourceRef)}</dd>
        <dt>Pricing source</dt><dd>{formatValue(view.metadata?.pricingSourceRef)}</dd>
        <dt>Extension source</dt><dd>{formatValue(view.metadata?.extensionSourceRef)}</dd>
        <dt>Float-down source</dt><dd>{formatValue(view.metadata?.floatDownSourceRef)}</dd>
      </dl>
      <ChipList label="Lock period export refs" values={view.exportRefs ?? []} />
      <ChipList label="Lock period audit refs" values={view.auditRefs ?? []} />
      <ChipList label="Lock period events" values={(view.events ?? []).map(businessFacingText)} />
    </section>
  );
}

function LockPeriodsTable({ periods, sortKey, onSort, onExport }: { periods: LockPeriodComparisonPeriod[]; sortKey: SortKey; onSort: (sortKey: SortKey) => void; onExport: (csv: string) => void }) {
  if (!periods.length) return <div className="banner banner--blocked" role="alert">No backend lock periods supplied. The UI will not create local lock-period catalog values.</div>;
  return (
    <>
      <div className="panel-heading-row">
        <h3>Periods Table</h3>
        <div className="offer-toolbar">
          <label htmlFor="lock-period-sort">Sort by</label>
          <select id="lock-period-sort" value={sortKey} onChange={(event) => onSort(event.target.value as SortKey)}>
            <option value="days">Lock Period</option>
            <option value="rate">Note Rate</option>
            <option value="price">Final Price</option>
            <option value="float-down">Float-Down</option>
          </select>
          <button type="button" onClick={() => onExport(buildCsv(periods))}>Export CSV</button>
        </div>
      </div>
      <div className="quote-table" role="table" aria-label="Lock periods table">
        <div role="row" className="quote-table__row quote-table__row--head">
          <span role="columnheader">Lock Period</span>
          <span role="columnheader">Note Rate</span>
          <span role="columnheader">Final Price (bps)</span>
          <span role="columnheader">Payment</span>
          <span role="columnheader">Extension Cost (bps/day)</span>
          <span role="columnheader">Max Extension (days)</span>
          <span role="columnheader">Float-Down Eligible</span>
          <span role="columnheader">Source</span>
        </div>
        {periods.map((period) => (
          <div key={period.periodId} role="row" className={period.currentLockPeriod ? 'quote-table__row quote-table__row--selected' : 'quote-table__row'}>
            <span role="rowheader"><strong>{periodLabel(period)}</strong>{period.currentLockPeriod ? <><br /><span className="chip">Current lock period</span></> : null}</span>
            <span role="cell">{formatBackendValue(period.noteRate, period.pricingUnavailableReason)}</span>
            <span role="cell">{formatBackendValue(period.finalPriceBps, period.pricingUnavailableReason)}</span>
            <span role="cell">{formatBackendValue(period.payment, period.pricingUnavailableReason)}</span>
            <span role="cell">{formatBackendValue(period.extensionCostBpsPerDay, period.extensionUnavailableReason)}</span>
            <span role="cell">{formatExtensionDays(period.maxExtensionDays, period.extensionUnavailableReason)}</span>
            <span role="cell"><EligibilityBadge eligibility={period.floatDownEligible} /></span>
            <span role="cell">{formatValue(period.sourceRef)}</span>
          </div>
        ))}
      </div>
    </>
  );
}

function LockPeriodCharts({ periods, currentPeriod }: { periods: LockPeriodComparisonPeriod[]; currentPeriod: LockPeriodComparisonPeriod | undefined }) {
  const points = useMemo(() => periods.map((period, index) => ({ period, x: period.days ?? index + 1 })), [periods]);
  if (!periods.length) return <div className="banner banner--blocked" role="alert">No backend lock periods supplied for charts.</div>;
  return (
    <div role="img" aria-label="Rate/Price Chart: backend supplied rate, price, and extension cost by lock period">
      <h3>Rate/Price Chart</h3>
      <p className="field-help">Charts render backend-supplied refs only. Missing values stay N/A and no lock pricing curve is inferred.</p>
      <div className="offer-grid" data-testid="lock-period-chart-series">
        {points.map(({ period, x }) => (
          <article key={period.periodId} className={period.currentLockPeriod ? 'module-card module-card--light offer-card--selected' : 'module-card module-card--light'} data-testid={`lock-period-chart-${period.periodId}`} data-current={period.currentLockPeriod ? 'true' : 'false'}>
            <p className="module-card__route">Period position: {formatValue(x)}</p>
            <strong className="module-card__title">{periodLabel(period)}</strong>
            <dl>
              <dt>Note rate</dt><dd>{formatBackendValue(period.noteRate, period.pricingUnavailableReason)}</dd>
              <dt>Final price</dt><dd>{formatBackendValue(period.finalPriceBps, period.pricingUnavailableReason)}</dd>
              <dt>Payment</dt><dd>{formatBackendValue(period.payment, period.pricingUnavailableReason)}</dd>
              <dt>Extension overlay</dt><dd>{formatBackendValue(period.extensionCostBpsPerDay, period.extensionUnavailableReason)}</dd>
            </dl>
            <ChipList label={`${period.periodId} evidence refs`} values={period.evidenceRefs ?? []} />
          </article>
        ))}
      </div>
      {currentPeriod ? <div className="banner banner--info" role="status" data-testid="lock-period-current-marker">Current period marker: {periodLabel(currentPeriod)}</div> : null}
    </div>
  );
}

function ExtensionAnalysis({ periods, currentPeriod }: { periods: LockPeriodComparisonPeriod[]; currentPeriod: LockPeriodComparisonPeriod | undefined }) {
  if (!periods.length) return <div className="banner banner--blocked" role="alert">No backend extension metadata supplied.</div>;
  return (
    <div role="group" aria-label="Extension analysis">
      <h3>Extension Analysis</h3>
      <p className="field-help">Total cost to extend is displayed only when the backend supplies it; this UI does not multiply cost by days.</p>
      {currentPeriod ? <div className="banner banner--info" role="status">Current period extension options highlighted for {periodLabel(currentPeriod)}.</div> : null}
      <div className="quote-table" role="table" aria-label="Extension analysis table">
        <div role="row" className="quote-table__row quote-table__row--head">
          <span role="columnheader">Period</span>
          <span role="columnheader">Cost bps/day</span>
          <span role="columnheader">Max days</span>
          <span role="columnheader">Total Cost to Extend</span>
          <span role="columnheader">Investor policy ref</span>
        </div>
        {periods.map((period) => (
          <div key={period.periodId} role="row" className={period.currentLockPeriod ? 'quote-table__row quote-table__row--selected' : 'quote-table__row'}>
            <span role="rowheader"><strong>{periodLabel(period)}</strong></span>
            <span role="cell">{formatBackendValue(period.extensionCostBpsPerDay, period.extensionUnavailableReason)}</span>
            <span role="cell">{formatExtensionDays(period.maxExtensionDays, period.extensionUnavailableReason)}</span>
            <span role="cell">{formatBackendValue(period.totalExtensionCost, period.extensionUnavailableReason ?? 'Backend did not supply total extension cost.')}</span>
            <span role="cell">{formatValue(period.investorPolicyRef)}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

function FloatDownAnalysis({ periods }: { periods: LockPeriodComparisonPeriod[] }) {
  if (!periods.length) return <div className="banner banner--blocked" role="alert">No backend float-down metadata supplied.</div>;
  return (
    <div role="group" aria-label="Float-down analysis">
      <h3>Float-Down Analysis</h3>
      <p className="field-help">Float-down eligibility, costs, and limits remain backend-owned. Request controls stay disabled until a lock workflow action contract is supplied.</p>
      <div className="offer-grid" role="list" aria-label="Float-down eligibility by lock period">
        {periods.map((period) => (
          <article key={period.periodId} className="module-card module-card--light" role="listitem">
            <p className="module-card__route">{formatValue(period.sourceRef)}</p>
            <strong className="module-card__title">{periodLabel(period)}</strong>
            <dl>
              <dt>Eligibility</dt><dd><EligibilityBadge eligibility={period.floatDownEligible} /></dd>
              <dt>Reason</dt><dd>{formatValue(period.floatDownReason ?? period.floatDownUnavailableReason)}</dd>
              <dt>Cost</dt><dd>{formatBackendValue(period.floatDownCostBps, period.floatDownUnavailableReason)}</dd>
              <dt>One-time limit</dt><dd>{formatBooleanValue(period.oneTimeLimit, period.floatDownUnavailableReason)}</dd>
            </dl>
            <button type="button" disabled>Request Float-Down</button>
          </article>
        ))}
      </div>
    </div>
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

function findCurrentPeriod(periods: LockPeriodComparisonPeriod[], currentLockPeriod?: string | number | null) {
  return periods.find((period) => period.currentLockPeriod) ?? periods.find((period) => String(period.days) === String(currentLockPeriod));
}

function sortPeriods(periods: LockPeriodComparisonPeriod[], sortKey: SortKey) {
  return [...periods].sort((left, right) => {
    if (sortKey === 'days') return numericSort(left.days, right.days);
    if (sortKey === 'rate') return stringSort(left.noteRate, right.noteRate);
    if (sortKey === 'price') return stringSort(left.finalPriceBps, right.finalPriceBps);
    return stringSort(left.floatDownEligible, right.floatDownEligible);
  });
}

function numericSort(left: string | number | null | undefined, right: string | number | null | undefined) {
  const leftNumber = typeof left === 'number' ? left : Number.parseFloat(String(left ?? ''));
  const rightNumber = typeof right === 'number' ? right : Number.parseFloat(String(right ?? ''));
  if (Number.isFinite(leftNumber) && Number.isFinite(rightNumber)) return leftNumber - rightNumber;
  return stringSort(left, right);
}

function stringSort(left: string | number | null | undefined, right: string | number | null | undefined) {
  return formatValue(left).localeCompare(formatValue(right));
}

function periodLabel(period: LockPeriodComparisonPeriod) {
  if (period.label) return period.label;
  if (period.days !== null && period.days !== undefined && period.days !== '') return `${period.days} days`;
  return 'Lock period not supplied';
}

function EligibilityBadge({ eligibility }: { eligibility: string }) {
  return <span className={`chip ${eligibilityClass(eligibility)}`}>{businessFacingText(eligibility)}</span>;
}

function ChipList({ label, values }: { label: string; values: string[] }) {
  if (!values.length) return null;
  return <ul className="chip-list" aria-label={label}>{values.map((value) => <li key={value}>{value}</li>)}</ul>;
}

function eligibilityClass(value: string) {
  const normalized = value.toUpperCase();
  if (normalized === 'ELIGIBLE') return 'eligibility--eligible';
  if (normalized === 'INELIGIBLE') return 'eligibility--ineligible';
  if (normalized === 'CONDITIONAL') return 'eligibility--conditional';
  return 'eligibility--unknown';
}

function formatBackendValue(value: string | number | boolean | null | undefined, unavailableReason?: string | null) {
  if (value === null || value === undefined || value === '') return unavailableReason ? `N/A - ${unavailableReason}` : 'N/A';
  return formatValue(value);
}

function formatBooleanValue(value: string | boolean | null | undefined, unavailableReason?: string | null) {
  if (value === true) return 'Yes';
  if (value === false) return 'No';
  return formatBackendValue(value, unavailableReason);
}

function formatExtensionDays(value: string | number | null | undefined, unavailableReason?: string | null) {
  if ((value === 0 || value === '0') && unavailableReason) return `No extensions available - ${unavailableReason}`;
  return formatBackendValue(value, unavailableReason);
}

function formatValue(value: string | number | boolean | null | undefined) {
  if (value === null || value === undefined || value === '') return 'Not supplied';
  return String(value);
}

function businessFacingText(value: string | number | null | undefined) {
  if (value === null || value === undefined || value === '') return 'Not supplied';
  return String(value).replace(/[_-]+/g, ' ');
}

function buildCsv(periods: LockPeriodComparisonPeriod[]) {
  const rows = [
    ['Lock Period', 'Note Rate', 'Final Price (bps)', 'Payment', 'Extension Cost (bps/day)', 'Max Extension (days)', 'Float-Down Eligible', 'Source'],
    ...periods.map((period) => [
      periodLabel(period),
      formatBackendValue(period.noteRate, period.pricingUnavailableReason),
      formatBackendValue(period.finalPriceBps, period.pricingUnavailableReason),
      formatBackendValue(period.payment, period.pricingUnavailableReason),
      formatBackendValue(period.extensionCostBpsPerDay, period.extensionUnavailableReason),
      formatBackendValue(period.maxExtensionDays, period.extensionUnavailableReason),
      businessFacingText(period.floatDownEligible),
      formatValue(period.sourceRef),
    ]),
  ];
  return rows.map((row) => row.map(csvEscape).join(',')).join('\n');
}

function csvEscape(value: string) {
  return `"${value.replace(/"/g, '""')}"`;
}
