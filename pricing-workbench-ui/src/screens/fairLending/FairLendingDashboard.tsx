import { useEffect, useMemo, useState } from 'react';
import { fetchFairLendingViolations, runFairLendingAnalysis, type AIRTable, type FairLendingReport, type FairLendingViolation, type RegressionResult } from '../../lib/api/fairLending';

type FairLendingDashboardProps = {
  tenantId?: string;
  initialReport?: FairLendingReport;
  initialViolations?: FairLendingViolation[];
  fetchImpl?: typeof fetch;
};

type FairLendingState =
  | { kind: 'loading' }
  | { kind: 'ready'; violations: FairLendingViolation[]; report?: FairLendingReport }
  | { kind: 'blocked'; message: string };

const defaultTenantId = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';

export function FairLendingDashboard({ tenantId = defaultTenantId, initialReport, initialViolations, fetchImpl }: FairLendingDashboardProps) {
  const [state, setState] = useState<FairLendingState>(() => initialReport ? { kind: 'ready', violations: initialReport.violations, report: initialReport } : initialViolations ? { kind: 'ready', violations: initialViolations } : { kind: 'loading' });
  const [selected, setSelected] = useState<FairLendingViolation | null>(null);

  useEffect(() => {
    if (initialReport || initialViolations) return;
    let active = true;
    fetchFairLendingViolations(tenantId, fetchImpl)
      .then((violations) => { if (active) setState({ kind: 'ready', violations }); })
      .catch((error: unknown) => { if (active) setState({ kind: 'blocked', message: error instanceof Error ? error.message : 'Fair lending data is unavailable.' }); });
    return () => { active = false; };
  }, [fetchImpl, initialReport, initialViolations, tenantId]);

  const runAnalysis = async () => {
    const report = await runFairLendingAnalysis({ tenantId, startDate: '2026-06-01', endDate: '2026-06-30', protectedClasses: ['RACE', 'ETHNICITY', 'SEX', 'AGE'], outcomes: ['NOTE_RATE', 'PRICE', 'TOTAL_LLPA_BPS', 'MARGIN_BPS'], controls: ['FICO', 'LTV', 'DTI', 'LOAN_AMOUNT', 'APPLICANT_AGE', 'APPLICANT_AGE_SQUARED'], marginalEffectThreshold: 0.1 }, fetchImpl);
    setState({ kind: 'ready', violations: report.violations, report });
  };

  if (state.kind === 'loading') return <section className="panel" aria-labelledby="fair-lending-heading"><h2 id="fair-lending-heading">Fair Lending Analysis</h2><p role="status">Loading fair lending violations...</p></section>;
  if (state.kind === 'blocked') return <section className="panel" aria-labelledby="fair-lending-heading"><h2 id="fair-lending-heading">Fair Lending Analysis</h2><div className="banner banner--blocked" role="alert"><strong>Fair lending service unavailable</strong><span>{state.message}</span></div></section>;

  const report = state.report;
  const rows = report ? flattenReport(report) : state.violations.map((violation) => ({ violation, regression: undefined, air: undefined }));
  const summary = summarize(rows.map((row) => row.violation));

  return (
    <>
      <section className="hero hero--compliance" aria-labelledby="fair-lending-title">
        <p className="eyebrow">Compliance / Fair lending - PII-31-S01</p>
        <h2 id="fair-lending-title">Fair Lending Analysis</h2>
        <p>Disparate impact monitoring for pricing outcomes by protected class, AIR, regression significance, and marginal effect evidence.</p>
      </section>
      <section className="panel" aria-labelledby="fair-lending-heading">
        <div className="panel-heading-row"><div><p className="eyebrow">Tenant {tenantId}</p><h2 id="fair-lending-heading">Analysis dashboard</h2></div><div className="button-row"><button type="button" onClick={runAnalysis}>Run Analysis</button><button type="button">Export</button></div></div>
        <dl className="status-grid"><dt>Period</dt><dd>{report ? `${report.startDate} - ${report.endDate}` : 'Latest violations'}</dd><dt>Sample</dt><dd>{report?.sampleSize ?? 'Report not run'}</dd><dt>Violations</dt><dd>{summary.violation}</dd><dt>Watch</dt><dd>{summary.watch}</dd></dl>
        <FairLendingTable rows={rows} onSelect={(violation) => setSelected(violation)} />
      </section>
      {selected ? <ViolationDetailModal violation={selected} report={report} onClose={() => setSelected(null)} /> : null}
    </>
  );
}

function FairLendingTable({ rows, onSelect }: { rows: { violation: FairLendingViolation; regression?: RegressionResult; air?: AIRTable }[]; onSelect: (violation: FairLendingViolation) => void }) {
  return <div className="quote-table" role="table" aria-label="Fair lending analysis results"><div role="row" className="quote-table__row quote-table__row--head"><span role="columnheader">Outcome</span><span role="columnheader">Class</span><span role="columnheader">Group</span><span role="columnheader">AIR</span><span role="columnheader">p-value</span><span role="columnheader">Status</span><span role="columnheader">Action</span></div>{rows.length === 0 ? <div role="row" className="quote-table__row"><span role="cell">No fair lending violations detected.</span></div> : rows.map(({ violation, regression, air }) => <div role="row" className="quote-table__row" key={`${violation.outcome}-${violation.protectedClass}-${violation.group}-${violation.violationType}`}><span role="cell">{display(violation.outcome)}</span><span role="cell">{display(violation.protectedClass)}</span><span role="cell">{display(violation.group)}</span><span role="cell">{formatNumber(air?.airRatios[violation.group] ?? (violation.violationType === 'AIR_FOUR_FIFTHS' ? violation.value : undefined))}</span><span role="cell">{formatNumber(regression?.pValues[violation.group] ?? (violation.violationType === 'REGRESSION_SIGNIFICANT' ? violation.value : undefined))}</span><span role="cell">{violation.severity === 'CRITICAL' || violation.severity === 'HIGH' ? 'VIOLATION' : 'WATCH'}</span><span role="cell"><button type="button" onClick={() => onSelect(violation)}>Drill-down</button></span></div>)}</div>;
}

function ViolationDetailModal({ violation, report, onClose }: { violation: FairLendingViolation; report?: FairLendingReport; onClose: () => void }) {
  const regression = report?.regressionResults.find((item) => item.outcome === violation.outcome && item.protectedClass === violation.protectedClass);
  const air = report?.airTables.find((item) => item.outcome === violation.outcome && item.protectedClass === violation.protectedClass);
  return <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="fair-lending-modal-title"><div className="modal-card"><h2 id="fair-lending-modal-title">Violation Detail: {display(violation.outcome)} ~ {display(violation.protectedClass)} ({display(violation.group)})</h2><p>{violation.recommendedAction}</p><h3>Regression coefficients</h3><dl className="status-grid">{regression ? Object.entries(regression.coefficients).map(([group, coefficient]) => <><dt key={`${group}-coef`}>{display(group)}</dt><dd key={`${group}-value`}>β={formatNumber(coefficient)} p={formatNumber(regression.pValues[group])} CI={regression.confidenceIntervals[group]} R²={formatNumber(regression.rSquared)}</dd></>) : <><dt>Regression</dt><dd>Report details are not loaded.</dd></>}</dl><h3>AIR table</h3><dl className="status-grid">{air ? Object.entries(air.airRatios).map(([group, ratio]) => <><dt key={`${group}-air`}>{display(group)}</dt><dd key={`${group}-air-value`}>AIR={formatNumber(ratio)} favorable={air.favorableCounts[group] ?? 0}/{air.totalCounts[group] ?? 0} reference={air.referenceGroup}</dd></>) : <><dt>AIR</dt><dd>Report details are not loaded.</dd></>}</dl><button type="button" onClick={onClose}>Close</button></div></div>;
}

function flattenReport(report: FairLendingReport) {
  return report.violations.map((violation) => ({ violation, regression: report.regressionResults.find((item) => item.outcome === violation.outcome && item.protectedClass === violation.protectedClass), air: report.airTables.find((item) => item.outcome === violation.outcome && item.protectedClass === violation.protectedClass) }));
}

function summarize(violations: FairLendingViolation[]) {
  return { violation: violations.filter((violation) => violation.severity === 'CRITICAL' || violation.severity === 'HIGH').length, watch: violations.filter((violation) => violation.severity === 'MEDIUM').length };
}

function display(value: string) { return value.toLowerCase().replace(/[_-]+/g, ' ').replace(/\b\w/g, (character) => character.toUpperCase()); }
function formatNumber(value?: number) { return typeof value === 'number' && Number.isFinite(value) ? value.toFixed(value < 0.1 ? 3 : 2) : '—'; }

export function fairLendingDashboardEvidence(report?: FairLendingReport) {
  return { screenId: 'fair-lending-dashboard', route: '/compliance/fair-lending', reportId: report?.reportId ?? '', violationCount: report?.violations.length ?? 0 };
}

export default FairLendingDashboard;
