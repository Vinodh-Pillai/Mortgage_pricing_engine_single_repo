export type ExceptionConcessionSection = {
  sectionId: string;
  label: string;
  status: string;
  backendRefs: string[];
  auditRefs: string[];
  summary: string;
};

export type ManualPriceMutationGuardView = {
  commitDisabled: boolean;
  decision: string;
  reasonCodes: string[];
  escalationPath: string;
  auditRef: string;
  replayHash: string;
};

export type ExceptionConcessionWorkbenchView = {
  tenantContext: string;
  dependencyStatus: string;
  status: string;
  sections: ExceptionConcessionSection[];
  manualPriceMutationGuard: ManualPriceMutationGuardView;
  crossServiceRefs: string[];
  versionRefs: string[];
  auditRefs: string[];
  blockers: string[];
  replayHash: string;
  exportManifestRef: string;
  uiTraceId: string;
  events: string[];
  fallbackReason: string;
};

export async function fetchExceptionConcessionWorkbench(
  tenantContext: string,
  fetchImpl: typeof fetch = fetch,
): Promise<ExceptionConcessionWorkbenchView> {
  const response = await fetchImpl('/api/v1/exceptions/concessions/workbench', {
    headers: {
      Accept: 'application/json',
      'X-Tenant-Context': tenantContext,
      'X-Ui-Trace-Id': 'exception-s18-local-trace',
    },
  });

  if (response.status >= 500) {
    throw new Error('Exception concession workbench is temporarily unavailable.');
  }

  return (await response.json()) as ExceptionConcessionWorkbenchView;
}
