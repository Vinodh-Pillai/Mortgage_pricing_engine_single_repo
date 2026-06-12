import type { BorrowerIntake, IntakeValidation, QuoteRunLaunch } from '../../lib/api/quoteRuns';
import { quoteIntakeTraceId } from './metadata';

export type QuoteLaunchResult =
  | { kind: 'created'; launch: QuoteRunLaunch }
  | { kind: 'blocked'; validation: IntakeValidation; blockers: string[] }
  | { kind: 'needs-attention'; message: string; blockers: string[] };

export async function launchQuoteRun(
  tenantId: string,
  scenarioId: string,
  scenarioVersion: number,
  intakeData: Partial<BorrowerIntake>,
  fetchImpl: typeof fetch = fetch,
): Promise<QuoteLaunchResult> {
  const response = await fetchImpl(`/api/v1/tenants/${encodeURIComponent(tenantId)}/quote-runs`, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'X-Ui-Trace-Id': quoteIntakeTraceId,
    },
    body: JSON.stringify({ scenarioId, scenarioVersion, intakeData }),
  });

  if (response.status >= 500) throw new Error('Quote run launch is temporarily unavailable.');
  const launch = (await response.json()) as QuoteRunLaunch;
  if (launch.status === 'BLOCKED') {
    return {
      kind: 'blocked',
      validation: launch.validationSummary,
      blockers: launch.missingContractBlockers ?? Object.values(launch.validationSummary.blockers).filter(Boolean),
    };
  }
  if (!launch.runId) {
    return { kind: 'needs-attention', message: 'Quote run was not created because the connected service did not return a run identifier.', blockers: launch.missingContractBlockers ?? [] };
  }
  return { kind: 'created', launch };
}
