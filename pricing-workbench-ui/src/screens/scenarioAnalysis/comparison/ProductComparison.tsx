import { useEffect, useMemo, useState } from 'react';
import { DiagnosticsDetails } from '../../../components/DiagnosticsDetails';
import {
  fetchProductComparison,
  type ProductComparisonFeature,
  type ProductComparisonFeatureValue,
  type ProductComparisonProduct,
  type ProductComparisonTotalCostPeriod,
  type ProductComparisonView,
  type ScenarioAnalysisBlocker,
} from '../../../lib/api/scenarioAnalysis';

type ProductComparisonState =
  | { kind: 'loading' }
  | { kind: 'loaded'; view: ProductComparisonView }
  | { kind: 'unreachable'; message: string };

type ActiveTab = 'matrix' | 'total-cost' | 'features';

type ProductComparisonMetricKey = 'noteRate' | 'finalPriceBps' | 'payment' | 'apr' | 'miType' | 'miPremium' | 'fees' | 'term' | 'lockPeriod' | 'eligibility';

const metricRows: Array<{ key: ProductComparisonMetricKey; label: string }> = [
  { key: 'noteRate', label: 'Note Rate' },
  { key: 'finalPriceBps', label: 'Final Price (bps)' },
  { key: 'payment', label: 'Payment' },
  { key: 'apr', label: 'APR' },
  { key: 'miType', label: 'MI Type' },
  { key: 'miPremium', label: 'MI Premium' },
  { key: 'fees', label: 'Fees' },
  { key: 'term', label: 'Term' },
  { key: 'lockPeriod', label: 'Lock Period' },
  { key: 'eligibility', label: 'Eligibility' },
];

export function ProductComparisonScreen({ runId, tenantContext }: { runId: string; tenantContext: string }) {
  const [state, setState] = useState<ProductComparisonState>({ kind: 'loading' });
  const [selectedProductIds, setSelectedProductIds] = useState<string[]>([]);
  const [activeTab, setActiveTab] = useState<ActiveTab>('matrix');
  const [selectionNotice, setSelectionNotice] = useState('');
  const [csvPreview, setCsvPreview] = useState('');

  useEffect(() => {
    let active = true;
    fetchProductComparison(tenantContext, runId)
      .then((view) => {
        if (!active) return;
        setState({ kind: 'loaded', view });
        setSelectedProductIds(initialSelection(view.products));
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'Product comparison is unavailable.';
        if (active) setState({ kind: 'unreachable', message });
      });
    return () => {
      active = false;
    };
  }, [runId, tenantContext]);

  if (state.kind === 'loading') {
    return (
      <section className="panel" aria-labelledby="product-comparison-heading">
        <h2 id="product-comparison-heading">Product Comparison</h2>
        <p role="status">Loading product comparison...</p>
      </section>
    );
  }

  if (state.kind === 'unreachable') {
    return (
      <section className="panel" aria-labelledby="product-comparison-heading">
        <h2 id="product-comparison-heading">Product Comparison</h2>
        <div className="banner banner--blocked" role="alert">{state.message}</div>
      </section>
    );
  }

  const view = state.view;
  const selectedProducts = view.products.filter((product) => selectedProductIds.includes(product.productId));
  const selectionBlocked = selectedProductIds.length < 2;
  const onlyOneEligible = view.products.filter((product) => product.eligibility === 'ELIGIBLE').length === 1;

  function toggleProduct(productId: string) {
    setCsvPreview('');
    setSelectionNotice('');
    setSelectedProductIds((current) => {
      if (current.includes(productId)) return current.filter((id) => id !== productId);
      if (current.length >= 5) {
        setSelectionNotice('Maximum 5 products can be compared.');
        return current;
      }
      return [...current, productId];
    });
  }

  function compareSelected() {
    if (selectedProductIds.length < 2) {
      setSelectionNotice('Select at least 2 products.');
      return;
    }
    setSelectionNotice(`Comparing ${selectedProductIds.length} backend-supplied products.`);
  }

  return (
    <>
      <section className="hero" aria-labelledby="product-comparison-title">
        <p className="eyebrow">Product comparison · PII-24-S32</p>
        <h2 id="product-comparison-title">Product Comparison</h2>
        <p>
          Compare backend-supplied product pricing, eligibility, total cost, feature metadata, and evidence refs for run {runId}.
          The workbench does not calculate mortgage pricing, eligibility, fees, or investor behavior in the browser.
        </p>
        <a href={`/quote/${encodeURIComponent(runId)}/offers`}>Back to Offers</a>
      </section>

      <ProductLayout view={view} selectedProducts={selectedProducts} />

      {view.fallbackReason ? <div className="banner banner--blocked" role="alert"><strong>Backend product comparison contract required</strong><span>{view.fallbackReason}</span></div> : null}
      {onlyOneEligible ? <div className="banner banner--blocked" role="alert">Only 1 eligible product was supplied by the backend.</div> : null}
      <ScenarioBlockerList blockers={view.blockers ?? []} label="Product comparison blockers" />

      <section className="panel" aria-labelledby="product-selector-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Selector</p>
            <h2 id="product-selector-heading">Product Selector</h2>
          </div>
          <button type="button" onClick={compareSelected} disabled={selectionBlocked}>Compare Selected</button>
        </div>
        {view.products.length === 0 ? <div className="banner banner--blocked" role="alert">No products supplied by the backend.</div> : null}
        {selectionBlocked ? <p className="field-help">Select at least 2 products.</p> : null}
        {selectionNotice ? <div className="banner banner--info" role="status">{selectionNotice}</div> : null}
        <div className="offer-grid" role="list" aria-label="Products available for comparison">
          {view.products.map((product) => (
            <label key={product.productId} className={product.currentBestOffer ? 'offer-card offer-card--selected' : 'offer-card'} role="listitem">
              <input type="checkbox" aria-label={productSelectorLabel(product)} checked={selectedProductIds.includes(product.productId)} onChange={() => toggleProduct(product.productId)} />
              <strong>{formatValue(product.label)}</strong>
              {product.currentBestOffer ? <span className="chip">Current best offer</span> : null}
              <span>{formatValue(product.productType)} · {formatValue(product.investor)} · {formatValue(product.channel)}</span>
              <span><EligibilityBadge eligibility={product.eligibility} /></span>
              {product.eligibilityReason ? <span>{product.eligibilityReason}</span> : null}
            </label>
          ))}
        </div>
      </section>

      <section className="panel" aria-labelledby="product-comparison-tabs-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Analysis</p>
            <h2 id="product-comparison-tabs-heading">Comparison Matrix, Total Cost, and Feature Checklist</h2>
          </div>
          <div className="offer-toolbar" role="tablist" aria-label="Product comparison views">
            <button type="button" role="tab" aria-selected={activeTab === 'matrix'} onClick={() => setActiveTab('matrix')}>Comparison Matrix</button>
            <button type="button" role="tab" aria-selected={activeTab === 'total-cost'} onClick={() => setActiveTab('total-cost')}>Total Cost</button>
            <button type="button" role="tab" aria-selected={activeTab === 'features'} onClick={() => setActiveTab('features')}>Feature Checklist</button>
          </div>
        </div>
        {activeTab === 'matrix' ? <ComparisonMatrix products={selectedProducts} onExport={(csv) => setCsvPreview(csv)} /> : null}
        {activeTab === 'total-cost' ? <TotalCost products={selectedProducts} /> : null}
        {activeTab === 'features' ? <FeatureChecklist products={selectedProducts} features={view.features ?? []} /> : null}
        {csvPreview ? <pre className="diagnostics-details" aria-label="Product comparison CSV preview">{csvPreview}</pre> : null}
      </section>
    </>
  );
}

function ProductLayout({ view, selectedProducts }: { view: ProductComparisonView; selectedProducts: ProductComparisonProduct[] }) {
  return (
    <section className="panel sticky-header" aria-labelledby="product-comparison-heading">
      <div className="panel-heading-row">
        <div>
          <p className="eyebrow">Run {view.runId}</p>
          <h2 id="product-comparison-heading">Product comparison summary</h2>
        </div>
        <DiagnosticsDetails items={[`Support reference: ${view.uiTraceId}`, `Dependency: ${view.dependencyStatus}`]} />
      </div>
      <dl className="status-grid">
        <dt>Products supplied</dt><dd>{view.products.length}</dd>
        <dt>Products selected</dt><dd>{selectedProducts.length}</dd>
        <dt>Current best offer</dt><dd>{formatValue(view.products.find((product) => product.currentBestOffer)?.label)}</dd>
        <dt>Total cost source</dt><dd>{formatValue(view.metadata?.totalCostSourceRef)}</dd>
      </dl>
      <ChipList label="Product comparison export refs" values={view.exportRefs ?? []} />
      <ChipList label="Product comparison audit refs" values={view.auditRefs ?? []} />
      <ChipList label="Product comparison events" values={(view.events ?? []).map(businessFacingText)} />
    </section>
  );
}

function ComparisonMatrix({ products, onExport }: { products: ProductComparisonProduct[]; onExport: (csv: string) => void }) {
  if (products.length < 2) return <div className="banner banner--blocked" role="alert">Select at least 2 products to render the comparison matrix.</div>;
  return (
    <>
      <div className="panel-heading-row">
        <h3>Comparison Matrix</h3>
        <button type="button" onClick={() => onExport(buildCsv(products))}>Export Matrix</button>
      </div>
      <div className="quote-table" role="table" aria-label="Product comparison matrix">
        <div role="row" className="quote-table__row quote-table__row--head">
          <span role="columnheader">Metric</span>
          {products.map((product) => <span key={product.productId} role="columnheader">{formatValue(product.label)}</span>)}
        </div>
        {metricRows.map((metric) => (
          <div key={metric.key} role="row" className="quote-table__row">
            <span role="rowheader"><strong>{metric.label}</strong></span>
            {products.map((product) => (
              <span key={`${product.productId}-${metric.key}`} role="cell" className={product.currentBestOffer ? 'quote-table__row--selected' : undefined}>
                {metric.key === 'eligibility' ? <EligibilityBadge eligibility={product.eligibility} /> : formatPricingValue(product[metric.key], product.pricingUnavailableReason)}
              </span>
            ))}
          </div>
        ))}
      </div>
    </>
  );
}

function TotalCost({ products }: { products: ProductComparisonProduct[] }) {
  const periods = useMemo(() => Array.from(new Set(products.flatMap((product) => (product.totalCostPeriods ?? []).map((period) => period.periodId)))), [products]);
  if (products.length < 2) return <div className="banner banner--blocked" role="alert">Select at least 2 products to render total-cost comparison.</div>;
  if (periods.length === 0) return <div className="banner banner--blocked" role="alert">No backend total-cost periods supplied. The UI will not calculate total cost locally.</div>;
  return (
    <div role="img" aria-label="Total Cost chart: backend supplied cost of credit by product">
      <h3>Total Cost</h3>
      <p className="field-help">Bars and breakdowns render backend-supplied totals only; missing values stay N/A.</p>
      <div className="offer-grid" role="list" aria-label="Total cost breakdown by product">
        {products.map((product) => (
          <article key={product.productId} className={product.currentBestOffer ? 'module-card module-card--light offer-card--selected' : 'module-card module-card--light'} role="listitem">
            <h4>{formatValue(product.label)}{product.currentBestOffer ? ' · Current best offer' : ''}</h4>
            {(product.totalCostPeriods ?? []).map((period) => <TotalCostPeriod key={period.periodId} productLabel={product.label} period={period} />)}
          </article>
        ))}
      </div>
    </div>
  );
}

function TotalCostPeriod({ productLabel, period }: { productLabel: string; period: ProductComparisonTotalCostPeriod }) {
  return (
    <dl className="status-grid" aria-label={`${productLabel} ${period.label} total cost breakdown`}>
      <dt>{formatValue(period.label)}</dt><dd>{formatPricingValue(period.totalCost, period.unavailableReason)}</dd>
      <dt>Principal</dt><dd>{formatPricingValue(period.principal, period.unavailableReason)}</dd>
      <dt>Interest</dt><dd>{formatPricingValue(period.interest, period.unavailableReason)}</dd>
      <dt>MI</dt><dd>{formatPricingValue(period.mortgageInsurance, period.unavailableReason)}</dd>
      <dt>Fees</dt><dd>{formatPricingValue(period.fees, period.unavailableReason)}</dd>
      <dt>Source</dt><dd>{formatValue(period.sourceRef)}</dd>
    </dl>
  );
}

function FeatureChecklist({ products, features }: { products: ProductComparisonProduct[]; features: ProductComparisonFeature[] }) {
  if (products.length < 2) return <div className="banner banner--blocked" role="alert">Select at least 2 products to render feature checklist.</div>;
  if (!features.length) return <div className="banner banner--blocked" role="alert">No backend feature metadata supplied.</div>;
  return (
    <>
      <div className="panel-heading-row">
        <h3>Feature Checklist</h3>
        <a href="/products/catalog">View Product Details</a>
      </div>
      <div className="quote-table" role="table" aria-label="Product feature checklist">
        <div role="row" className="quote-table__row quote-table__row--head">
          <span role="columnheader">Feature</span>
          {products.map((product) => <span key={product.productId} role="columnheader">{formatValue(product.label)}</span>)}
        </div>
        {features.map((feature) => (
          <div key={feature.featureId} role="row" className="quote-table__row">
            <span role="rowheader"><strong>{formatValue(feature.label)}</strong></span>
            {products.map((product) => <span key={`${feature.featureId}-${product.productId}`} role="cell">{formatFeatureValue(feature.valuesByProductId[product.productId])}</span>)}
          </div>
        ))}
      </div>
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

function initialSelection(products: ProductComparisonProduct[]) {
  const bestOffer = products.find((product) => product.currentBestOffer)?.productId;
  const selected = bestOffer ? [bestOffer] : [];
  for (const product of products) {
    if (selected.length >= 2) break;
    if (!selected.includes(product.productId)) selected.push(product.productId);
  }
  return selected;
}

function productSelectorLabel(product: ProductComparisonProduct) {
  return `${formatValue(product.label)} · ${formatValue(product.productType)} · ${formatValue(product.investor)} · ${formatValue(product.channel)}`;
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

function formatPricingValue(value: string | number | boolean | null | undefined, unavailableReason?: string | null) {
  if (value === null || value === undefined || value === '') return unavailableReason ? `N/A - ${unavailableReason}` : 'N/A';
  return formatValue(value);
}

function formatFeatureValue(value: ProductComparisonFeatureValue | undefined) {
  if (value === true) return 'Yes';
  if (value === false) return 'No';
  if (value === null || value === undefined || value === '') return 'N/A';
  return String(value);
}

function formatValue(value: string | number | boolean | null | undefined) {
  if (value === null || value === undefined || value === '') return 'Not supplied';
  return String(value);
}

function businessFacingText(value: string | number | null | undefined) {
  if (value === null || value === undefined || value === '') return 'Not supplied';
  return String(value).replace(/[_-]+/g, ' ');
}

function buildCsv(products: ProductComparisonProduct[]) {
  const rows = [
    ['Metric', ...products.map((product) => formatValue(product.label))],
    ...metricRows.map((metric) => [
      metric.label,
      ...products.map((product) => metric.key === 'eligibility' ? businessFacingText(product.eligibility) : formatPricingValue(product[metric.key], product.pricingUnavailableReason)),
    ]),
  ];
  return rows.map((row) => row.map(csvEscape).join(',')).join('\n');
}

function csvEscape(value: string) {
  return `"${value.replace(/"/g, '""')}"`;
}
