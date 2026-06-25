import { MajorFunctionalityPage, type EvidenceCapture, type FunctionalityPageConfig } from '../shared/MajorFunctionalityPage';
import type { ScreenVisualState } from '../contract/ScreenProps';

type PricingRow = { id: string; scenario: string; evidence: string; status: string };

export const pricingAnalysisEvidenceTarget = '.local-harness/evidence/PII-25-S04/pricing-analysis.json';

const pricingConfig: FunctionalityPageConfig<PricingRow> = {
  screenId: 'pricing-analysis',
  evidenceTarget: pricingAnalysisEvidenceTarget,
  breadcrumb: 'Pricing / Analysis',
  eyebrow: 'Pricing analysis service',
  title: 'Pricing Analysis',
  summary: 'Reviews waterfall, margin, adjustment, scenario comparison, profitability floor, and export readiness evidence.',
  dataBoundary: 'pricing-service: GET /api/v1/pricing/waterfall/:runId',
  sections: [
    { id: 'waterfall-view', eyebrow: 'Waterfall', title: 'Waterfall View', summary: 'Read-only step references from backend evidence.', status: 'ready', items: ['Base selection ref', 'Adjustment refs', 'Final output ref'] },
    { id: 'margin-analysis', eyebrow: 'Margin', title: 'Margin Analysis', summary: 'Margin refs and redaction status.', status: 'needs-attention', items: ['Margin evidence', 'Redaction marker', 'Approval owner'] },
    { id: 'adjustment-evidence', eyebrow: 'Adjustments', title: 'Adjustment Evidence', summary: 'Adjustment reason-code evidence.', status: 'ready', items: ['Reason refs', 'Trace refs', 'Audit refs'] },
    { id: 'scenario-comparison', eyebrow: 'Scenarios', title: 'Scenario Comparison', summary: 'Side-by-side scenario comparison.', status: 'ready', items: ['Baseline scenario', 'Comparison scenario', 'Delta refs'] },
    { id: 'profitability-floor', eyebrow: 'Floor', title: 'Profitability Floor', summary: 'Floor policy reference, not a hardcoded threshold.', status: 'blocked', items: ['Floor policy ref', 'Exception owner', 'Approval ref'] },
    { id: 'export', eyebrow: 'Export', title: 'Export', summary: 'Controlled export payloads with evidence metadata.', status: 'empty', items: ['CSV metadata', 'JSON metadata', 'PDF unavailable'] },
  ],
  metrics: [
    { label: 'Mode', value: 'Review', help: 'Pricing values come from connected pricing records.' },
    { label: 'Comparison', value: '2-up', help: 'Side-by-side scenarios' },
    { label: 'Evidence', value: 'Traceable', help: 'Review records stay available for audit.' },
  ],
  rows: [
    { id: 'pricing-base', scenario: 'Baseline scenario', evidence: 'waterfall-ref-required', status: 'ready' },
    { id: 'pricing-compare', scenario: 'Comparison scenario', evidence: 'scenario-compare-ref', status: 'needs-attention' },
    { id: 'pricing-floor', scenario: 'Profitability floor', evidence: 'floor-policy-ref-required', status: 'blocked' },
  ],
  columns: [
    { key: 'scenario', header: 'Scenario' },
    { key: 'evidence', header: 'Evidence ref' },
    { key: 'status', header: 'Status', render: (row) => <span className={`functionality-badge functionality-badge--${row.status}`}>{row.status}</span> },
  ],
  tableCaption: 'Pricing analysis evidence records',
  primaryActions: [{ id: 'export-analysis', label: 'Export analysis', variant: 'primary' }],
  secondaryActions: [{ id: 'review-waterfall', label: 'Review waterfall' }, { id: 'compare-scenarios', label: 'Compare scenarios' }],
  emptyMessage: 'No pricing run has been selected for analysis.',
  blockedMessage: 'Pricing analysis is blocked until pricing-service waterfall and floor references are available.',
  attentionMessage: 'Margin and floor references need review before export.',
  renderSpotlight: () => (
    <section className="section-card" aria-labelledby="waterfall-chart-heading">
      <h2 id="waterfall-chart-heading">Interactive waterfall chart</h2>
      <div className="waterfall-bars" aria-label="Read-only waterfall chart">
        {[['Base selection', '82%'], ['Adjustment evidence', '62%'], ['Margin review', '48%']].map(([label, width]) => <div className="waterfall-bar" key={label}><span>{label}</span><span style={{ width }} /></div>)}
      </div>
      <p>No local pricing calculations are performed; values are visual readiness placeholders until connected pricing records provide evidence.</p>
    </section>
  ),
};

export function PricingAnalysisScreen({ visualState, onEvidenceCapture }: { visualState?: ScreenVisualState; onEvidenceCapture?: EvidenceCapture }) {
  return <MajorFunctionalityPage config={pricingConfig} visualState={visualState} onEvidenceCapture={onEvidenceCapture} />;
}

export default PricingAnalysisScreen;
