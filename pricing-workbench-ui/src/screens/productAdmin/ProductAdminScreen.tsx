import { useEffect, useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import { fetchProductAdmin, type ProductAdminMapping, type ProductAdminProduct, type ProductAdminStipulation, type ProductAdminView } from '../../lib/api/products';
import { displayChannelLabel } from '../../lib/utils/channelDisplay';
import type { ScreenProps, ScreenVisualState } from '../contract/ScreenProps';

type ProductAdminScreenProps = Partial<ScreenProps> & {
  fetchImpl?: typeof fetch;
  tenantContext?: string;
};

type ProductAdminState =
  | { kind: 'loading' }
  | { kind: 'ready'; view: ProductAdminView; source: 'backend' | 'local-preview' }
  | { kind: 'blocked'; message: string; view: ProductAdminView };

type ActiveTab = 'products' | 'stipulations' | 'mappings';
type ModalKind = 'product' | 'stipulation' | null;

export const productAdminEvidenceTarget = '.local-harness/evidence/PII-53-S01/product-admin.json';
export const productAdminStateCoverage: ScreenVisualState[] = ['loading', 'empty', 'ready', 'blocked'];

const lifecycleOrder = ['DRAFT', 'REVIEW', 'PUBLISHED', 'DEPRECATED'];

const localPreviewView: ProductAdminView = {
  tenantContext: 'ui-preview-tenant',
  dependencyStatus: 'ADMIN_PRODUCT_APIS_UNAVAILABLE',
  uiTraceId: 'product-admin-local-trace',
  fallbackReason: 'Catalog-service admin product and stipulation APIs are not connected in this local UI lane; records below are labeled setup placeholders and do not define pricing policy.',
  lifecycle: lifecycleOrder,
  pricingRuleSets: ['Backend supplied rule set reference required'],
  products: [
    {
      productId: 'product-setup-required',
      productCode: 'PRODUCT_SETUP_REQUIRED',
      productName: 'Product definition setup required',
      productType: 'Backend supplied product type required',
      investorCode: 'Backend supplied investor reference required',
      channelCode: 'Backend supplied channel reference required',
      status: 'DRAFT',
      description: 'Local preview row used only to keep the administration workflow visible while catalog-service write APIs are unavailable.',
      minLoanAmount: null,
      maxLoanAmount: null,
      minFico: null,
      maxLtv: null,
      maxDti: null,
      propertyTypes: [],
      occupancyTypes: [],
      loanPurposes: [],
      pricingRuleSet: 'Backend supplied rule set reference required',
      version: 1,
      changeSummary: 'Awaiting backend product-admin contract.',
    },
  ],
  stipulations: [
    {
      stipulationId: 'stipulation-setup-required',
      stipulationCode: 'STIP_SETUP_REQUIRED',
      stipulationName: 'Stipulation setup required',
      category: 'PROGRAM',
      severity: 'REQUIRED',
      description: 'Local preview row only; backend stipulation library owns real conditions and validation rules.',
      validationRule: null,
      appliesToProductTypes: [],
    },
  ],
  mappings: [
    {
      productId: 'product-setup-required',
      stipulationId: 'stipulation-setup-required',
      isRequired: true,
      conditionExpression: null,
      displayOrder: 1,
    },
  ],
};

function uniqueCodeExists<T extends { productCode?: string; stipulationCode?: string }>(records: T[], code: string) {
  const normalized = code.trim().toUpperCase();
  return records.some((record) => (record.productCode ?? record.stipulationCode ?? '').trim().toUpperCase() === normalized);
}

function nextStatus(current: string) {
  const index = lifecycleOrder.indexOf(current);
  return lifecycleOrder[Math.min(index + 1, lifecycleOrder.length - 1)] ?? 'DRAFT';
}

function splitValues(value: string) {
  return value.split(',').map((item) => item.trim()).filter(Boolean);
}

function optionalNumber(data: FormData, field: string) {
  const raw = String(data.get(field) ?? '').trim();
  if (!raw) return null;
  const parsed = Number(raw);
  if (!Number.isFinite(parsed)) throw new Error(`${field} must be numeric when supplied.`);
  return parsed;
}

function jsonRule(value: string): Record<string, unknown> | null {
  const trimmed = value.trim();
  if (!trimmed) return null;
  const parsed = JSON.parse(trimmed) as unknown;
  if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) throw new Error('Validation rule must be a JSON object.');
  return parsed as Record<string, unknown>;
}

export function ProductAdminScreen({ fetchImpl = fetch, tenantContext = 'ui-preview-tenant' }: ProductAdminScreenProps) {
  const [state, setState] = useState<ProductAdminState>({ kind: 'loading' });
  const [activeTab, setActiveTab] = useState<ActiveTab>('products');
  const [modal, setModal] = useState<ModalKind>(null);
  const [draftError, setDraftError] = useState('');

  useEffect(() => {
    let active = true;
    fetchProductAdmin(fetchImpl)
      .then((view) => {
        if (active) setState({ kind: 'ready', view, source: 'backend' });
      })
      .catch((error: unknown) => {
        if (!active) return;
        const message = error instanceof Error ? error.message : localPreviewView.fallbackReason;
        setState({ kind: 'blocked', message, view: { ...localPreviewView, tenantContext } });
      });
    return () => {
      active = false;
    };
  }, [fetchImpl, tenantContext]);

  const view = state.kind === 'loading' ? null : state.view;
  const products = view?.products ?? [];
  const stipulations = view?.stipulations ?? [];
  const mappings = view?.mappings ?? [];

  const mappingLookup = useMemo(() => new Set(mappings.map((mapping) => `${mapping.productId}:${mapping.stipulationId}`)), [mappings]);

  function updateView(updater: (view: ProductAdminView) => ProductAdminView) {
    setState((current) => {
      if (current.kind === 'loading') return current;
      const nextView = updater(current.view);
      return current.kind === 'blocked' ? { ...current, view: nextView } : { ...current, view: nextView };
    });
  }

  function advanceProductStatus(productId: string) {
    updateView((current) => ({
      ...current,
      products: current.products.map((product) => product.productId === productId ? { ...product, status: nextStatus(product.status), version: product.version + 1, changeSummary: 'Local lifecycle preview updated; backend version history owns persisted history.' } : product),
    }));
  }

  function previewEditProduct(productId: string) {
    updateView((current) => ({
      ...current,
      products: current.products.map((product) => product.productId === productId ? { ...product, version: product.version + 1, changeSummary: 'Local update preview recorded; catalog-service update API owns persisted edits.' } : product),
    }));
  }

  function previewDeleteProduct(productId: string) {
    updateView((current) => ({
      ...current,
      products: current.products.filter((product) => product.productId !== productId),
      mappings: current.mappings.filter((mapping) => mapping.productId !== productId),
    }));
  }

  function previewEditStipulation(stipulationId: string) {
    updateView((current) => ({
      ...current,
      stipulations: current.stipulations.map((stipulation) => stipulation.stipulationId === stipulationId ? { ...stipulation, description: stipulation.description || 'Local update preview recorded; catalog-service stipulation update API owns persisted edits.' } : stipulation),
    }));
  }

  function previewDeleteStipulation(stipulationId: string) {
    updateView((current) => ({
      ...current,
      stipulations: current.stipulations.filter((stipulation) => stipulation.stipulationId !== stipulationId),
      mappings: current.mappings.filter((mapping) => mapping.stipulationId !== stipulationId),
    }));
  }

  function toggleMapping(productId: string, stipulationId: string) {
    updateView((current) => {
      const exists = current.mappings.some((mapping) => mapping.productId === productId && mapping.stipulationId === stipulationId);
      return {
        ...current,
        mappings: exists
          ? current.mappings.filter((mapping) => !(mapping.productId === productId && mapping.stipulationId === stipulationId))
          : [...current.mappings, { productId, stipulationId, isRequired: true, conditionExpression: null, displayOrder: current.mappings.length + 1 }],
      };
    });
  }

  function createProduct(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const productCode = String(data.get('productCode') ?? '').trim().toUpperCase();
    const productName = String(data.get('productName') ?? '').trim();
    if (!productCode || !productName) {
      setDraftError('Product code and name are required.');
      return;
    }
    if (uniqueCodeExists(products, productCode)) {
      setDraftError('Product code must be unique in the current admin view.');
      return;
    }
    try {
      const product: ProductAdminProduct = {
        productId: `local-product-${productCode.toLowerCase()}`,
        productCode,
        productName,
        productType: String(data.get('productType') ?? '').trim() || 'Backend supplied product type required',
        investorCode: String(data.get('investorCode') ?? '').trim() || 'Backend supplied investor reference required',
        channelCode: String(data.get('channelCode') ?? '').trim() || 'Backend supplied channel reference required',
        status: 'DRAFT',
        description: String(data.get('description') ?? '').trim(),
        minLoanAmount: optionalNumber(data, 'minLoanAmount'),
        maxLoanAmount: optionalNumber(data, 'maxLoanAmount'),
        minFico: optionalNumber(data, 'minFico'),
        maxLtv: optionalNumber(data, 'maxLtv'),
        maxDti: optionalNumber(data, 'maxDti'),
        propertyTypes: splitValues(String(data.get('propertyTypes') ?? '')),
        occupancyTypes: splitValues(String(data.get('occupancyTypes') ?? '')),
        loanPurposes: splitValues(String(data.get('loanPurposes') ?? '')),
        pricingRuleSet: String(data.get('pricingRuleSet') ?? '').trim() || 'Backend supplied rule set reference required',
        version: 1,
        changeSummary: 'Created in local admin preview; backend persistence pending.',
      };
      updateView((current) => ({ ...current, products: [...current.products, product] }));
      setDraftError('');
      setModal(null);
    } catch (error) {
      setDraftError(error instanceof Error ? error.message : 'Constraint values must be numeric when supplied.');
    }
  }

  function createStipulation(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const stipulationCode = String(data.get('stipulationCode') ?? '').trim().toUpperCase();
    const stipulationName = String(data.get('stipulationName') ?? '').trim();
    if (!stipulationCode || !stipulationName) {
      setDraftError('Stipulation code and name are required.');
      return;
    }
    if (uniqueCodeExists(stipulations, stipulationCode)) {
      setDraftError('Stipulation code must be unique in the current admin view.');
      return;
    }
    try {
      const stipulation: ProductAdminStipulation = {
        stipulationId: `local-stipulation-${stipulationCode.toLowerCase()}`,
        stipulationCode,
        stipulationName,
        category: String(data.get('category') ?? '').trim() || 'PROGRAM',
        severity: String(data.get('severity') ?? '').trim() || 'REQUIRED',
        description: String(data.get('description') ?? '').trim(),
        validationRule: jsonRule(String(data.get('validationRule') ?? '')),
        appliesToProductTypes: splitValues(String(data.get('appliesToProductTypes') ?? '')),
      };
      updateView((current) => ({ ...current, stipulations: [...current.stipulations, stipulation] }));
      setDraftError('');
      setModal(null);
    } catch (error) {
      setDraftError(error instanceof Error ? error.message : 'Validation rule must be valid JSON.');
    }
  }

  if (state.kind === 'loading') {
    return (
      <section className="panel" aria-labelledby="product-admin-title">
        <h2 id="product-admin-title">Product Administration</h2>
        <p role="status">Loading product administration workspace...</p>
      </section>
    );
  }

  return (
    <div className="product-admin-shell" aria-labelledby="product-admin-title">
      <section className="hero" aria-labelledby="product-admin-title">
        <p className="eyebrow">Products and stipulations · PII-53-S01</p>
        <h2 id="product-admin-title">Product Administration</h2>
        <p>
          Manage product definitions, stipulation library records, many-to-many mappings, lifecycle state, and pricing-rule references.
          The browser keeps pricing, eligibility, and investor policy as backend-owned references only.
        </p>
        <dl className="status-grid">
          <dt>Workspace</dt><dd>{view?.tenantContext}</dd>
          <dt>Setup status</dt><dd>{view?.dependencyStatus}</dd>
          <dt>Support reference</dt><dd><code>{view?.uiTraceId}</code></dd>
          <dt>Evidence target</dt><dd><code>{productAdminEvidenceTarget}</code></dd>
        </dl>
      </section>

      {state.kind === 'blocked' ? (
        <div className="banner banner--blocked" role="alert">
          <strong>Product admin APIs need setup</strong>
          <span>{state.message}</span>
          <span>{view?.fallbackReason}</span>
        </div>
      ) : null}

      <nav className="module-rail__nav" aria-label="Product administration tabs">
        {(['products', 'stipulations', 'mappings'] as ActiveTab[]).map((tab) => (
          <button key={tab} type="button" aria-pressed={activeTab === tab} onClick={() => setActiveTab(tab)}>{tab[0].toUpperCase() + tab.slice(1)}</button>
        ))}
      </nav>

      {activeTab === 'products' ? <ProductsTab products={products} onCreate={() => { setDraftError(''); setModal('product'); }} onAdvance={advanceProductStatus} onPreviewEdit={previewEditProduct} onPreviewDelete={previewDeleteProduct} /> : null}
      {activeTab === 'stipulations' ? <StipulationsTab stipulations={stipulations} onCreate={() => { setDraftError(''); setModal('stipulation'); }} onPreviewEdit={previewEditStipulation} onPreviewDelete={previewDeleteStipulation} /> : null}
      {activeTab === 'mappings' ? <MappingsTab products={products} stipulations={stipulations} mappings={mappings} mappingLookup={mappingLookup} onToggle={toggleMapping} /> : null}

      <section className="panel" aria-labelledby="version-history-heading">
        <h2 id="version-history-heading">Version history and pricing-rule references</h2>
        <p>Version history is represented by product version and change summary until catalog-service returns persisted snapshots.</p>
        <ul className="chip-list" aria-label="Pricing rule set references">
          {(view?.pricingRuleSets ?? []).map((ruleSet) => <li key={ruleSet}>{ruleSet}</li>)}
        </ul>
        <ol aria-label="Product lifecycle order">
          {(view?.lifecycle ?? lifecycleOrder).map((status) => <li key={status}>{status}</li>)}
        </ol>
      </section>

      {modal === 'product' ? <ProductFormModal error={draftError} onClose={() => setModal(null)} onSubmit={createProduct} pricingRuleSets={view?.pricingRuleSets ?? []} /> : null}
      {modal === 'stipulation' ? <StipulationFormModal error={draftError} onClose={() => setModal(null)} onSubmit={createStipulation} /> : null}
    </div>
  );
}

function ProductsTab({ products, onCreate, onAdvance, onPreviewEdit, onPreviewDelete }: { products: ProductAdminProduct[]; onCreate: () => void; onAdvance: (productId: string) => void; onPreviewEdit: (productId: string) => void; onPreviewDelete: (productId: string) => void }) {
  return (
    <section className="panel" aria-labelledby="products-tab-heading">
      <div className="panel-heading-row">
        <div><p className="eyebrow">Products</p><h2 id="products-tab-heading">Product lifecycle CRUD</h2></div>
        <button type="button" onClick={onCreate}>Create Product</button>
      </div>
      {products.length === 0 ? <div className="banner banner--info" role="status">No product definitions are available.</div> : null}
      <div className="quote-table" role="table" aria-label="Product administration table">
        <div role="row" className="quote-table__row quote-table__row--head"><span role="columnheader">Product</span><span role="columnheader">Attributes</span><span role="columnheader">Constraints</span><span role="columnheader">Lifecycle</span><span role="columnheader">Actions</span></div>
        {products.map((product) => (
          <div role="row" className="quote-table__row" key={product.productId}>
            <span role="cell"><strong>{product.productName}</strong><br /><code>{product.productCode}</code><br />{product.description}</span>
            <span role="cell">Type: {product.productType}<br />Investor: {product.investorCode}<br />Channel: {displayChannelLabel(product.channelCode)}</span>
            <span role="cell">Loan amount: {rangeText(product.minLoanAmount, product.maxLoanAmount)}<br />FICO/LTV/DTI: {constraintText(product)}<ChipList label={`${product.productCode} eligibility refs`} values={[...product.propertyTypes, ...product.occupancyTypes, ...product.loanPurposes]} /></span>
            <span role="cell"><strong>{product.status}</strong><br />Version {product.version}<br />{product.changeSummary}<br /><button type="button" onClick={() => onAdvance(product.productId)} disabled={product.status === 'DEPRECATED'}>Advance status</button></span>
            <span role="cell"><button type="button" aria-label={`Edit product preview for ${product.productCode}`} onClick={() => onPreviewEdit(product.productId)}>Edit preview</button><button type="button" aria-label={`Delete product preview for ${product.productCode}`} onClick={() => onPreviewDelete(product.productId)}>Delete preview</button></span>
          </div>
        ))}
      </div>
    </section>
  );
}

function StipulationsTab({ stipulations, onCreate, onPreviewEdit, onPreviewDelete }: { stipulations: ProductAdminStipulation[]; onCreate: () => void; onPreviewEdit: (stipulationId: string) => void; onPreviewDelete: (stipulationId: string) => void }) {
  return (
    <section className="panel" aria-labelledby="stipulations-tab-heading">
      <div className="panel-heading-row">
        <div><p className="eyebrow">Stipulations</p><h2 id="stipulations-tab-heading">Stipulation library</h2></div>
        <button type="button" onClick={onCreate}>Create Stipulation</button>
      </div>
      <div className="quote-table" role="table" aria-label="Stipulation library table">
        <div role="row" className="quote-table__row quote-table__row--head"><span role="columnheader">Stipulation</span><span role="columnheader">Category</span><span role="columnheader">Validation rule</span><span role="columnheader">Applies to</span><span role="columnheader">Actions</span></div>
        {stipulations.map((stipulation) => (
          <div role="row" className="quote-table__row" key={stipulation.stipulationId}>
            <span role="cell"><strong>{stipulation.stipulationName}</strong><br /><code>{stipulation.stipulationCode}</code><br />{stipulation.description}</span>
            <span role="cell">{stipulation.category}<br />{stipulation.severity}</span>
            <span role="cell"><code>{stipulation.validationRule ? JSON.stringify(stipulation.validationRule) : 'Backend validation rule required'}</code></span>
            <span role="cell"><ChipList label={`${stipulation.stipulationCode} product types`} values={stipulation.appliesToProductTypes} /></span>
            <span role="cell"><button type="button" aria-label={`Edit stipulation preview for ${stipulation.stipulationCode}`} onClick={() => onPreviewEdit(stipulation.stipulationId)}>Edit preview</button><button type="button" aria-label={`Delete stipulation preview for ${stipulation.stipulationCode}`} onClick={() => onPreviewDelete(stipulation.stipulationId)}>Delete preview</button></span>
          </div>
        ))}
      </div>
    </section>
  );
}

function MappingsTab({ products, stipulations, mappings, mappingLookup, onToggle }: { products: ProductAdminProduct[]; stipulations: ProductAdminStipulation[]; mappings: ProductAdminMapping[]; mappingLookup: Set<string>; onToggle: (productId: string, stipulationId: string) => void }) {
  return (
    <section className="panel" aria-labelledby="mappings-tab-heading">
      <div className="panel-heading-row"><div><p className="eyebrow">Mappings</p><h2 id="mappings-tab-heading">Product-stipulation mapping matrix</h2></div></div>
      <p className="field-help">Checkboxes represent local mapping intent only until catalog-service mapping persistence is available.</p>
      <div className="quote-table" role="table" aria-label="Product stipulation mapping matrix">
        <div role="row" className="quote-table__row quote-table__row--head"><span role="columnheader">Product</span>{stipulations.map((stipulation) => <span role="columnheader" key={stipulation.stipulationId}>{stipulation.stipulationCode}<br />{stipulation.category}</span>)}</div>
        {products.map((product) => (
          <div role="row" className="quote-table__row" key={product.productId}>
            <span role="cell"><strong>{product.productCode}</strong><br />{product.productName}</span>
            {stipulations.map((stipulation) => {
              const checked = mappingLookup.has(`${product.productId}:${stipulation.stipulationId}`);
              return <span role="cell" key={stipulation.stipulationId}><label><input type="checkbox" checked={checked} onChange={() => onToggle(product.productId, stipulation.stipulationId)} /> {checked ? 'Mapped' : 'Not mapped'}</label></span>;
            })}
          </div>
        ))}
      </div>
      <ChipList label="Mapping order evidence" values={mappings.map((mapping) => `${mapping.displayOrder}: ${mapping.productId} -> ${mapping.stipulationId}${mapping.isRequired ? ' required' : ' conditional'}`)} />
    </section>
  );
}

function ProductFormModal({ error, onClose, onSubmit, pricingRuleSets }: { error: string; onClose: () => void; onSubmit: (event: FormEvent<HTMLFormElement>) => void; pricingRuleSets: string[] }) {
  return (
    <div role="dialog" aria-modal="true" aria-labelledby="product-form-heading" className="modal-panel">
      <form onSubmit={onSubmit}>
        <h2 id="product-form-heading">Product definition form</h2>
        {error ? <div className="banner banner--blocked" role="alert">{error}</div> : null}
        <Field label="Product Code" name="productCode" required />
        <Field label="Product Name" name="productName" required />
        <Field label="Product Type" name="productType" />
        <Field label="Investor Code" name="investorCode" />
        <Field label="Channel Code" name="channelCode" />
        <label>Description<textarea name="description" /></label>
        <Field label="Min Loan Amount" name="minLoanAmount" inputMode="decimal" help="Optional backend constraint reference; leave blank when policy source is unavailable." />
        <Field label="Max Loan Amount" name="maxLoanAmount" inputMode="decimal" help="Optional backend constraint reference; leave blank when policy source is unavailable." />
        <Field label="Min FICO" name="minFico" inputMode="numeric" help="Optional backend constraint reference; no default is assumed." />
        <Field label="Max LTV" name="maxLtv" inputMode="decimal" help="Optional backend constraint reference; no default is assumed." />
        <Field label="Max DTI" name="maxDti" inputMode="decimal" help="Optional backend constraint reference; no default is assumed." />
        <Field label="Property Types" name="propertyTypes" help="Comma-separated backend references." />
        <Field label="Occupancy Types" name="occupancyTypes" help="Comma-separated backend references." />
        <Field label="Loan Purposes" name="loanPurposes" help="Comma-separated backend references." />
        <label>Pricing Rule Set<select name="pricingRuleSet">{pricingRuleSets.map((ruleSet) => <option key={ruleSet} value={ruleSet}>{ruleSet}</option>)}</select></label>
        <button type="submit">Create Product</button>
        <button type="button" onClick={onClose}>Cancel</button>
      </form>
    </div>
  );
}

function StipulationFormModal({ error, onClose, onSubmit }: { error: string; onClose: () => void; onSubmit: (event: FormEvent<HTMLFormElement>) => void }) {
  return (
    <div role="dialog" aria-modal="true" aria-labelledby="stipulation-form-heading" className="modal-panel">
      <form onSubmit={onSubmit}>
        <h2 id="stipulation-form-heading">Stipulation form</h2>
        {error ? <div className="banner banner--blocked" role="alert">{error}</div> : null}
        <Field label="Stipulation Code" name="stipulationCode" required />
        <Field label="Stipulation Name" name="stipulationName" required />
        <Field label="Category" name="category" />
        <label>Severity<select name="severity"><option>REQUIRED</option><option>CONDITIONAL</option><option>ADVISORY</option></select></label>
        <label>Description<textarea name="description" /></label>
        <Field label="Applies To Product Types" name="appliesToProductTypes" help="Comma-separated backend product type references." />
        <label>Validation Rule JSON<textarea name="validationRule" aria-label="Validation Rule JSON" /></label>
        <button type="submit">Create Stipulation</button>
        <button type="button" onClick={onClose}>Cancel</button>
      </form>
    </div>
  );
}

function Field({ label, name, help, required = false, inputMode }: { label: string; name: string; help?: string; required?: boolean; inputMode?: 'decimal' | 'numeric' }) {
  return <label>{label}<input name={name} required={required} inputMode={inputMode} />{help ? <span className="field-help">{help}</span> : null}</label>;
}

function ChipList({ label, values }: { label: string; values: string[] }) {
  const safeValues = values.length ? values : ['No backend reference supplied'];
  return <ul className="chip-list" aria-label={label}>{safeValues.map((value) => <li key={value}>{value}</li>)}</ul>;
}

function rangeText(min?: number | null, max?: number | null) {
  if (min == null && max == null) return 'Backend constraints required';
  return `${min ?? 'No min'} - ${max ?? 'No max'}`;
}

function constraintText(product: ProductAdminProduct) {
  const values = [`FICO ${product.minFico ?? 'backend required'}`, `LTV ${product.maxLtv ?? 'backend required'}`, `DTI ${product.maxDti ?? 'backend required'}`];
  return values.join(' / ');
}

export default ProductAdminScreen;
