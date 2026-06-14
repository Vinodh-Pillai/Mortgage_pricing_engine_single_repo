import { useEffect, useMemo, useState } from 'react';
import type { ScreenEvidence, ScreenVisualState } from '../contract/ScreenProps';
import {
  fetchTenantProducts,
  filterTenantProductsLocally,
  tenantHomeEvidenceTarget,
  tenantHomePreviewProducts,
  tenantHomePreviewTenants,
  type AuthorizedProduct,
  type TenantHomeTenantContext,
  type TenantProductStatus,
  type TenantProductsResponse,
} from '../../lib/api/tenantHome';
import './TenantHomeScreen.css';

const pageSize = 20;
const defaultUserId = 'local-tenant-user';

type TenantHomeScreenProps = {
  fetchImpl?: typeof fetch;
  tenants?: TenantHomeTenantContext[];
  initialProducts?: AuthorizedProduct[];
  userId?: string;
  onNavigate?: (target: string) => void;
  onEvidenceCapture?: (evidence: ScreenEvidence) => void;
};

type FilterState = {
  productTypes: string[];
  investors: string[];
  channels: string[];
  status: TenantProductStatus | 'ALL';
};

type TenantHomeState =
  | { kind: 'loading' }
  | { kind: 'ready'; response: TenantProductsResponse; source: 'backend' | 'local-preview'; message: string }
  | { kind: 'blocked'; response: TenantProductsResponse; message: string };

const emptyFilters: FilterState = {
  productTypes: [],
  investors: [],
  channels: [],
  status: 'ACTIVE',
};

export function TenantHomeScreen({
  fetchImpl = fetch,
  tenants = tenantHomePreviewTenants,
  initialProducts,
  userId = defaultUserId,
  onNavigate,
  onEvidenceCapture,
}: TenantHomeScreenProps) {
  const safeTenants = tenants.length ? tenants : tenantHomePreviewTenants;
  const [selectedTenant, setSelectedTenant] = useState<TenantHomeTenantContext>(() => readStoredTenant(userId, safeTenants));
  const [filters, setFilters] = useState<FilterState>(emptyFilters);
  const [page, setPage] = useState(1);
  const [comparisonCodes, setComparisonCodes] = useState<string[]>([]);
  const [state, setState] = useState<TenantHomeState>({ kind: 'loading' });
  const previewProducts = initialProducts ?? tenantHomePreviewProducts;

  useEffect(() => {
    persistTenantContext(userId, selectedTenant);
  }, [selectedTenant, userId]);

  useEffect(() => {
    let active = true;
    const requestFilter = {
      tenantId: selectedTenant.tenantId,
      productTypes: filters.productTypes,
      investors: filters.investors,
      channels: filters.channels,
      status: filters.status,
      page,
      pageSize,
    };

    if (initialProducts) {
      setState({
        kind: 'ready',
        response: filterTenantProductsLocally(initialProducts, requestFilter),
        source: 'local-preview',
        message: 'Injected tenant-home product evidence.',
      });
      return () => { active = false; };
    }

    setState({ kind: 'loading' });
    fetchTenantProducts(requestFilter, fetchImpl)
      .then((response) => {
        if (active) setState({ kind: 'ready', response, source: 'backend', message: 'Tenant product authorization API connected.' });
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'Tenant product authorization API is unavailable.';
        if (active) {
          setState({
            kind: 'blocked',
            response: filterTenantProductsLocally(previewProducts, requestFilter),
            message: `${message} Showing bounded local preview products; backend authorization, rate indicators, and persisted activity remain blocked.`,
          });
        }
      });

    return () => { active = false; };
  }, [fetchImpl, filters, initialProducts, page, previewProducts, selectedTenant.tenantId]);

  const response = state.kind === 'loading' ? filterTenantProductsLocally(previewProducts, { tenantId: selectedTenant.tenantId, ...filters, page, pageSize }) : state.response;
  const filterCount = filters.productTypes.length + filters.investors.length + filters.channels.length + (filters.status !== 'ALL' ? 1 : 0);
  const totalPages = Math.max(1, Math.ceil(response.totalCount / pageSize));
  const visualState = tenantHomeVisualState(state.kind, response.totalCount);

  useEffect(() => {
    if (state.kind === 'loading') return;
    onEvidenceCapture?.({
      screenId: 'tenant-home',
      timestamp: new Date().toISOString(),
      state: visualState,
      dataRefs: [tenantHomeEvidenceTarget, selectedTenant.tenantId, ...response.products.map((product) => product.productCode)],
      blockers: state.kind === 'blocked' ? [state.message] : [],
    });
  }, [onEvidenceCapture, response.products, response.totalCount, selectedTenant.tenantId, state, visualState]);

  const setMultiFilter = (key: 'productTypes' | 'investors' | 'channels', value: string, checked: boolean) => {
    setPage(1);
    setFilters((current) => ({
      ...current,
      [key]: checked ? [...current[key], value] : current[key].filter((item) => item !== value),
    }));
  };

  const setStatus = (status: TenantProductStatus | 'ALL') => {
    setPage(1);
    setFilters((current) => ({ ...current, status }));
  };

  const clearFilters = () => {
    setPage(1);
    setFilters({ ...emptyFilters, status: 'ALL' });
  };

  const switchTenant = (tenantId: string) => {
    const nextTenant = safeTenants.find((tenant) => tenant.tenantId === tenantId) ?? safeTenants[0];
    setPage(1);
    setSelectedTenant(nextTenant);
  };

  const addToComparison = (productCode: string) => {
    setComparisonCodes((current) => current.includes(productCode) ? current : [...current, productCode]);
  };

  return (
    <div className="tenant-home-shell" aria-labelledby="tenant-home-title">
      <section className="hero tenant-home-hero" aria-labelledby="tenant-home-title">
        <p className="eyebrow">Tenant home · PII-51-S01</p>
        <h2 id="tenant-home-title">Authorized products</h2>
        <p>Filter tenant-authorized product metadata and start pricing workflows without calculating rates or policy in the browser.</p>
        <dl className="status-grid">
          <dt>Tenant context</dt><dd>{selectedTenant.tenantName}</dd>
          <dt>Visible products</dt><dd>{response.totalCount}</dd>
          <dt>Evidence target</dt><dd><code>{tenantHomeEvidenceTarget}</code></dd>
        </dl>
      </section>

      {state.kind === 'loading' ? <div className="banner banner--info" role="status">Loading tenant product authorizations...</div> : null}
      {state.kind === 'blocked' ? (
        <div className="banner banner--blocked" role="alert">
          <strong>Tenant product API needs setup</strong>
          <span>{state.message}</span>
        </div>
      ) : null}

      <section className="tenant-home-layout" aria-label="Tenant home filter and product grid">
        <aside className="tenant-home-filters" aria-label="Tenant context and product filters">
          <TenantContextSelector tenants={safeTenants} selectedTenant={selectedTenant} onChange={switchTenant} />
          <ProductFilterPanel
            availableFilters={response.availableFilters}
            filterCount={filterCount}
            filters={filters}
            onClear={clearFilters}
            onMultiFilter={setMultiFilter}
            onStatus={setStatus}
          />
          <RecentActivity comparisonCodes={comparisonCodes} />
        </aside>

        <ProductGrid
          products={response.products}
          totalCount={response.totalCount}
          page={page}
          totalPages={totalPages}
          onPage={setPage}
          onNavigate={onNavigate}
          onCompare={addToComparison}
        />
      </section>
    </div>
  );
}

function TenantContextSelector({ tenants, selectedTenant, onChange }: { tenants: TenantHomeTenantContext[]; selectedTenant: TenantHomeTenantContext; onChange: (tenantId: string) => void }) {
  const initials = selectedTenant.tenantName.split(/\s+/).map((part) => part[0]).join('').slice(0, 2).toUpperCase();
  return (
    <section className="panel tenant-context-card" aria-labelledby="tenant-context-heading">
      <div className="panel-heading-row">
        <div><p className="eyebrow">Tenant context</p><h2 id="tenant-context-heading">{selectedTenant.tenantName}</h2></div>
        <span className="tenant-avatar" aria-hidden="true">{initials}</span>
      </div>
      <span className={`functionality-badge functionality-badge--${selectedTenant.status === 'ACTIVE' ? 'ready' : 'blocked'}`}>{selectedTenant.status}</span>
      {tenants.length > 1 ? (
        <label className="field-group">Switch Tenant
          <select value={selectedTenant.tenantId} onChange={(event) => onChange(event.target.value)} aria-label="Switch tenant context">
            {tenants.map((tenant) => <option key={tenant.tenantId} value={tenant.tenantId}>{tenant.tenantName}</option>)}
          </select>
        </label>
      ) : <p className="field-help">Single-tenant access; selector is fixed.</p>}
    </section>
  );
}

function ProductFilterPanel({ availableFilters, filterCount, filters, onClear, onMultiFilter, onStatus }: {
  availableFilters: TenantProductsResponse['availableFilters'];
  filterCount: number;
  filters: FilterState;
  onClear: () => void;
  onMultiFilter: (key: 'productTypes' | 'investors' | 'channels', value: string, checked: boolean) => void;
  onStatus: (status: TenantProductStatus | 'ALL') => void;
}) {
  return (
    <section className="panel product-filter-panel" aria-labelledby="product-filter-heading">
      <div className="panel-heading-row">
        <div><p className="eyebrow">Filters</p><h2 id="product-filter-heading">Product filters</h2></div>
        <span className="trace-badge" aria-label={`${filterCount} active filters`}>{filterCount}</span>
      </div>
      <FilterGroup label="Product Type" values={availableFilters.productTypes} selected={filters.productTypes} onChange={(value, checked) => onMultiFilter('productTypes', value, checked)} />
      <FilterGroup label="Investor" values={availableFilters.investors} selected={filters.investors} onChange={(value, checked) => onMultiFilter('investors', value, checked)} />
      <FilterGroup label="Channel" values={availableFilters.channels} selected={filters.channels} onChange={(value, checked) => onMultiFilter('channels', value, checked)} />
      <fieldset className="tenant-filter-group">
        <legend>Status</legend>
        {(['ACTIVE', 'INACTIVE', 'PENDING', 'ALL'] as const).map((status) => (
          <label key={status} className="checkbox-row"><input type="radio" name="tenant-product-status" checked={filters.status === status} onChange={() => onStatus(status)} />{status}</label>
        ))}
      </fieldset>
      <button type="button" className="button-secondary" onClick={onClear}>Clear Filters</button>
    </section>
  );
}

function FilterGroup({ label, values, selected, onChange }: { label: string; values: string[]; selected: string[]; onChange: (value: string, checked: boolean) => void }) {
  return (
    <fieldset className="tenant-filter-group">
      <legend>{label}</legend>
      {values.length === 0 ? <p className="field-help">Backend filters unavailable.</p> : values.map((value) => (
        <label key={value} className="checkbox-row"><input type="checkbox" checked={selected.includes(value)} onChange={(event) => onChange(value, event.target.checked)} />{value}</label>
      ))}
    </fieldset>
  );
}

function ProductGrid({ products, totalCount, page, totalPages, onPage, onNavigate, onCompare }: {
  products: AuthorizedProduct[];
  totalCount: number;
  page: number;
  totalPages: number;
  onPage: (page: number) => void;
  onNavigate?: (target: string) => void;
  onCompare: (productCode: string) => void;
}) {
  return (
    <section className="panel authorized-product-panel" aria-labelledby="authorized-products-heading">
      <div className="panel-heading-row">
        <div><p className="eyebrow">Authorized products</p><h2 id="authorized-products-heading">Product grid</h2></div>
        <span className="trace-badge">{totalCount} total</span>
      </div>
      {products.length === 0 ? <div className="banner banner--info" role="status">No products match your filters. Adjust filters or contact admin.</div> : null}
      <div className="tenant-product-grid" role="list" aria-label="Authorized product cards">
        {products.map((product) => <ProductCard key={product.productCode} product={product} onNavigate={onNavigate} onCompare={onCompare} />)}
      </div>
      <nav className="tenant-pagination" aria-label="Product pagination">
        <button type="button" className="button-secondary" disabled={page <= 1} onClick={() => onPage(page - 1)}>Prev</button>
        <span>Page {page} of {totalPages}</span>
        <button type="button" className="button-secondary" disabled={page >= totalPages} onClick={() => onPage(page + 1)}>Next</button>
      </nav>
    </section>
  );
}

function ProductCard({ product, onNavigate, onCompare }: { product: AuthorizedProduct; onNavigate?: (target: string) => void; onCompare: (productCode: string) => void }) {
  return (
    <article className="tenant-product-card" role="listitem" aria-label={`${product.productName} ${product.status}`}>
      <div className="panel-heading-row">
        <div><p className="module-card__route">{product.productCode}</p><h3>{product.productName}</h3></div>
        <span className={`functionality-badge functionality-badge--${statusClass(product.status)}`}>{product.status}</span>
      </div>
      <dl>
        <dt>Type</dt><dd>{product.productType}</dd>
        <dt>Investor</dt><dd>{product.investorCode}</dd>
        <dt>Channel</dt><dd>{product.channelCode}</dd>
        <dt>Rate indicator</dt><dd>{rateIndicator(product)}</dd>
        <dt>Authorization</dt><dd>{product.authorizationExpiresAt ?? 'No expiration supplied'}</dd>
      </dl>
      <div className="button-row" aria-label={`${product.productCode} quick actions`}>
        <button type="button" onClick={() => onNavigate?.(`/admin/products/catalog/${encodeURIComponent(product.productCode)}`)}>View Details</button>
        <button type="button" onClick={() => onNavigate?.(`/pipeline?product=${encodeURIComponent(product.productCode)}`)}>Create Quote</button>
        <button type="button" className="button-secondary" onClick={() => onCompare(product.productCode)}>Compare</button>
      </div>
    </article>
  );
}

function RecentActivity({ comparisonCodes }: { comparisonCodes: string[] }) {
  const recent = comparisonCodes.slice(-3).reverse();
  return (
    <section className="panel" aria-labelledby="recent-activity-heading">
      <p className="eyebrow">Recent activity</p>
      <h2 id="recent-activity-heading">Quick actions</h2>
      {recent.length === 0 ? <p className="field-help">Product quick actions will appear here during this local session.</p> : (
        <ul className="offer-list" aria-label="Recent tenant home activity">
          {recent.map((code) => <li key={code}>Added {code} to comparison tray.</li>)}
        </ul>
      )}
    </section>
  );
}

function readStoredTenant(userId: string, tenants: TenantHomeTenantContext[]) {
  if (typeof window === 'undefined') return tenants[0];
  try {
    const stored = window.localStorage.getItem(storageKey(userId));
    if (!stored) return tenants[0];
    const parsed = JSON.parse(stored) as Partial<TenantHomeTenantContext>;
    return tenants.find((tenant) => tenant.tenantId === parsed.tenantId) ?? tenants[0];
  } catch {
    return tenants[0];
  }
}

function persistTenantContext(userId: string, tenant: TenantHomeTenantContext) {
  if (typeof window === 'undefined') return;
  window.localStorage.setItem(storageKey(userId), JSON.stringify({ tenantId: tenant.tenantId, tenantName: tenant.tenantName }));
}

function storageKey(userId: string) {
  return `wcpe:tenantContext:${userId}`;
}

function tenantHomeVisualState(kind: TenantHomeState['kind'], totalCount: number): ScreenVisualState {
  if (kind === 'loading') return 'loading';
  if (kind === 'blocked') return 'blocked';
  return totalCount === 0 ? 'empty' : 'ready';
}

function rateIndicator(product: AuthorizedProduct) {
  if (product.baseRateMin == null && product.baseRateMax == null) return 'Rate pending';
  if (product.baseRateMin != null && product.baseRateMax != null) return `${product.baseRateMin.toFixed(3)}% - ${product.baseRateMax.toFixed(3)}%`;
  return `${(product.baseRateMin ?? product.baseRateMax)?.toFixed(3)}%`;
}

function statusClass(status: TenantProductStatus) {
  if (status === 'ACTIVE') return 'ready';
  if (status === 'PENDING') return 'needs-attention';
  return 'blocked';
}

export default TenantHomeScreen;
