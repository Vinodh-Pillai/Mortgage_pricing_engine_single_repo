import { describe, expect, it, vi } from 'vitest';
import { fetchScenarioIntakeMetadata, launchQuoteRun as launchQuoteRunClient, toLoanPassQuoteIntakePayload } from '../../../src/lib/api/quoteRuns';
import { launchQuoteRun as launchPipelineQuoteRun } from '../../../src/screens/quoteIntake/launch';
import { blockedLaunchResponse, createdLaunchResponse, jsonResponse, loanPassFullIntake, loanPassMetadata, loanPassMinimumIntake, loanPassTenantId, loanPassTraceId } from './loanpass-test-fixtures';

describe('PII-26-S17 LoanPass API request and response contract', () => {
  it('GET intake metadata sends the approved local trace header and parses the configured metadata response', async () => {
    const fetchMock = vi.fn(async () => jsonResponse(loanPassMetadata()));

    const metadata = await fetchScenarioIntakeMetadata(loanPassTenantId, fetchMock as typeof fetch);

    expect(fetchMock).toHaveBeenCalledWith(`/api/v1/tenants/${loanPassTenantId}/quote-runs/intake-metadata`, {
      headers: { Accept: 'application/json', 'X-Ui-Trace-Id': loanPassTraceId },
    });
    expect(metadata.quickQuoteState?.minimalFirstStepFields).toEqual(['borrowerLastName', 'loanNumber', 'mortgageType']);
    expect(metadata.fieldGroups.flatMap((group) => group.fields).map((field) => field.fieldId)).toContain('mortgageType');
  });

  it('POST quote-runs uses only the LoanPass launch payload fields and includes the approved trace header', async () => {
    const fetchMock = vi.fn(async () => jsonResponse(createdLaunchResponse()));

    const launch = await launchQuoteRunClient(loanPassTenantId, {
      ...loanPassFullIntake,
      quoteIntent: 'legacy value should be excluded',
      scenarioName: 'legacy scenario name should be excluded',
    }, fetchMock as typeof fetch);

    expect(launch.runId).toBe('loanpass-run-123');
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    const requestBody = JSON.parse(String(init?.body));

    expect(url).toBe(`/api/v1/tenants/${loanPassTenantId}/quote-runs`);
    expect(init).toMatchObject({
      method: 'POST',
      headers: { Accept: 'application/json', 'Content-Type': 'application/json', 'X-Ui-Trace-Id': loanPassTraceId },
    });
    expect(requestBody).toEqual(toLoanPassQuoteIntakePayload(loanPassFullIntake));
    expect(requestBody).not.toHaveProperty('quoteIntent');
    expect(requestBody).not.toHaveProperty('scenarioName');
  });

  it('UI launch boundary posts scenario identity, version, and intakeData without calling external LoanPass services', async () => {
    const fetchMock = vi.fn(async () => jsonResponse(createdLaunchResponse()));

    const result = await launchPipelineQuoteRun(loanPassTenantId, 'scenario-loanpass-1', 3, loanPassMinimumIntake, fetchMock as typeof fetch);

    expect(result.kind).toBe('created');
    expect(fetchMock).toHaveBeenCalledWith(`/api/v1/tenants/${loanPassTenantId}/quote-runs`, expect.objectContaining({ method: 'POST' }));
    const request = JSON.parse(String(fetchMock.mock.calls[0][1]?.body));
    expect(request).toEqual({ scenarioId: 'scenario-loanpass-1', scenarioVersion: 3, intakeData: loanPassMinimumIntake });
    expect(String(fetchMock.mock.calls[0][0])).not.toContain('loanpass');
  });

  it('UI launch boundary preserves LoanPass field-level validation blockers from a 422-style response body', async () => {
    const fetchMock = vi.fn(async () => jsonResponse(blockedLaunchResponse(), 422));

    const result = await launchPipelineQuoteRun(loanPassTenantId, 'scenario-loanpass-1', 3, { ...loanPassMinimumIntake, borrowerLastName: '' }, fetchMock as typeof fetch);

    expect(result).toEqual({
      kind: 'blocked',
      validation: blockedLaunchResponse().validationSummary,
      blockers: ['borrowerLastName'],
    });
  });

  it('UI launch boundary reports needs-attention when a successful service response lacks a run identifier', async () => {
    const fetchMock = vi.fn(async () => jsonResponse({ ...createdLaunchResponse(), runId: null }));

    const result = await launchPipelineQuoteRun(loanPassTenantId, 'scenario-loanpass-1', 3, loanPassMinimumIntake, fetchMock as typeof fetch);

    expect(result).toEqual({ kind: 'needs-attention', message: 'Quote run was not created because the connected service did not return a run identifier.', blockers: [] });
  });
});
