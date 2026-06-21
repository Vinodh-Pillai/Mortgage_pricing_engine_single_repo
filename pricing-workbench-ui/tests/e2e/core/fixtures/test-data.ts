export interface TestBorrower {
  borrowerName: string;
  borrowerRole: 'PRIMARY' | 'CO_BORROWER' | 'NON_OCCUPANT_CO_BORROWER';
  contactEmail: string;
  creditScore: number;
  creditScoreSource: 'TRI_MERGE' | 'DU' | 'LPA' | 'MANUAL';
  creditReportDate: string;
  monthlyIncome: number;
  incomeType: 'W2' | 'SELF_EMPLOYED' | 'RETIREMENT' | 'OTHER';
  employmentType: 'SALARIED' | 'HOURLY' | 'COMMISSION' | 'SELF_EMPLOYED';
  monthlyDebt: number;
  liquidAssets: number;
  reserves: number;
}

export interface TestLoan {
  quoteIntent: 'PURCHASE' | 'RATE_TERM_REFI' | 'CASH_OUT_REFI' | 'SCENARIO_ANALYSIS';
  channel: 'RETAIL' | 'WHOLESALE' | 'CORRESPONDENT' | 'CONSUMER_DIRECT';
  loanPurpose: 'PURCHASE' | 'RATE_TERM_REFI' | 'CASH_OUT_REFI';
  loanAmount: number;
  purchasePriceOrValue: number;
  downPaymentOrEquity: number;
  lienPosition: 'FIRST' | 'SECOND';
  termMonths: 180 | 240 | 360;
  amortizationType: 'FIXED' | 'ARM';
  requestedLockPeriodDays: 15 | 30 | 45 | 60;
}

export interface TestProperty {
  propertyState: string;
  propertyCounty: string;
  propertyZip: string;
  propertyType: 'SINGLE_FAMILY' | 'CONDO' | 'TOWNHOUSE' | 'MULTI_FAMILY_2_4' | 'MANUFACTURED';
  occupancyType: 'PRIMARY_RESIDENCE' | 'SECOND_HOME' | 'INVESTMENT_PROPERTY';
  unitCount: 1 | 2 | 3 | 4;
  purchasePrice: number;
  appraisedValue?: number;
}

export interface TestScenario {
  scenarioName: string;
  externalLoanId: string;
  borrower: TestBorrower;
  coBorrower?: TestBorrower;
  loan: TestLoan;
  property: TestProperty;
  productPreference?: string;
  effectiveDate?: string;
}

export const testBorrowers: Record<string, TestBorrower> = {
  prime: {
    borrowerName: 'John Smith',
    borrowerRole: 'PRIMARY',
    contactEmail: 'john.smith@example.com',
    creditScore: 780,
    creditScoreSource: 'TRI_MERGE',
    creditReportDate: '2024-01-15',
    monthlyIncome: 12500,
    incomeType: 'W2',
    employmentType: 'SALARIED',
    monthlyDebt: 2800,
    liquidAssets: 75000,
    reserves: 60000,
  },
  nearPrime: {
    borrowerName: 'Jane Doe',
    borrowerRole: 'PRIMARY',
    contactEmail: 'jane.doe@example.com',
    creditScore: 700,
    creditScoreSource: 'DU',
    creditReportDate: '2024-01-10',
    monthlyIncome: 9500,
    incomeType: 'W2',
    employmentType: 'SALARIED',
    monthlyDebt: 3200,
    liquidAssets: 45000,
    reserves: 30000,
  },
  subPrime: {
    borrowerName: 'Bob Wilson',
    borrowerRole: 'PRIMARY',
    contactEmail: 'bob.wilson@example.com',
    creditScore: 640,
    creditScoreSource: 'LPA',
    creditReportDate: '2024-01-05',
    monthlyIncome: 7200,
    incomeType: 'SELF_EMPLOYED',
    employmentType: 'SELF_EMPLOYED',
    monthlyDebt: 2100,
    liquidAssets: 25000,
    reserves: 15000,
  },
  coBorrower: {
    borrowerName: 'Mary Smith',
    borrowerRole: 'CO_BORROWER',
    contactEmail: 'mary.smith@example.com',
    creditScore: 760,
    creditScoreSource: 'TRI_MERGE',
    creditReportDate: '2024-01-15',
    monthlyIncome: 8500,
    incomeType: 'W2',
    employmentType: 'SALARIED',
    monthlyDebt: 1200,
    liquidAssets: 40000,
    reserves: 35000,
  },
};

export const testLoans: Record<string, TestLoan> = {
  purchaseConforming: {
    quoteIntent: 'PURCHASE',
    channel: 'RETAIL',
    loanPurpose: 'PURCHASE',
    loanAmount: 400000,
    purchasePriceOrValue: 500000,
    downPaymentOrEquity: 100000,
    lienPosition: 'FIRST',
    termMonths: 360,
    amortizationType: 'FIXED',
    requestedLockPeriodDays: 30,
  },
  refiRateTerm: {
    quoteIntent: 'RATE_TERM_REFI',
    channel: 'RETAIL',
    loanPurpose: 'RATE_TERM_REFI',
    loanAmount: 350000,
    purchasePriceOrValue: 450000,
    downPaymentOrEquity: 100000,
    lienPosition: 'FIRST',
    termMonths: 360,
    amortizationType: 'FIXED',
    requestedLockPeriodDays: 30,
  },
  cashOutRefi: {
    quoteIntent: 'CASH_OUT_REFI',
    channel: 'RETAIL',
    loanPurpose: 'CASH_OUT_REFI',
    loanAmount: 380000,
    purchasePriceOrValue: 500000,
    downPaymentOrEquity: 120000,
    lienPosition: 'FIRST',
    termMonths: 360,
    amortizationType: 'FIXED',
    requestedLockPeriodDays: 45,
  },
  wholesalePurchase: {
    quoteIntent: 'PURCHASE',
    channel: 'WHOLESALE',
    loanPurpose: 'PURCHASE',
    loanAmount: 425000,
    purchasePriceOrValue: 550000,
    downPaymentOrEquity: 125000,
    lienPosition: 'FIRST',
    termMonths: 360,
    amortizationType: 'FIXED',
    requestedLockPeriodDays: 30,
  },
};

export const testProperties: Record<string, TestProperty> = {
  californiaSFR: {
    propertyState: 'CA',
    propertyCounty: 'Los Angeles',
    propertyZip: '90210',
    propertyType: 'SINGLE_FAMILY',
    occupancyType: 'PRIMARY_RESIDENCE',
    unitCount: 1,
    purchasePrice: 500000,
    appraisedValue: 510000,
  },
  texasCondo: {
    propertyState: 'TX',
    propertyCounty: 'Travis',
    propertyZip: '78701',
    propertyType: 'CONDO',
    occupancyType: 'PRIMARY_RESIDENCE',
    unitCount: 1,
    purchasePrice: 350000,
    appraisedValue: 355000,
  },
  floridaInvestment: {
    propertyState: 'FL',
    propertyCounty: 'Miami-Dade',
    propertyZip: '33101',
    propertyType: 'SINGLE_FAMILY',
    occupancyType: 'INVESTMENT_PROPERTY',
    unitCount: 1,
    purchasePrice: 400000,
    appraisedValue: 405000,
  },
};

export const testScenarios: Record<string, TestScenario> = {
  primePurchase: {
    scenarioName: 'Prime Purchase - CA SFR',
    externalLoanId: 'LOS-2024-001',
    borrower: testBorrowers.prime,
    loan: testLoans.purchaseConforming,
    property: testProperties.californiaSFR,
    productPreference: 'CONV',
    effectiveDate: '2024-01-20',
  },
  nearPrimeRefi: {
    scenarioName: 'Near Prime Rate/Term Refi - TX Condo',
    externalLoanId: 'LOS-2024-002',
    borrower: testBorrowers.nearPrime,
    loan: testLoans.refiRateTerm,
    property: testProperties.texasCondo,
    productPreference: 'CONV',
    effectiveDate: '2024-01-20',
  },
  subPrimeCashOut: {
    scenarioName: 'Sub Prime Cash-Out - FL Investment',
    externalLoanId: 'LOS-2024-003',
    borrower: testBorrowers.subPrime,
    loan: testLoans.cashOutRefi,
    property: testProperties.floridaInvestment,
    productPreference: 'NON_QM',
    effectiveDate: '2024-01-20',
  },
  wholesaleWithCoBorrower: {
    scenarioName: 'Wholesale Purchase with Co-Borrower - CA SFR',
    externalLoanId: 'LOS-2024-004',
    borrower: testBorrowers.prime,
    coBorrower: testBorrowers.coBorrower,
    loan: testLoans.wholesalePurchase,
    property: testProperties.californiaSFR,
    productPreference: 'CONV',
    effectiveDate: '2024-01-20',
  },
};

export const expectedPricingOutcomes: Record<string, {
  minRate: number;
  maxRate: number;
  minPrice: number;
  maxPrice: number;
  eligibleProducts: string[];
  ineligibleProducts: string[];
}> = {
  primePurchase: {
    minRate: 6.000,
    maxRate: 6.750,
    minPrice: 99.500,
    maxPrice: 101.000,
    eligibleProducts: ['CONV', 'FHA', 'VA'],
    ineligibleProducts: ['NON_QM'],
  },
  nearPrimeRefi: {
    minRate: 6.250,
    maxRate: 7.000,
    minPrice: 99.000,
    maxPrice: 100.500,
    eligibleProducts: ['CONV', 'FHA'],
    ineligibleProducts: ['VA', 'NON_QM'],
  },
  subPrimeCashOut: {
    minRate: 7.500,
    maxRate: 8.500,
    minPrice: 97.000,
    maxPrice: 99.000,
    eligibleProducts: ['NON_QM', 'FHA'],
    ineligibleProducts: ['CONV', 'VA'],
  },
};

export const pii25QuoteIntakeFixture = {
  quoteIntent: 'Purchase',
  channel: 'Retail',
  scenarioName: 'PII-25 E2E Prime Purchase',
  externalLoanId: 'PII25-E2E-001',
  borrowerName: 'Sarah Borrower',
  contactEmail: 'sarah.borrower@example.com',
  creditScore: '780',
  loanPurpose: 'Purchase',
  loanAmount: '400000',
  purchasePriceOrValue: '500000',
  propertyState: 'CA',
  propertyZip: '90210',
  propertyCounty: 'Los Angeles',
  monthlyIncome: '12500',
  monthlyDebt: '2800',
  liquidAssets: '75000',
  productFamily: 'Conventional',
  productPreference: 'CONV',
  effectiveDate: '2024-01-20',
};

export const pii25FunctionalityFixtures = {
  tenant: { workspaceName: 'PII-25 Tenant Workspace', contact: 'ops@example.com' },
  product: { productName: 'PII-25 Purchase Product', channel: 'Retail' },
  ratesheet: { fileName: 'pii25-rate-sheet.csv', investor: 'Investor mapping A' },
  pricing: { runId: 'e2e-run', scenario: 'Baseline scenario' },
  locks: { lockId: 'lock-e2e', borrowerRef: 'Borrower ref B' },
};

export type SarahMitchellWorkflowName =
  | 'pipeline-intake'
  | 'quick-quote'
  | 'quote-comparison'
  | 'request-lock'
  | 'lock-status'
  | 'lock-expiry'
  | 'lock-extension'
  | 'scenario-analysis'
  | 'what-if';

export const sarahMitchellWorkflowFixtureMetadata = {
  personaId: 'persona-loan-officer',
  personaName: 'Sarah Mitchell',
  classification: 'synthetic-test-only',
  sourceNote: 'Public local E2E fixture data. Values are deterministic test inputs, not production borrower data or mortgage pricing rules.',
  missingProductionIntegrations: [
    'Live LOS/LoanPASS borrower and pipeline import adapters are not production-backed in this fixture.',
    'Quote comparison responses are served by deterministic BFF/local mocks until full downstream quote-service integration is proven.',
    'Investor lock submission, lock expiry, and lock extension workflows are fixture-backed; no investor lock desk adapter is invoked.',
    'Scenario analysis and what-if responses use synthetic expected ranges only and must not be treated as pricing policy.',
  ],
  workflows: [
    'pipeline-intake',
    'quick-quote',
    'quote-comparison',
    'request-lock',
    'lock-status',
    'lock-expiry',
    'lock-extension',
    'scenario-analysis',
    'what-if',
  ] satisfies SarahMitchellWorkflowName[],
} as const;

export const sarahMitchellWorkflowFixtures = {
  metadata: sarahMitchellWorkflowFixtureMetadata,
  pipelineIntake: {
    ...pii25QuoteIntakeFixture,
    scenarioName: 'Synthetic Sarah Mitchell Pipeline Intake',
    externalLoanId: 'SARAH-SYNTH-PIPELINE-001',
    borrowerName: 'Sarah Synthetic Borrower',
    contactEmail: 'sarah.synthetic.borrower@example.com',
  },
  quickQuote: {
    quoteIntent: pii25QuoteIntakeFixture.quoteIntent,
    productPreference: pii25QuoteIntakeFixture.productPreference,
    minimalFields: ['borrowerName', 'externalLoanId', 'loanPurpose', 'loanAmount', 'purchasePriceOrValue', 'propertyState'],
    expectedStatus: 'READY_FOR_SYNTHETIC_QUOTE',
    fixtureOnlyNumericInputs: {
      creditScore: pii25QuoteIntakeFixture.creditScore,
      loanAmount: pii25QuoteIntakeFixture.loanAmount,
      purchasePriceOrValue: pii25QuoteIntakeFixture.purchasePriceOrValue,
    },
  },
  quoteComparison: {
    runId: 'sarah-synthetic-run-001',
    optionIds: ['sarah-synthetic-conv-30', 'sarah-synthetic-fha-30'],
    expectedEvidenceStatus: 'QUOTE_SERVICE_EVIDENCE_VISIBLE',
    expectedPricingRange: expectedPricingOutcomes.primePurchase,
    fixtureOnlyNote: 'Expected pricing range is inherited from synthetic primePurchase fixture and is not a production rate rule.',
  },
  lockWorkflow: {
    request: { lockId: 'sarah-synthetic-lock-001', optionId: 'sarah-synthetic-conv-30', lockPeriodDays: 30, requestedBy: 'Sarah Mitchell' },
    status: { state: 'REQUESTED', evidenceLabel: 'Synthetic lock request accepted by local mock' },
    expiry: { expiresOn: '2024-02-19', daysUntilExpiry: 30, evidenceLabel: 'Synthetic lock expiry date for deterministic E2E assertions' },
    extension: { extensionRequestId: 'sarah-synthetic-extension-001', extensionDays: 15, status: 'PENDING_FIXTURE_REVIEW' },
  },
  scenarioAnalysis: {
    scenarioId: 'sarah-synthetic-scenario-001',
    sourceScenario: testScenarios.primePurchase.scenarioName,
    analysisMode: 'fixture-backed-local-preview',
    expectedProducts: expectedPricingOutcomes.primePurchase.eligibleProducts,
  },
  whatIf: {
    workspaceId: 'sarah-synthetic-what-if-001',
    scenarios: ['fico-sensitivity', 'ltv-sensitivity', 'product-comparison', 'lock-period-comparison'],
    status: 'SYNTHETIC_FIXTURE_ONLY',
    policyNote: 'Do not infer production pricing adjustments from these local what-if labels or fixture ranges.',
  },
} as const;
