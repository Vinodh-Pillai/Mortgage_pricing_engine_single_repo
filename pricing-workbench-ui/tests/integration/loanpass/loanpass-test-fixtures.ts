import type { BorrowerIntake, IntakeValidation, QuoteRunLaunch, ScenarioIntakeField, ScenarioIntakeMetadata } from '../../../src/lib/api/quoteRuns';
import { initialQuoteIntake } from '../../../src/screens/quoteIntake/QuoteIntakeFlow';

export const loanPassTraceId = 'brw-s01-local-trace';
export const loanPassTenantId = 'ui-preview-tenant';

export const loanPassMinimumIntake: BorrowerIntake = {
  ...initialQuoteIntake,
  borrowerLastName: 'Borrower',
  loanNumber: 'LP-1001',
  mortgageType: 'Conventional',
};

export const loanPassFullIntake: BorrowerIntake = Object.fromEntries(
  Object.keys(initialQuoteIntake).map((field) => [field, `${field}-value`]),
) as BorrowerIntake;

export function loanPassField(fieldId: keyof BorrowerIntake, required = false, dataType: ScenarioIntakeField['dataType'] = 'text'): ScenarioIntakeField {
  return {
    fieldId,
    label: fieldId.replace(/[A-Z]/g, ' $&').replace(/^./, (character) => character.toUpperCase()),
    groupId: 'loanpass-approved-test-metadata',
    dataType,
    required,
    helpText: `${fieldId} help`,
    sourceRef: 'PII-26-S13-approved-metadata',
    decisionQuality: 'VERIFIED',
    validationMessages: [],
  };
}

export function loanPassMetadata(): ScenarioIntakeMetadata {
  return {
    tenantContext: loanPassTenantId,
    dependencyStatus: 'READY',
    fieldGroups: [
      { groupId: 'scenario-identity', label: 'Loan Basics', helpText: 'minimum fields', fields: [loanPassField('borrowerLastName', true), loanPassField('loanNumber', true), loanPassField('mortgageType', true)] },
      { groupId: 'borrower-credit', label: 'Borrower & Credit', helpText: 'borrower fields', fields: [loanPassField('borrowerFirstName'), loanPassField('contactEmail', false, 'email'), loanPassField('decisionCreditScore', false, 'number')] },
      { groupId: 'loan-structure', label: 'Loan Structure', helpText: 'loan fields', fields: [loanPassField('loanPurpose'), loanPassField('baseLoanAmount', false, 'number'), loanPassField('desiredAmortizationType')] },
      { groupId: 'property', label: 'Property', helpText: 'property fields', fields: [loanPassField('state'), loanPassField('propertyZip'), loanPassField('propertyType'), loanPassField('occupancyType'), loanPassField('purchasePrice', false, 'number')] },
      { groupId: 'income-assets', label: 'Income & Assets', helpText: 'income fields', fields: [loanPassField('selfEmployed'), loanPassField('totalBorrowerIncome', false, 'number'), loanPassField('documentationType')] },
    ],
    decisionControls: [],
    validationIssues: [],
    auditPackageId: 'audit-pii-26-s17-local',
    replayHashRef: 'replay-pii-26-s17-local',
    fallbackReason: '',
    uiTraceId: loanPassTraceId,
    quickQuoteState: {
      minimalFirstStepFields: ['borrowerLastName', 'loanNumber', 'mortgageType'],
      progressiveSectionOrder: ['scenario-identity', 'borrower-credit', 'loan-structure', 'property', 'income-assets'],
      quoteServiceRequiredFacts: ['scenarioId', 'scenarioVersion'],
      backendOwnedFactSources: ['scenario-service draft', 'quote-service launch'],
      blockedByContracts: [],
      fallbackReason: '',
    },
  };
}

export function createdLaunchResponse(): QuoteRunLaunch {
  return {
    runId: 'loanpass-run-123',
    status: 'CREATED',
    nextRoute: '/quote/loanpass-run-123/offers',
    validationSummary: passedValidation(),
    uiTraceId: loanPassTraceId,
    events: ['QUOTE_RUN_CREATED'],
    fallbackMode: false,
    dependencyStatus: 'READY',
    auditPackageId: 'audit-pii-26-s17-local',
    replayHashRef: 'replay-pii-26-s17-local',
    validationIssues: [],
    missingContractBlockers: [],
  };
}

export function blockedLaunchResponse(): QuoteRunLaunch {
  return {
    ...createdLaunchResponse(),
    runId: null,
    status: 'BLOCKED',
    nextRoute: null,
    validationSummary: {
      passed: false,
      status: 'BLOCKED',
      message: 'LoanPass rejected required intake fields.',
      blockers: { borrowerLastName: 'Borrower last name is required.' },
    },
    missingContractBlockers: ['borrowerLastName'],
  };
}

export function passedValidation(): IntakeValidation {
  return { passed: true, status: 'PASSED', message: 'Validated by deterministic local LoanPass mock.', blockers: {} };
}

export function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

export function textResponse(body: string, status: number): Response {
  return new Response(body, { status, headers: { 'Content-Type': 'text/plain' } });
}
