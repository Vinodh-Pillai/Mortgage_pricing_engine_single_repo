export type BorrowerIntake = {
  borrowerName: string;
  contactEmail: string;
  quoteGoal: string;
};

export type IntakeValidation = {
  passed: boolean;
  status: 'PASSED' | 'BLOCKED';
  message: string;
  blockers: Partial<Record<keyof BorrowerIntake, string>>;
};

export type QuoteRunLaunch = {
  runId: string | null;
  status: 'CREATED' | 'BLOCKED';
  nextRoute: string | null;
  validationSummary: IntakeValidation;
  uiTraceId: string;
  events: string[];
  fallbackMode: boolean;
  dependencyStatus: string;
};

export async function launchQuoteRun(
  tenantId: string,
  intake: BorrowerIntake,
  fetchImpl: typeof fetch = fetch,
): Promise<QuoteRunLaunch> {
  const response = await fetchImpl(`/api/v1/tenants/${encodeURIComponent(tenantId)}/quote-runs`, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'X-Ui-Trace-Id': 'brw-s01-local-trace',
    },
    body: JSON.stringify(intake),
  });

  const launch = (await response.json()) as QuoteRunLaunch;
  if (response.status >= 500) {
    throw new Error('BFF borrower intake boundary is temporarily unavailable.');
  }

  return launch;
}
