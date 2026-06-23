import { useEffect, useMemo, useState } from 'react';
import type { ChangeEvent, FormEvent } from 'react';
import { fetchProductAdmin, type ProductAdminMapping, type ProductAdminProduct, type ProductAdminStipulation, type ProductAdminView } from '../../lib/api/products';
import { usePageActions } from '../../layout/PageActionsContext';

type ProductManagementScreenProps = {
  fetchImpl?: typeof fetch;
  tenantContext?: string;
};

type ProductManagementState =
  | { kind: 'loading' }
  | { kind: 'ready'; view: ProductAdminView; source: 'backend' | 'preview' }
  | { kind: 'blocked'; view: ProductAdminView; message: string };

type ProductDetailTab = 'General' | 'Pricing' | 'Eligibility' | 'Stipulations' | 'Adjustments';
type SlideOver = { kind: 'add' } | { kind: 'detail'; productId: string } | null;

type ManagedProduct = ProductAdminProduct & {
  mortgageType: string;
  rateMin: string;
  rateMax: string;
  states: string[];
  counties: string[];
  termMonths: string;
  amortizationType: string;
  lienPosition: string;
  documentationType: string;
  incomeType: string;
  lockDays: string;
  effectiveFrom: string;
  effectiveTo: string;
  eligibilityJson: string;
  stipulationRefs: string[];
  adjustmentRefs: string[];
};

type ProductFilters = {
  investor: string;
  channel: string;
  productType: string;
  status: string;
  mortgageType: string;
};

const blankFilters: ProductFilters = {
  investor: 'All',
  channel: 'All',
  productType: 'All',
  status: 'All',
  mortgageType: 'All',
};

const detailTabs: ProductDetailTab[] = ['General', 'Pricing', 'Eligibility', 'Stipulations', 'Adjustments'];

const previewView: ProductAdminView = {
  tenantContext: 'Product workspace',
  dependencyStatus: 'PRODUCT_CATALOG_PREVIEW',
  fallbackReason: 'catalog preview',
  uiTraceId: 'product-management-preview-trace',
  lifecycle: ['DRAFT', 'ACTIVE', 'DISABLED', 'REVIEW', 'PUBLISHED', 'DEPRECATED'],
  pricingRuleSets: ['catalog-rate-reference', 'investor-margin-reference', 'adjustment-grid-reference'],
  products: [
    {
      productId: 'preview-product-1',
      productCode: 'LP_SETUP',
      productName: 'LoanPass setup product',
      productType: 'Agency',
      investorCode: 'INV_SETUP',
      channelCode: 'Retail',
      status: 'DRAFT',
      description: 'Preview product',
      minLoanAmount: null,
      maxLoanAmount: null,
      minFico: null,
      maxLtv: null,
      maxDti: null,
      propertyTypes: ['SFR', 'Condo'],
      occupancyTypes: ['Primary', 'Second Home'],
      loanPurposes: ['Purchase', 'Refinance'],
      pricingRuleSet: 'catalog-rate-reference',
      version: 1,
      changeSummary: 'Preview',
    },
  ],
  stipulations: [
    {
      stipulationId: 'stip-preview-1',
      stipulationCode: 'ASSET_DOC',
      stipulationName: 'Asset documentation',
      category: 'CONDITION',
      severity: 'CONDITIONAL',
      description: 'Preview stipulation',
      validationRule: null,
      appliesToProductTypes: ['Agency'],
    },
  ],
  mappings: [{ productId: 'preview-product-1', stipulationId: 'stip-preview-1', isRequired: true, conditionExpression: null, displayOrder: 1 }],
};

export function ProductManagementScreen({ fetchImpl = fetch, tenantContext = 'ui-preview-tenant' }: ProductManagementScreenProps) {
  const { setPromotedActions } = usePageActions();
  const [state, setState] = useState<ProductManagementState>({ kind: 'loading' });
  const [products, setProducts] = useState<ManagedProduct[]>([]);
  const [filters, setFilters] = useState<ProductFilters>(blankFilters);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [slideOver, setSlideOver] = useState<SlideOver>(null);
  const [activeTab, setActiveTab] = useState<ProductDetailTab>('General');
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [bulkInvestor, setBulkInvestor] = useState('');
  const [exportText, setExportText] = useState('');

  useEffect(() => {
    let active = true;
    fetchProductAdmin(fetchImpl)
      .then((view) => {
        if (!active) return;
        setState({ kind: 'ready', view, source: 'backend' });
        setProducts(view.products.map(toManagedProduct));
      })
      .catch((error: unknown) => {
        if (!active) return;
        const message = error instanceof Error ? error.message : 'blocked';
        const view = { ...previewView, tenantContext };
        setState({ kind: 'blocked', view, message });
        setProducts(view.products.map(toManagedProduct));
      });
    return () => {
      active = false;
    };
  }, [fetchImpl, tenantContext]);

  const view = state.kind === 'loading' ? null : state.view;
  const stipulations = view?.stipulations ?? [];
  const mappings = view?.mappings ?? [];
  const pricingRuleSets = view?.pricingRuleSets ?? [];
  const lifecycleOptions = useMemo(() => Array.from(new Set([...(view?.lifecycle ?? []), 'DRAFT', 'ACTIVE', 'DISABLED'])).filter(Boolean), [view?.lifecycle]);

  const filterOptions = useMemo(() => ({
    investor: selectOptions(products.map((product) => product.investorCode)),
    channel: selectOptions(products.map((product) => product.channelCode)),
    productType: selectOptions(products.map((product) => product.productType)),
    status: selectOptions(products.map((product) => product.status)),
    mortgageType: selectOptions(products.map((product) => product.mortgageType)),
  }), [products]);

  const filteredProducts = useMemo(() => products.filter((product) => matchesFilters(product, filters)), [products, filters]);
  const selectedProducts = useMemo(() => products.filter((product) => selectedIds.has(product.productId)), [products, selectedIds]);
  const activeProduct = slideOver?.kind === 'detail' ? products.find((product) => product.productId === slideOver.productId) ?? null : null;

  function updateProduct(productId: string, updater: (product: ManagedProduct) => ManagedProduct) {
    setProducts((current) => current.map((product) => product.productId === productId ? updater(product) : product));
  }

  function patchProduct(productId: string, patch: Partial<ManagedProduct>) {
    updateProduct(productId, (product) => ({ ...product, ...patch, version: product.version + 1, changeSummary: 'Local quick edit' }));
  }

  function toggleSelected(productId: string) {
    setSelectedIds((current) => {
      const next = new Set(current);
      if (next.has(productId)) next.delete(productId);
      else next.add(productId);
      return next;
    });
  }

  function bulkStatus(status: string) {
    setProducts((current) => current.map((product) => selectedIds.has(product.productId) ? { ...product, status, version: product.version + 1, changeSummary: `Bulk ${status}` } : product));
  }

  function assignBulkInvestor() {
    const investorCode = bulkInvestor.trim();
    if (!investorCode) return;
    setProducts((current) => current.map((product) => selectedIds.has(product.productId) ? { ...product, investorCode, version: product.version + 1, changeSummary: 'Bulk investor assignment' } : product));
    setBulkInvestor('');
  }

  function exportSelected() {
    const payload = selectedProducts.length ? selectedProducts : filteredProducts;
    setExportText(JSON.stringify(payload.map(toExportProduct), null, 2));
  }

  const promotedProductActions = useMemo(() => (
    <div className="pm-actions" aria-label="Product management actions">
      <button type="button" className="pm-primary" onClick={() => setSlideOver({ kind: 'add' })}>Add Product</button>
      <button type="button" onClick={() => bulkStatus('ACTIVE')} disabled={!selectedIds.size}>Enable</button>
      <button type="button" onClick={() => bulkStatus('DISABLED')} disabled={!selectedIds.size}>Disable</button>
      <button type="button" onClick={assignBulkInvestor} disabled={!selectedIds.size || !bulkInvestor.trim()}>Assign</button>
      <button type="button" onClick={exportSelected}>Export</button>
    </div>
  ), [bulkInvestor, filteredProducts, selectedIds.size, selectedProducts]);

  useEffect(() => {
    setPromotedActions({ label: 'Product management page actions', actions: promotedProductActions });
    return () => setPromotedActions(null);
  }, [promotedProductActions, setPromotedActions]);

  function createProduct(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const productCode = fieldValue(data, 'productCode').toUpperCase();
    const productName = fieldValue(data, 'productName');
    if (!productCode || !productName) return;
    const product: ManagedProduct = {
      productId: `local-${productCode.toLowerCase()}-${Date.now()}`,
      productCode,
      productName,
      productType: fieldValue(data, 'productType') || 'Unassigned',
      investorCode: fieldValue(data, 'investorCode') || 'Unassigned',
      channelCode: fieldValue(data, 'channelCode') || 'Unassigned',
      status: fieldValue(data, 'status') || 'DRAFT',
      description: fieldValue(data, 'description'),
      minLoanAmount: optionalNumber(data, 'minLoanAmount'),
      maxLoanAmount: optionalNumber(data, 'maxLoanAmount'),
      minFico: optionalNumber(data, 'minFico'),
      maxLtv: optionalNumber(data, 'maxLtv'),
      maxDti: optionalNumber(data, 'maxDti'),
      propertyTypes: splitValues(fieldValue(data, 'propertyTypes')),
      occupancyTypes: splitValues(fieldValue(data, 'occupancyTypes')),
      loanPurposes: splitValues(fieldValue(data, 'loanPurposes')),
      pricingRuleSet: fieldValue(data, 'pricingRuleSet'),
      version: 1,
      changeSummary: 'Local product draft',
      mortgageType: fieldValue(data, 'mortgageType') || 'Unassigned',
      rateMin: fieldValue(data, 'rateMin'),
      rateMax: fieldValue(data, 'rateMax'),
      states: splitValues(fieldValue(data, 'states')),
      counties: splitValues(fieldValue(data, 'counties')),
      termMonths: fieldValue(data, 'termMonths'),
      amortizationType: fieldValue(data, 'amortizationType'),
      lienPosition: fieldValue(data, 'lienPosition'),
      documentationType: fieldValue(data, 'documentationType'),
      incomeType: fieldValue(data, 'incomeType'),
      lockDays: fieldValue(data, 'lockDays'),
      effectiveFrom: fieldValue(data, 'effectiveFrom'),
      effectiveTo: fieldValue(data, 'effectiveTo'),
      eligibilityJson: fieldValue(data, 'eligibilityJson'),
      stipulationRefs: splitValues(fieldValue(data, 'stipulationRefs')),
      adjustmentRefs: splitValues(fieldValue(data, 'adjustmentRefs')),
    };
    setProducts((current) => [product, ...current]);
    setSlideOver({ kind: 'detail', productId: product.productId });
    setActiveTab('General');
  }

  if (state.kind === 'loading') {
    return (
      <section className="pm-shell pm-shell--loading" aria-labelledby="product-management-title">
        <style>{productManagementStyles}</style>
        <div className="pm-glass pm-loader" role="status">Loading</div>
      </section>
    );
  }

  return (
    <section className="pm-shell" aria-labelledby="product-management-title">
      <style>{productManagementStyles}</style>
      <aside className={`pm-sidebar pm-glass ${sidebarCollapsed ? 'pm-sidebar--collapsed' : ''}`} aria-label="Product filters">
        <button className="pm-icon-button" type="button" onClick={() => setSidebarCollapsed((value) => !value)} aria-label="Toggle filters">{sidebarCollapsed ? '›' : '‹'}</button>
        <div className="pm-filter-title">Filters</div>
        <FilterSelect collapsed={sidebarCollapsed} label="Investor" value={filters.investor} options={filterOptions.investor} onChange={(value) => setFilters((current) => ({ ...current, investor: value }))} />
        <FilterSelect collapsed={sidebarCollapsed} label="Channel" value={filters.channel} options={filterOptions.channel} onChange={(value) => setFilters((current) => ({ ...current, channel: value }))} />
        <FilterSelect collapsed={sidebarCollapsed} label="Product Type" value={filters.productType} options={filterOptions.productType} onChange={(value) => setFilters((current) => ({ ...current, productType: value }))} />
        <FilterSelect collapsed={sidebarCollapsed} label="Status" value={filters.status} options={filterOptions.status} onChange={(value) => setFilters((current) => ({ ...current, status: value }))} />
        <FilterSelect collapsed={sidebarCollapsed} label="Mortgage Type" value={filters.mortgageType} options={filterOptions.mortgageType} onChange={(value) => setFilters((current) => ({ ...current, mortgageType: value }))} />
        {!sidebarCollapsed ? <button type="button" className="pm-secondary" onClick={() => setFilters(blankFilters)}>Clear</button> : null}
      </aside>

      <main className="pm-main">
        <header className="pm-toolbar pm-glass">
          <div>
            <p className="pm-kicker">Product workspace</p>
            <h1 id="product-management-title">Product Management</h1>
            {state.kind === 'blocked' ? <span className="pm-pill pm-pill--warn">Connected product catalog unavailable</span> : null}
          </div>
          <div className="pm-actions">
            <button type="button" className="pm-primary" onClick={() => setSlideOver({ kind: 'add' })}>Add Product</button>
          </div>
        </header>

        <section className="pm-bulk pm-glass" aria-label="Bulk actions">
          <label className="pm-checkline"><input type="checkbox" checked={filteredProducts.length > 0 && filteredProducts.every((product) => selectedIds.has(product.productId))} onChange={(event) => setSelectedIds(event.target.checked ? new Set(filteredProducts.map((product) => product.productId)) : new Set())} /> {selectedIds.size}</label>
          <details className="pm-bulk-details">
            <summary>Bulk actions</summary>
            <div className="pm-bulk-details__controls">
              <button type="button" onClick={() => bulkStatus('ACTIVE')} disabled={!selectedIds.size}>Enable</button>
              <button type="button" onClick={() => bulkStatus('DISABLED')} disabled={!selectedIds.size}>Disable</button>
              <input value={bulkInvestor} onChange={(event) => setBulkInvestor(event.target.value)} placeholder="Investor" aria-label="Bulk investor" />
              <button type="button" onClick={assignBulkInvestor} disabled={!selectedIds.size || !bulkInvestor.trim()}>Assign</button>
              <button type="button" onClick={exportSelected}>Export</button>
            </div>
          </details>
          <span className="pm-count">{filteredProducts.length}/{products.length}</span>
        </section>

        {exportText ? <textarea className="pm-export pm-glass" readOnly value={exportText} aria-label="Exported products" /> : null}

        <section className="pm-grid" aria-label="Products">
          {filteredProducts.map((product, index) => (
            <article className="pm-card pm-glass" key={`${product.productId}-${index}`} onClick={() => { setSlideOver({ kind: 'detail', productId: product.productId }); setActiveTab('General'); }}>
              <div className="pm-card-top">
                <input type="checkbox" aria-label={`Select ${product.productCode}`} checked={selectedIds.has(product.productId)} onClick={(event) => event.stopPropagation()} onChange={() => toggleSelected(product.productId)} />
                <span className={`pm-status pm-status--${String(product.status).toLowerCase()}`}>{product.status}</span>
              </div>
              <div className="pm-card-code">{product.productCode}</div>
              <input className="pm-inline-title" value={product.productName} aria-label={`${product.productCode} name`} onClick={(event) => event.stopPropagation()} onChange={(event) => patchProduct(product.productId, { productName: event.target.value })} />
              <div className="pm-card-meta"><span>{product.investorCode}</span><span>{product.channelCode}</span><span>{product.productType}</span></div>
              <div className="pm-rate">{rateRange(product)}</div>
              <div className="pm-quick-row" onClick={(event) => event.stopPropagation()}>
                <select value={product.status} aria-label={`${product.productCode} status`} onChange={(event) => patchProduct(product.productId, { status: event.target.value })}>
                  {lifecycleOptions.map((status, statusIndex) => <option key={`${status}-${statusIndex}`} value={status}>{status}</option>)}
                </select>
                <input value={product.investorCode} aria-label={`${product.productCode} investor`} onChange={(event) => patchProduct(product.productId, { investorCode: event.target.value })} />
              </div>
            </article>
          ))}
        </section>
      </main>

      {slideOver?.kind === 'add' ? <AddProductPanel onClose={() => setSlideOver(null)} onSubmit={createProduct} pricingRuleSets={pricingRuleSets} /> : null}
      {activeProduct ? <ProductDetailPanel product={activeProduct} activeTab={activeTab} tabs={detailTabs} stipulations={stipulations} mappings={mappings} onTab={setActiveTab} onClose={() => setSlideOver(null)} onPatch={(patch) => patchProduct(activeProduct.productId, patch)} /> : null}
    </section>
  );
}

function FilterSelect({ collapsed, label, value, options, onChange }: { collapsed: boolean; label: string; value: string; options: string[]; onChange: (value: string) => void }) {
  return (
    <label className="pm-filter">
      <span>{collapsed ? label.slice(0, 1) : label}</span>
      {!collapsed ? <select value={value} onChange={(event) => onChange(event.target.value)}>{options.map((option) => <option key={option} value={option}>{option}</option>)}</select> : null}
    </label>
  );
}

function AddProductPanel({ onClose, onSubmit, pricingRuleSets }: { onClose: () => void; onSubmit: (event: FormEvent<HTMLFormElement>) => void; pricingRuleSets: string[] }) {
  return (
    <div className="pm-scrim" role="presentation">
      <aside className="pm-panel pm-glass" role="dialog" aria-modal="true" aria-labelledby="add-product-title">
        <div className="pm-panel-head"><h2 id="add-product-title">Add Product</h2><button type="button" onClick={onClose}>×</button></div>
        <form className="pm-form" onSubmit={onSubmit}>
          <Field name="productCode" label="Code" required />
          <Field name="productName" label="Name" required />
          <Field name="investorCode" label="Investor" />
          <Field name="channelCode" label="Channel" />
          <Field name="productType" label="Product Type" />
          <Field name="mortgageType" label="Mortgage Type" />
          <label>Status<select name="status"><option>DRAFT</option><option>ACTIVE</option><option>DISABLED</option><option>REVIEW</option><option>PUBLISHED</option></select></label>
          <label>Description<textarea name="description" /></label>
          <Field name="rateMin" label="Rate Min" inputMode="decimal" />
          <Field name="rateMax" label="Rate Max" inputMode="decimal" />
          <Field name="minLoanAmount" label="Loan Min" inputMode="decimal" />
          <Field name="maxLoanAmount" label="Loan Max" inputMode="decimal" />
          <Field name="minFico" label="FICO Min" inputMode="numeric" />
          <Field name="maxLtv" label="LTV Max" inputMode="decimal" />
          <Field name="maxDti" label="DTI Max" inputMode="decimal" />
          <Field name="states" label="States" />
          <Field name="counties" label="Counties" />
          <Field name="propertyTypes" label="Property" />
          <Field name="occupancyTypes" label="Occupancy" />
          <Field name="loanPurposes" label="Purpose" />
          <Field name="termMonths" label="Term" inputMode="numeric" />
          <Field name="amortizationType" label="Amortization" />
          <Field name="lienPosition" label="Lien" />
          <Field name="documentationType" label="Doc Type" />
          <Field name="incomeType" label="Income" />
          <Field name="lockDays" label="Lock Days" inputMode="numeric" />
          <Field name="effectiveFrom" label="Effective From" type="date" />
          <Field name="effectiveTo" label="Effective To" type="date" />
          <label>Pricing Set<select name="pricingRuleSet"><option value="">—</option>{pricingRuleSets.map((ruleSet) => <option key={ruleSet} value={ruleSet}>{ruleSet}</option>)}</select></label>
          <label>Eligibility JSON<textarea name="eligibilityJson" /></label>
          <Field name="stipulationRefs" label="Stipulations" />
          <Field name="adjustmentRefs" label="Adjustments" />
          <div className="pm-form-actions"><button type="submit" className="pm-primary">Save</button><button type="button" onClick={onClose}>Cancel</button></div>
        </form>
      </aside>
    </div>
  );
}

function ProductDetailPanel({ product, activeTab, tabs, stipulations, mappings, onTab, onClose, onPatch }: { product: ManagedProduct; activeTab: ProductDetailTab; tabs: ProductDetailTab[]; stipulations: ProductAdminStipulation[]; mappings: ProductAdminMapping[]; onTab: (tab: ProductDetailTab) => void; onClose: () => void; onPatch: (patch: Partial<ManagedProduct>) => void }) {
  const productStipulations = mappedStipulations(product.productId, stipulations, mappings, product.stipulationRefs);
  return (
    <div className="pm-scrim" role="presentation">
      <aside className="pm-panel pm-panel--detail pm-glass" role="dialog" aria-modal="true" aria-labelledby="product-detail-title">
        <div className="pm-panel-head"><div><p className="pm-kicker">{product.productCode}</p><h2 id="product-detail-title">{product.productName}</h2></div><button type="button" onClick={onClose}>×</button></div>
        <nav className="pm-tabs" aria-label="Product detail tabs">{tabs.map((tab) => <button key={tab} type="button" aria-pressed={activeTab === tab} onClick={() => onTab(tab)}>{tab}</button>)}</nav>
        {activeTab === 'General' ? (
          <div className="pm-detail-grid">
            <FieldValue label="Code" value={product.productCode} onChange={(value) => onPatch({ productCode: value.toUpperCase() })} />
            <FieldValue label="Name" value={product.productName} onChange={(value) => onPatch({ productName: value })} />
            <FieldValue label="Investor" value={product.investorCode} onChange={(value) => onPatch({ investorCode: value })} />
            <FieldValue label="Channel" value={product.channelCode} onChange={(value) => onPatch({ channelCode: value })} />
            <FieldValue label="Product Type" value={product.productType} onChange={(value) => onPatch({ productType: value })} />
            <FieldValue label="Mortgage Type" value={product.mortgageType} onChange={(value) => onPatch({ mortgageType: value })} />
          </div>
        ) : null}
        {activeTab === 'Pricing' ? (
          <div className="pm-detail-grid">
            <FieldValue label="Rate Min" value={product.rateMin} onChange={(value) => onPatch({ rateMin: value })} />
            <FieldValue label="Rate Max" value={product.rateMax} onChange={(value) => onPatch({ rateMax: value })} />
            <FieldValue label="Pricing Set" value={product.pricingRuleSet ?? ''} onChange={(value) => onPatch({ pricingRuleSet: value })} />
            <FieldValue label="Lock Days" value={product.lockDays} onChange={(value) => onPatch({ lockDays: value })} />
            <FieldValue label="Term" value={product.termMonths} onChange={(value) => onPatch({ termMonths: value })} />
            <FieldValue label="Amortization" value={product.amortizationType} onChange={(value) => onPatch({ amortizationType: value })} />
          </div>
        ) : null}
        {activeTab === 'Eligibility' ? (
          <div className="pm-stack">
            <div className="pm-detail-grid">
              <FieldValue label="Loan Min" value={String(product.minLoanAmount ?? '')} onChange={(value) => onPatch({ minLoanAmount: numberOrNull(value) })} />
              <FieldValue label="Loan Max" value={String(product.maxLoanAmount ?? '')} onChange={(value) => onPatch({ maxLoanAmount: numberOrNull(value) })} />
              <FieldValue label="FICO" value={String(product.minFico ?? '')} onChange={(value) => onPatch({ minFico: numberOrNull(value) })} />
              <FieldValue label="LTV" value={String(product.maxLtv ?? '')} onChange={(value) => onPatch({ maxLtv: numberOrNull(value) })} />
              <FieldValue label="DTI" value={String(product.maxDti ?? '')} onChange={(value) => onPatch({ maxDti: numberOrNull(value) })} />
              <FieldValue label="Lien" value={product.lienPosition} onChange={(value) => onPatch({ lienPosition: value })} />
            </div>
            <ChipRow label="States" values={product.states} />
            <ChipRow label="Counties" values={product.counties} />
            <ChipRow label="Property" values={product.propertyTypes} />
            <ChipRow label="Occupancy" values={product.occupancyTypes} />
            <ChipRow label="Purpose" values={product.loanPurposes} />
            <textarea className="pm-json" value={product.eligibilityJson} onChange={(event) => onPatch({ eligibilityJson: event.target.value })} aria-label="Eligibility JSON" />
          </div>
        ) : null}
        {activeTab === 'Stipulations' ? <ChipRow label="Stipulations" values={productStipulations} /> : null}
        {activeTab === 'Adjustments' ? <ChipRow label="Adjustments" values={product.adjustmentRefs} /> : null}
      </aside>
    </div>
  );
}

function Field({ label, name, required = false, inputMode, type = 'text' }: { label: string; name: string; required?: boolean; inputMode?: 'decimal' | 'numeric'; type?: string }) {
  return <label>{label}<input name={name} required={required} inputMode={inputMode} type={type} /></label>;
}

function FieldValue({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
  return <label>{label}<input value={value} onChange={(event: ChangeEvent<HTMLInputElement>) => onChange(event.target.value)} /></label>;
}

function ChipRow({ label, values }: { label: string; values: string[] }) {
  return <div className="pm-chip-row"><span>{label}</span><div>{(values.length ? values : ['—']).map((value, index) => <b key={`${value}-${index}`}>{value}</b>)}</div></div>;
}

function toManagedProduct(product: ProductAdminProduct): ManagedProduct {
  return {
    ...product,
    mortgageType: product.productType || 'Unassigned',
    rateMin: '',
    rateMax: '',
    states: [],
    counties: [],
    termMonths: '',
    amortizationType: '',
    lienPosition: '',
    documentationType: '',
    incomeType: '',
    lockDays: '',
    effectiveFrom: '',
    effectiveTo: '',
    eligibilityJson: '',
    stipulationRefs: [],
    adjustmentRefs: [],
  };
}

function matchesFilters(product: ManagedProduct, filters: ProductFilters) {
  return (filters.investor === 'All' || product.investorCode === filters.investor)
    && (filters.channel === 'All' || product.channelCode === filters.channel)
    && (filters.productType === 'All' || product.productType === filters.productType)
    && (filters.status === 'All' || product.status === filters.status)
    && (filters.mortgageType === 'All' || product.mortgageType === filters.mortgageType);
}

function selectOptions(values: string[]) {
  return ['All', ...Array.from(new Set(values.filter(Boolean))).sort((a, b) => a.localeCompare(b))];
}

function splitValues(value: string) {
  return value.split(',').map((item) => item.trim()).filter(Boolean);
}

function fieldValue(data: FormData, field: string) {
  return String(data.get(field) ?? '').trim();
}

function optionalNumber(data: FormData, field: string) {
  return numberOrNull(fieldValue(data, field));
}

function numberOrNull(value: string) {
  if (!value.trim()) return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function rateRange(product: ManagedProduct) {
  if (!product.rateMin && !product.rateMax) return 'Rate —';
  return `${product.rateMin || '—'} - ${product.rateMax || '—'}`;
}

function mappedStipulations(productId: string, stipulations: ProductAdminStipulation[], mappings: ProductAdminMapping[], localRefs: string[]) {
  const mappedIds = new Set(mappings.filter((mapping) => mapping.productId === productId).map((mapping) => mapping.stipulationId));
  return [...stipulations.filter((stipulation) => mappedIds.has(stipulation.stipulationId)).map((stipulation) => stipulation.stipulationCode), ...localRefs];
}

function toExportProduct(product: ManagedProduct) {
  const { description, productId, version, changeSummary, ...rest } = product;
  return { productId, version, changeSummary, description, ...rest };
}

const productManagementStyles = `
.pm-shell { width: 100%; min-height: 100vh; display: flex; gap: 18px; padding: 18px; box-sizing: border-box; color: #eef6ff; background: radial-gradient(circle at top left, rgba(93, 214, 255, .28), transparent 32rem), radial-gradient(circle at 80% 20%, rgba(168, 85, 247, .24), transparent 28rem), linear-gradient(135deg, #07111f 0%, #101827 48%, #0c1220 100%); }
.pm-shell * { box-sizing: border-box; }
.pm-glass { background: linear-gradient(145deg, rgba(255, 255, 255, .15), rgba(255, 255, 255, .055)); border: 1px solid rgba(255, 255, 255, .18); box-shadow: 0 24px 70px rgba(0, 0, 0, .26), inset 0 1px 0 rgba(255, 255, 255, .16); backdrop-filter: blur(18px); }
.pm-shell--loading { align-items: center; justify-content: center; }
.pm-loader { padding: 22px 34px; border-radius: 24px; }
.pm-sidebar { width: 286px; flex: 0 0 286px; border-radius: 28px; padding: 16px; position: sticky; top: 18px; height: calc(100vh - 36px); transition: width .18s ease, flex-basis .18s ease; overflow: hidden; }
.pm-sidebar--collapsed { width: 72px; flex-basis: 72px; }
.pm-icon-button { width: 38px; height: 38px; border-radius: 999px; float: right; }
.pm-filter-title { clear: both; font-size: 12px; letter-spacing: .18em; text-transform: uppercase; color: #9ed8ff; margin: 48px 0 14px; }
.pm-filter { display: grid; gap: 7px; margin: 0 0 14px; color: #cde7ff; font-size: 12px; text-transform: uppercase; letter-spacing: .08em; }
.pm-filter select, .pm-shell input, .pm-shell select, .pm-shell textarea { width: 100%; border: 1px solid rgba(255,255,255,.16); border-radius: 14px; background: rgba(3, 10, 22, .56); color: #f8fbff; padding: 10px 11px; outline: none; }
.pm-main { min-width: 0; flex: 1; display: grid; gap: 14px; align-content: start; }
.pm-toolbar, .pm-bulk { border-radius: 26px; padding: 14px 16px; display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.pm-toolbar h1 { margin: 0; font-size: clamp(24px, 2.2vw, 38px); letter-spacing: -.04em; }
.pm-kicker { margin: 0 0 4px; color: #8fd7ff; text-transform: uppercase; letter-spacing: .16em; font-size: 11px; }
.pm-actions, .pm-bulk { flex-wrap: wrap; }
.pm-bulk { justify-content: flex-start; }
.pm-bulk-details { min-width: min(100%, 560px); }
.pm-bulk-details summary { cursor: pointer; font-weight: 900; color: #b8f3ff; }
.pm-bulk-details__controls { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; margin-top: 10px; }
.pm-bulk input { max-width: 180px; }
.pm-count { margin-left: auto; color: #b7d7ef; font-weight: 700; }
.pm-checkline { display: inline-flex; gap: 8px; align-items: center; min-width: 72px; }
.pm-checkline input, .pm-card-top input { width: auto; }
.pm-primary, .pm-secondary, .pm-shell button { border: 0; border-radius: 999px; color: #f8fbff; background: rgba(255, 255, 255, .14); padding: 10px 14px; cursor: pointer; font-weight: 800; }
.pm-primary { background: linear-gradient(135deg, #27d5ff, #8b5cf6); color: #06101d; }
.pm-secondary { width: 100%; color: #cde7ff; }
.pm-shell button:disabled { opacity: .42; cursor: not-allowed; }
.pm-pill { display: inline-flex; margin-top: 5px; border-radius: 999px; padding: 4px 9px; font-size: 11px; background: rgba(255,255,255,.12); }
.pm-pill--warn { color: #ffe0a3; }
.pm-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(245px, 1fr)); gap: 14px; }
.pm-card { min-height: 206px; border-radius: 26px; padding: 16px; cursor: pointer; display: grid; gap: 10px; align-content: start; transition: transform .16s ease, border-color .16s ease; }
.pm-card:hover { transform: translateY(-2px); border-color: rgba(88, 218, 255, .48); }
.pm-card-top, .pm-card-meta, .pm-quick-row { display: flex; align-items: center; gap: 8px; }
.pm-card-top { justify-content: space-between; }
.pm-status { border-radius: 999px; padding: 4px 9px; background: rgba(148, 163, 184, .18); color: #dbeafe; font-size: 11px; font-weight: 900; }
.pm-status--active, .pm-status--published { background: rgba(52, 211, 153, .2); color: #9ff7ce; }
.pm-status--disabled, .pm-status--deprecated { background: rgba(248, 113, 113, .2); color: #ffb4b4; }
.pm-card-code { font: 900 24px/1.1 ui-monospace, SFMono-Regular, Menlo, monospace; letter-spacing: -.03em; }
.pm-inline-title { font-size: 15px; font-weight: 800; }
.pm-card-meta { flex-wrap: wrap; color: #bad7ea; font-size: 12px; }
.pm-card-meta span { border-radius: 999px; padding: 4px 8px; background: rgba(255,255,255,.08); }
.pm-rate { font-size: 21px; font-weight: 900; color: #b8f3ff; }
.pm-quick-row select { flex: 1; min-width: 110px; }
.pm-quick-row input { flex: 1; min-width: 90px; }
.pm-export { min-height: 108px; border-radius: 22px; resize: vertical; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }
.pm-scrim { position: fixed; inset: 0; z-index: 30; display: flex; justify-content: flex-end; background: rgba(2, 6, 23, .48); }
.pm-panel { width: min(760px, 96vw); height: 100vh; overflow: auto; border-radius: 30px 0 0 30px; padding: 18px; }
.pm-panel--detail { width: min(860px, 96vw); }
.pm-panel-head { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; margin-bottom: 12px; }
.pm-panel-head h2 { margin: 0; font-size: 28px; letter-spacing: -.04em; }
.pm-form, .pm-detail-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.pm-form label, .pm-detail-grid label { display: grid; gap: 6px; color: #b9d8ea; font-size: 12px; font-weight: 800; text-transform: uppercase; letter-spacing: .07em; }
.pm-form textarea, .pm-json { min-height: 84px; resize: vertical; }
.pm-form label:has(textarea), .pm-form-actions, .pm-stack, .pm-json { grid-column: 1 / -1; }
.pm-form-actions { display: flex; gap: 10px; justify-content: flex-end; margin-top: 4px; }
.pm-tabs { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 14px; }
.pm-tabs button[aria-pressed='true'] { background: rgba(39, 213, 255, .22); color: #b8f3ff; }
.pm-stack { display: grid; gap: 12px; }
.pm-chip-row { display: grid; gap: 8px; }
.pm-chip-row > span { color: #9ed8ff; font-size: 12px; font-weight: 900; text-transform: uppercase; letter-spacing: .12em; }
.pm-chip-row > div { display: flex; gap: 8px; flex-wrap: wrap; }
.pm-chip-row b { border-radius: 999px; padding: 7px 10px; background: rgba(255,255,255,.1); color: #eef6ff; }
@media (max-width: 880px) { .pm-shell { padding: 10px; gap: 10px; } .pm-sidebar { position: fixed; z-index: 20; left: 10px; top: 10px; height: calc(100vh - 20px); } .pm-sidebar--collapsed { position: sticky; } .pm-toolbar { margin-left: 82px; } .pm-form, .pm-detail-grid { grid-template-columns: 1fr; } }
`;

export default ProductManagementScreen;
