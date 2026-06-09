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
