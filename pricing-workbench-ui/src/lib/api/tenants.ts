export type TenantSetupRequest = {
  tenantName: string;
  operationsContact: string;
  launchGoal: string;
};

export type TenantSetupResult = {
  tenantId: string | null;
  status: 'RECORDED' | 'BLOCKED';
  message: string;
  nextStep: string;
  placeholders: string[];
};

export async function createTenantWorkspace(
  setup: TenantSetupRequest,
  fetchImpl: typeof fetch = fetch,
): Promise<TenantSetupResult> {
  const response = await fetchImpl('/api/v1/tenants/workspaces', {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'X-Ui-Trace-Id': 'tenant-s11-local-trace',
    },
    body: JSON.stringify(setup),
  });

  if (response.status >= 500) {
    throw new Error('Tenant setup is temporarily unavailable.');
  }

  return response.json() as Promise<TenantSetupResult>;
}

export type TenantStatus = 'PENDING_ACTIVATION' | 'ACTIVE' | 'SUSPENDED' | 'DEACTIVATED';

export type TenantAdminRecord = {
  tenantId: string;
  name: string;
  displayName: string;
  status: TenantStatus;
  createdAt: string;
  updatedAt?: string;
  activatedAt?: string | null;
  suspendedAt?: string | null;
  deactivatedAt?: string | null;
  assignedUserCount: number;
  logoUrl?: string;
  primaryColor?: string;
  secondaryColor?: string;
  contactEmail?: string;
  contactPhone?: string;
  addressLine1?: string;
  city?: string;
  state?: string;
  postalCode?: string;
  country?: string;
  nmlsId?: string;
};

export type TenantAdminListResponse = {
  content: TenantAdminRecord[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
};

export type TenantFeatureFlag = {
  enabled: boolean;
  config?: Record<string, unknown>;
  updatedAt?: string;
  updatedBy?: string;
};

export type TenantFeatureFlagsResponse = {
  tenantId: string;
  flags: Record<string, TenantFeatureFlag>;
};

export type TenantCreatePayload = {
  tenantName: string;
  displayName: string;
  contactEmail: string;
  contactPhone: string;
  addressLine1: string;
  city: string;
  state: string;
  postalCode: string;
  country: string;
  nmlsId: string;
  logoUrl: string;
  primaryColor: string;
  secondaryColor: string;
};

export async function fetchTenantAdminList(fetchImpl: typeof fetch = fetch): Promise<TenantAdminListResponse> {
  const response = await fetchImpl('/api/v1/admin/tenants?page=0&size=20', { headers: { Accept: 'application/json' } });
  if (!response.ok) throw new Error('Tenant admin API is unavailable.');
  return response.json() as Promise<TenantAdminListResponse>;
}

export async function createTenantAdminRecord(payload: TenantCreatePayload, fetchImpl: typeof fetch = fetch): Promise<TenantAdminRecord> {
  const response = await fetchImpl('/api/v1/admin/tenants', {
    method: 'POST',
    headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  if (!response.ok) throw new Error('Tenant creation failed validation or the API is unavailable.');
  return response.json() as Promise<TenantAdminRecord>;
}

export async function updateTenantStatus(tenantId: string, action: 'activate' | 'suspend' | 'deactivate', fetchImpl: typeof fetch = fetch): Promise<TenantAdminRecord> {
  const response = await fetchImpl(`/api/v1/admin/tenants/${encodeURIComponent(tenantId)}/${action}`, {
    method: 'POST',
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) throw new Error(`Tenant ${action} action failed or is unavailable.`);
  return response.json() as Promise<TenantAdminRecord>;
}

export async function fetchTenantFeatureFlags(tenantId: string, fetchImpl: typeof fetch = fetch): Promise<TenantFeatureFlagsResponse> {
  const response = await fetchImpl(`/api/v1/admin/tenants/${encodeURIComponent(tenantId)}/feature-flags`, { headers: { Accept: 'application/json' } });
  if (!response.ok) throw new Error('Tenant feature flag API is unavailable.');
  return response.json() as Promise<TenantFeatureFlagsResponse>;
}
