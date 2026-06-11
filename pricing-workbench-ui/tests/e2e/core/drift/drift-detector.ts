export interface BaselineExpectation {
  scenario: string;
  persona: string;
  version: string;
  capturedAt: string;
  expectations: {
    apiResponses: Record<string, ApiExpectation>;
    uiStates: Record<string, UiExpectation>;
    pricingOutcomes: PricingExpectation[];
    eligibilityDecisions: EligibilityExpectation[];
    marginCalculations: MarginExpectation[];
    latencyBudgets: LatencyExpectation[];
  };
}

export interface ApiExpectation {
  endpoint: string;
  expectedStatus: number;
  expectedFields: string[];
  fieldConstraints: Record<string, FieldConstraint>;
  responseTimeMs: { max: number; p95: number };
}

export interface FieldConstraint {
  type: 'string' | 'number' | 'boolean' | 'array' | 'object';
  required: boolean;
  min?: number;
  max?: number;
  pattern?: string;
  enum?: string[];
}

export interface UiExpectation {
  route: string;
  expectedElements: string[];
  forbiddenElements: string[];
  loadTimeMs: { max: number };
  accessibilityScore: number;
}

export interface PricingExpectation {
  scenario: string;
  persona: string;
  inputs: Record<string, any>;
  expected: {
    rateRange: { min: number; max: number };
    priceRange: { min: number; max: number };
    paymentRange: { min: number; max: number };
    aprRange: { min: number; max: number };
    eligibleProducts: string[];
    ineligibleProducts: string[];
    adjustmentCategories: string[];
  };
  tolerance: {
    rateBps: number;
    priceBps: number;
  };
}

export interface EligibilityExpectation {
  scenario: string;
  persona: string;
  inputs: Record<string, any>;
  expectedDecision: 'ELIGIBLE' | 'INELIGIBLE' | 'CONDITIONAL';
  expectedBlockers: string[];
  requiredFacts: string[];
}

export interface MarginExpectation {
  scenario: string;
  persona: string;
  expected: {
    companyMarginBps: { min: number; max: number };
    branchMarginBps: { min: number; max: number };
    loMarginBps: { min: number; max: number };
    floorPass: boolean;
  };
}

export interface LatencyExpectation {
  operation: string;
  p50Ms: number;
  p95Ms: number;
  p99Ms: number;
}

export interface DriftFinding {
  category: 'api' | 'ui' | 'pricing' | 'eligibility' | 'margin' | 'latency';
  severity: 'INFO' | 'WARNING' | 'CRITICAL';
  description: string;
  expected: any;
  actual: any;
  baselineVersion: string;
  currentVersion: string;
  recommendation: string;
}

export interface DriftReport {
  baselineVersion: string;
  currentVersion: string;
  timestamp: string;
  driftDetected: boolean;
  overallSeverity: 'NONE' | 'INFO' | 'WARNING' | 'CRITICAL';
  findings: DriftFinding[];
  summary: {
    totalFindings: number;
    criticalCount: number;
    warningCount: number;
    infoCount: number;
  };
}

export class DriftDetector {
  private baselines: Map<string, BaselineExpectation> = new Map();

  loadBaseline(baseline: BaselineExpectation): void {
    const key = `${baseline.scenario}-${baseline.persona}`;
    this.baselines.set(key, baseline);
  }

  loadBaselinesFromDir(dir: string): void {
    // Implementation would read JSON files from baselines directory
    console.log(`Loading baselines from ${dir}`);
  }

  async compareApiResponse(
    scenario: string,
    persona: string,
    endpoint: string,
    actualResponse: any,
    actualStatus: number,
    actualResponseTime: number
  ): Promise<DriftFinding[]> {
    const key = `${scenario}-${persona}`;
    const baseline = this.baselines.get(key);
    const findings: DriftFinding[] = [];

    if (!baseline || !baseline.expectations.apiResponses[endpoint]) {
      return findings;
    }

    const expected = baseline.expectations.apiResponses[endpoint];

    // Check status
    if (actualStatus !== expected.expectedStatus) {
      findings.push({
        category: 'api',
        severity: 'CRITICAL',
        description: `Status code mismatch for ${endpoint}`,
        expected: expected.expectedStatus,
        actual: actualStatus,
        baselineVersion: baseline.version,
        currentVersion: 'current',
        recommendation: 'Investigate API contract change',
      });
    }

    // Check required fields
    for (const field of expected.expectedFields) {
      if (!(field in actualResponse)) {
        findings.push({
          category: 'api',
          severity: 'WARNING',
          description: `Missing expected field: ${field} in ${endpoint}`,
          expected: `field ${field} present`,
          actual: 'field missing',
          baselineVersion: baseline.version,
          currentVersion: 'current',
          recommendation: 'Verify API response schema',
        });
      }
    }

    // Check field constraints
    for (const [field, constraint] of Object.entries(expected.fieldConstraints)) {
      if (field in actualResponse) {
        const value = actualResponse[field];
        const constraintFinding = this.checkFieldConstraint(field, value, constraint, baseline.version);
        if (constraintFinding) findings.push(constraintFinding);
      }
    }

    // Check response time
    if (actualResponseTime > expected.responseTimeMs.max) {
      findings.push({
        category: 'latency',
        severity: actualResponseTime > expected.responseTimeMs.p95 ? 'WARNING' : 'INFO',
        description: `Response time exceeded budget for ${endpoint}`,
        expected: `<= ${expected.responseTimeMs.max}ms`,
        actual: `${actualResponseTime}ms`,
        baselineVersion: baseline.version,
        currentVersion: 'current',
        recommendation: 'Investigate performance regression',
      });
    }

    return findings;
  }

  private checkFieldConstraint(
    field: string,
    value: any,
    constraint: FieldConstraint,
    baselineVersion: string
  ): DriftFinding | null {
    if (constraint.required && (value === null || value === undefined)) {
      return {
        category: 'api',
        severity: 'CRITICAL',
        description: `Required field ${field} is null/undefined`,
        expected: 'non-null value',
        actual: value,
        baselineVersion,
        currentVersion: 'current',
        recommendation: 'Field should not be nullable',
      };
    }

    if (typeof constraint.min === 'number' && typeof value === 'number' && value < constraint.min) {
      return {
        category: 'api',
        severity: 'WARNING',
        description: `Field ${field} below minimum`,
        expected: `>= ${constraint.min}`,
        actual: value,
        baselineVersion,
        currentVersion: 'current',
        recommendation: 'Check business rule validation',
      };
    }

    if (typeof constraint.max === 'number' && typeof value === 'number' && value > constraint.max) {
      return {
        category: 'api',
        severity: 'WARNING',
        description: `Field ${field} above maximum`,
        expected: `<= ${constraint.max}`,
        actual: value,
        baselineVersion,
        currentVersion: 'current',
        recommendation: 'Check business rule validation',
      };
    }

    if (constraint.enum && !constraint.enum.includes(value)) {
      return {
        category: 'api',
        severity: 'WARNING',
        description: `Field ${field} has unexpected enum value`,
        expected: `one of [${constraint.enum.join(', ')}]`,
        actual: value,
        baselineVersion,
        currentVersion: 'current',
        recommendation: 'Verify enum values match contract',
      };
    }

    return null;
  }

  async comparePricingOutcome(
    scenario: string,
    persona: string,
    inputs: Record<string, any>,
    actualOutcome: {
      rate: number;
      price: number;
      payment: number;
      apr: number;
      eligibleProducts: string[];
      ineligibleProducts: string[];
      adjustments: Array<{ category: string; bps: number }>;
    }
  ): Promise<DriftFinding[]> {
    const key = `${scenario}-${persona}`;
    const baseline = this.baselines.get(key);
    const findings: DriftFinding[] = [];

    if (!baseline) return findings;

    const pricingExpectation = baseline.expectations.pricingOutcomes.find(
      p => p.scenario === scenario
    );

    if (!pricingExpectation) return findings;

    const tolerance = pricingExpectation.tolerance;

    // Check rate
    const expectedRateMin = pricingExpectation.expected.rateRange.min;
    const expectedRateMax = pricingExpectation.expected.rateRange.max;
    if (actualOutcome.rate < expectedRateMin - tolerance.rateBps / 10000 ||
        actualOutcome.rate > expectedRateMax + tolerance.rateBps / 10000) {
      findings.push({
        category: 'pricing',
        severity: 'CRITICAL',
        description: `Rate outside expected range for ${scenario}`,
        expected: `${expectedRateMin} - ${expectedRateMax}`,
        actual: actualOutcome.rate,
        baselineVersion: baseline.version,
        currentVersion: 'current',
        recommendation: 'Investigate pricing engine changes',
      });
    }

    // Check price
    const expectedPriceMin = pricingExpectation.expected.priceRange.min;
    const expectedPriceMax = pricingExpectation.expected.priceRange.max;
    if (actualOutcome.price < expectedPriceMin - tolerance.priceBps / 100 ||
        actualOutcome.price > expectedPriceMax + tolerance.priceBps / 100) {
      findings.push({
        category: 'pricing',
        severity: 'CRITICAL',
        description: `Price outside expected range for ${scenario}`,
        expected: `${expectedPriceMin} - ${expectedPriceMax}`,
        actual: actualOutcome.price,
        baselineVersion: baseline.version,
        currentVersion: 'current',
        recommendation: 'Investigate pricing engine changes',
      });
    }

    // Check eligible products
    const expectedEligible = new Set(pricingExpectation.expected.eligibleProducts);
    const actualEligible = new Set(actualOutcome.eligibleProducts);
    for (const product of expectedEligible) {
      if (!actualEligible.has(product)) {
        findings.push({
          category: 'pricing',
          severity: 'WARNING',
          description: `Expected eligible product ${product} not in results`,
          expected: `includes ${product}`,
          actual: `missing ${product}`,
          baselineVersion: baseline.version,
          currentVersion: 'current',
          recommendation: 'Verify product eligibility rules',
        });
      }
    }

    // Check ineligible products
    const expectedIneligible = new Set(pricingExpectation.expected.ineligibleProducts);
    const actualIneligible = new Set(actualOutcome.ineligibleProducts);
    for (const product of expectedIneligible) {
      if (!actualIneligible.has(product)) {
        findings.push({
          category: 'pricing',
          severity: 'WARNING',
          description: `Expected ineligible product ${product} appears eligible`,
          expected: `excludes ${product}`,
          actual: `includes ${product}`,
          baselineVersion: baseline.version,
          currentVersion: 'current',
          recommendation: 'Verify product eligibility rules',
        });
      }
    }

    return findings;
  }

  async compareEligibility(
    scenario: string,
    persona: string,
    inputs: Record<string, any>,
    actualDecision: string,
    actualBlockers: string[]
  ): Promise<DriftFinding[]> {
    const key = `${scenario}-${persona}`;
    const baseline = this.baselines.get(key);
    const findings: DriftFinding[] = [];

    if (!baseline) return findings;

    const eligExpectation = baseline.expectations.eligibilityDecisions.find(
      e => e.scenario === scenario
    );

    if (!eligExpectation) return findings;

    if (actualDecision !== eligExpectation.expectedDecision) {
      findings.push({
        category: 'eligibility',
        severity: 'CRITICAL',
        description: `Eligibility decision mismatch for ${scenario}`,
        expected: eligExpectation.expectedDecision,
        actual: actualDecision,
        baselineVersion: baseline.version,
        currentVersion: 'current',
        recommendation: 'Investigate eligibility rule changes',
      });
    }

    for (const blocker of eligExpectation.expectedBlockers) {
      if (!actualBlockers.includes(blocker)) {
        findings.push({
          category: 'eligibility',
          severity: 'WARNING',
          description: `Expected blocker ${blocker} not present`,
          expected: `includes ${blocker}`,
          actual: 'blocker missing',
          baselineVersion: baseline.version,
          currentVersion: 'current',
          recommendation: 'Verify eligibility rule evaluation',
        });
      }
    }

    return findings;
  }

  generateReport(findings: DriftFinding[], baselineVersion: string, currentVersion: string): DriftReport {
    const criticalCount = findings.filter(f => f.severity === 'CRITICAL').length;
    const warningCount = findings.filter(f => f.severity === 'WARNING').length;
    const infoCount = findings.filter(f => f.severity === 'INFO').length;

    let overallSeverity: DriftReport['overallSeverity'] = 'NONE';
    if (criticalCount > 0) overallSeverity = 'CRITICAL';
    else if (warningCount > 0) overallSeverity = 'WARNING';
    else if (infoCount > 0) overallSeverity = 'INFO';

    return {
      baselineVersion,
      currentVersion,
      timestamp: new Date().toISOString(),
      driftDetected: findings.length > 0,
      overallSeverity,
      findings,
      summary: {
        totalFindings: findings.length,
        criticalCount,
        warningCount,
        infoCount,
      },
    };
  }
}

export const driftDetector = new DriftDetector();