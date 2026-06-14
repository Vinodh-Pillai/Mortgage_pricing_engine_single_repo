export type ProductSetupRequest = {
  productName: string;
  productOwner: string;
  borrowerNeed: string;
};

export type ProductSetupResult = {
  productId: string | null;
  status: 'RECORDED' | 'BLOCKED';
  message: string;
  nextStep: string;
  placeholders: string[];
};

export type ProductCatalogArea = {
  areaId: string;
  label: string;
  sourceRef: string;
  status: 'BLOCKED' | 'READY' | string;
  guidance: string;
  fields: string[];
  validationMessages: string[];
};

export type ProductCatalogLifecycle = {
  state: string;
  actionsDisabled: boolean;
  actions: string[];
  snapshotRefs: string[];
  auditRefs: string[];
  blocker: string;
};

export type ProductCatalogManagerView = {
  tenantContext: string;
  dependencyStatus: string;
  areas: ProductCatalogArea[];
  lifecycle: ProductCatalogLifecycle;
  events: string[];
  fallbackReason: string;
  uiTraceId: string;
};

export type ProductAdminStatus = 'DRAFT' | 'REVIEW' | 'PUBLISHED' | 'DEPRECATED' | string;

export type ProductAdminProduct = {
  productId: string;
  productCode: string;
  productName: string;
  productType: string;
  investorCode: string;
  channelCode: string;
  status: ProductAdminStatus;
  description?: string;
  minLoanAmount?: number | null;
  maxLoanAmount?: number | null;
  minFico?: number | null;
  maxLtv?: number | null;
  maxDti?: number | null;
  propertyTypes: string[];
  occupancyTypes: string[];
  loanPurposes: string[];
  pricingRuleSet?: string | null;
  version: number;
  changeSummary?: string;
};

export type ProductAdminStipulation = {
  stipulationId: string;
  stipulationCode: string;
  stipulationName: string;
  category: string;
  severity: 'REQUIRED' | 'CONDITIONAL' | 'ADVISORY' | string;
  description?: string;
  validationRule: Record<string, unknown> | null;
  appliesToProductTypes: string[];
};

export type ProductAdminMapping = {
  productId: string;
  stipulationId: string;
  isRequired: boolean;
  conditionExpression: Record<string, unknown> | null;
  displayOrder: number;
};

export type ProductAdminView = {
  tenantContext: string;
  dependencyStatus: string;
  products: ProductAdminProduct[];
  stipulations: ProductAdminStipulation[];
  mappings: ProductAdminMapping[];
  pricingRuleSets: string[];
  lifecycle: ProductAdminStatus[];
  fallbackReason: string;
  uiTraceId: string;
};

export async function createProductCatalogEntry(
  product: ProductSetupRequest,
  fetchImpl: typeof fetch = fetch,
): Promise<ProductSetupResult> {
  const response = await fetchImpl('/api/v1/products/catalog', {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'X-Ui-Trace-Id': 'product-s11-local-trace',
    },
    body: JSON.stringify(product),
  });

  if (response.status >= 500) {
    throw new Error('Product setup is temporarily unavailable.');
  }

  return response.json() as Promise<ProductSetupResult>;
}

export async function fetchProductCatalogManager(fetchImpl: typeof fetch = fetch): Promise<ProductCatalogManagerView> {
  const response = await fetchImpl('/api/v1/products/catalog/manager', {
    headers: {
      Accept: 'application/json',
      'X-Tenant-Context': 'ui-preview-tenant',
      'X-Ui-Trace-Id': 'catalog-manager-local-trace',
    },
  });

  if (response.status >= 500) {
    throw new Error('Product catalog manager is temporarily unavailable.');
  }

  return response.json() as Promise<ProductCatalogManagerView>;
}

export async function fetchProductAdmin(fetchImpl: typeof fetch = fetch): Promise<ProductAdminView> {
  const response = await fetchImpl('/api/v1/admin/products', {
    headers: {
      Accept: 'application/json',
      'X-Tenant-Context': 'ui-preview-tenant',
      'X-Ui-Trace-Id': 'product-admin-local-trace',
    },
  });

  if (response.status >= 500) {
    throw new Error('Product administration is temporarily unavailable.');
  }

  return response.json() as Promise<ProductAdminView>;
}
