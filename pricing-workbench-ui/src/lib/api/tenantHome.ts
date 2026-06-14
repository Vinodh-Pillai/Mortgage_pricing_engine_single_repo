export type TenantProductStatus = 'ACTIVE' | 'INACTIVE' | 'PENDING';

export interface TenantProductFilter {
  tenantId: string;
  productTypes?: string[];
  investors?: string[];
  channels?: string[];
  status?: TenantProductStatus | 'ALL';
  page: number;
  pageSize: number;
}

export interface AuthorizedProduct {
  productCode: string;
  productName: string;
  productType: string;
  investorCode: string;
  channelCode: string;
  status: TenantProductStatus;
  baseRateMin?: number;
  baseRateMax?: number;
  rateSpread?: number;
  lastUpdated: string;
  authorizationExpiresAt?: string | null;
}

export interface TenantProductsResponse {
  products: AuthorizedProduct[];
  totalCount: number;
  page: number;
  pageSize: number;
  availableFilters: {
    productTypes: string[];
    investors: string[];
    channels: string[];
  };
}

export interface TenantHomeTenantContext {
  tenantId: string;
  tenantName: string;
  status: 'ACTIVE' | 'SUSPENDED';
  userId: string;
}

export const tenantHomeEvidenceTarget = '.local-harness/evidence/PII-51-S01/tenant-home-screen.json';

export const tenantHomePreviewTenants: TenantHomeTenantContext[] = [
  { tenantId: 'tenant-preview-acme', tenantName: 'Acme Mortgage Preview', status: 'ACTIVE', userId: 'local-tenant-user' },
  { tenantId: 'tenant-preview-regional', tenantName: 'Regional Lending Preview', status: 'ACTIVE', userId: 'local-tenant-user' },
];

export const tenantHomePreviewProducts: AuthorizedProduct[] = [
  {
    productCode: 'CONF_30YR_PREVIEW',
    productName: 'Conventional 30-Year Preview',
    productType: 'CONVENTIONAL',
    investorCode: 'FNMA',
    channelCode: 'RETAIL',
    status: 'ACTIVE',
    lastUpdated: 'backend-ref:last-updated',
    authorizationExpiresAt: null,
  },
  {
    productCode: 'CONF_15YR_PREVIEW',
    productName: 'Conventional 15-Year Preview',
    productType: 'CONVENTIONAL',
    investorCode: 'FHLMC',
    channelCode: 'RETAIL',
    status: 'ACTIVE',
    lastUpdated: 'backend-ref:last-updated',
    authorizationExpiresAt: null,
  },
  {
    productCode: 'FHA_30YR_PREVIEW',
    productName: 'FHA 30-Year Preview',
    productType: 'FHA',
    investorCode: 'GNMA',
    channelCode: 'WHOLESALE',
    status: 'PENDING',
    lastUpdated: 'backend-ref:last-updated',
    authorizationExpiresAt: null,
  },
  {
    productCode: 'VA_30YR_PREVIEW',
    productName: 'VA 30-Year Preview',
    productType: 'VA',
    investorCode: 'GNMA',
    channelCode: 'CORR',
    status: 'ACTIVE',
    lastUpdated: 'backend-ref:last-updated',
    authorizationExpiresAt: null,
  },
  {
    productCode: 'JUMBO_PREVIEW',
    productName: 'Jumbo Preview Product',
    productType: 'JUMBO',
    investorCode: 'CHASE_PORT',
    channelCode: 'TPO',
    status: 'INACTIVE',
    lastUpdated: 'backend-ref:last-updated',
    authorizationExpiresAt: 'backend-ref:authorization-expiration',
  },
];

export async function fetchTenantProducts(
  filter: TenantProductFilter,
  fetchImpl: typeof fetch = fetch,
): Promise<TenantProductsResponse> {
  const params = new URLSearchParams();
  if (filter.productTypes?.length) params.set('productTypes', filter.productTypes.join(','));
  if (filter.investors?.length) params.set('investors', filter.investors.join(','));
  if (filter.channels?.length) params.set('channels', filter.channels.join(','));
  if (filter.status) params.set('status', filter.status);
  params.set('page', String(filter.page));
  params.set('pageSize', String(filter.pageSize));

  const response = await fetchImpl(`/api/v1/tenant/${encodeURIComponent(filter.tenantId)}/products?${params.toString()}`, {
    headers: {
      Accept: 'application/json',
      'X-Ui-Trace-Id': 'tenant-home-local-trace',
    },
  });
  if (!response.ok) throw new Error('Tenant product authorization API is unavailable.');
  return response.json() as Promise<TenantProductsResponse>;
}

export async function fetchTenantProductFilters(
  tenantId: string,
  fetchImpl: typeof fetch = fetch,
): Promise<TenantProductsResponse['availableFilters']> {
  const response = await fetchImpl(`/api/v1/tenant/${encodeURIComponent(tenantId)}/products/filters`, {
    headers: {
      Accept: 'application/json',
      'X-Ui-Trace-Id': 'tenant-home-local-trace',
    },
  });
  if (!response.ok) throw new Error('Tenant product filter API is unavailable.');
  return response.json() as Promise<TenantProductsResponse['availableFilters']>;
}

export function filterTenantProductsLocally(products: AuthorizedProduct[], filter: TenantProductFilter): TenantProductsResponse {
  const filtered = products.filter((product) => {
    const productTypeMatches = !filter.productTypes?.length || filter.productTypes.includes(product.productType);
    const investorMatches = !filter.investors?.length || filter.investors.includes(product.investorCode);
    const channelMatches = !filter.channels?.length || filter.channels.includes(product.channelCode);
    const statusMatches = !filter.status || filter.status === 'ALL' || product.status === filter.status;
    return productTypeMatches && investorMatches && channelMatches && statusMatches;
  });
  const pageSize = Math.max(1, filter.pageSize);
  const page = Math.max(1, filter.page);
  const start = (page - 1) * pageSize;
  return {
    products: filtered.slice(start, start + pageSize),
    totalCount: filtered.length,
    page,
    pageSize,
    availableFilters: availableFiltersFor(products),
  };
}

export function availableFiltersFor(products: AuthorizedProduct[]): TenantProductsResponse['availableFilters'] {
  return {
    productTypes: uniqueSorted(products.map((product) => product.productType)),
    investors: uniqueSorted(products.map((product) => product.investorCode)),
    channels: uniqueSorted(products.map((product) => product.channelCode)),
  };
}

function uniqueSorted(values: string[]) {
  return Array.from(new Set(values.filter(Boolean))).sort((left, right) => left.localeCompare(right));
}
