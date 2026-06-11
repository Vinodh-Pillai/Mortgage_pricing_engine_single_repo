export type PersonaRole = 
  | 'RETAIL_LO'
  | 'WHOLESALE_LO'
  | 'CORRESPONDENT_LO'
  | 'PRICING_ANALYST'
  | 'LOCK_DESK'
  | 'OPS_LEAD'
  | 'COMPLIANCE_OFFICER'
  | 'GOVERNANCE_REVIEWER'
  | 'CAPITAL_MARKETS'
  | 'ADMIN';

export interface Persona {
  id: PersonaRole;
  name: string;
  description: string;
  roles: string[];
  permissions: string[];
  defaultTenant: string;
  testScenarios: string[];
  accessibleRoutes: string[];
  restrictedRoutes: string[];
  expectedPricingBehavior: {
    marginProfile: 'standard' | 'wholesale' | 'correspondent' | 'analyst';
    expectedRateRange: { min: number; max: number };
    eligibleProducts: string[];
  };
}

export const personas: Record<PersonaRole, Persona> = {
  RETAIL_LO: {
    id: 'RETAIL_LO',
    name: 'Retail Loan Officer',
    description: 'Originates retail loans directly to consumers',
    roles: ['loan_officer', 'retail'],
    permissions: ['quote:create', 'quote:view', 'quote:lock', 'scenario:analyze'],
    defaultTenant: 'retail-tenant-001',
    testScenarios: ['primePurchase', 'nearPrimeRefi', 'wholesaleWithCoBorrower'],
    accessibleRoutes: [
      '/quote/start',
      '/quote/:runId/offers',
      '/quote/:runId/offers/:optionId',
      '/quote/:runId/lock',
      '/quote/:runId/eligibility',
      '/quote/:runId/what-if',
      '/quote/:runId/what-if/fico-sensitivity',
      '/quote/:runId/what-if/ltv-sensitivity',
      '/quote/:runId/what-if/product-comparison',
      '/quote/:runId/what-if/lock-period-comparison',
    ],
    restrictedRoutes: [
      '/pricing/adjustments',
      '/pricing/margins',
      '/ops/rate-feeds',
      '/ops/performance',
      '/admin/products/catalog',
      '/admin/governance',
      '/advisory/ml',
      '/advisory/ml/governance',
      '/advisory/ml/drift',
      '/exceptions/concessions',
      '/partners/quotes',
      '/partners/integrations',
      '/compliance/evidence',
    ],
    expectedPricingBehavior: {
      marginProfile: 'standard',
      expectedRateRange: { min: 6.0, max: 8.0 },
      eligibleProducts: ['CONV', 'FHA', 'VA', 'USDA'],
    },
  },
  WHOLESALE_LO: {
    id: 'WHOLESALE_LO',
    name: 'Wholesale Loan Officer',
    description: 'Works with mortgage brokers for wholesale lending',
    roles: ['loan_officer', 'wholesale'],
    permissions: ['quote:create', 'quote:view', 'quote:lock', 'partner:view', 'partner:reprice'],
    defaultTenant: 'wholesale-tenant-001',
    testScenarios: ['primePurchase', 'wholesaleWithCoBorrower'],
    accessibleRoutes: [
      '/quote/start',
      '/quote/:runId/offers',
      '/quote/:runId/offers/:optionId',
      '/quote/:runId/lock',
      '/partners/quotes',
      '/partners/integrations',
      '/quote/:runId/what-if',
    ],
    restrictedRoutes: [
      '/pricing/adjustments',
      '/pricing/margins',
      '/admin/governance',
      '/advisory/ml',
      '/exceptions/concessions',
      '/compliance/evidence',
    ],
    expectedPricingBehavior: {
      marginProfile: 'wholesale',
      expectedRateRange: { min: 5.875, max: 7.750 },
      eligibleProducts: ['CONV', 'FHA', 'VA', 'NON_QM'],
    },
  },
  CORRESPONDENT_LO: {
    id: 'CORRESPONDENT_LO',
    name: 'Correspondent Loan Officer',
    description: 'Manages correspondent lending relationships',
    roles: ['loan_officer', 'correspondent'],
    permissions: ['quote:create', 'quote:view', 'quote:lock', 'partner:view', 'partner:reprice', 'lock:manage'],
    defaultTenant: 'correspondent-tenant-001',
    testScenarios: ['primePurchase', 'nearPrimeRefi'],
    accessibleRoutes: [
      '/quote/start',
      '/quote/:runId/offers',
      '/quote/:runId/offers/:optionId',
      '/quote/:runId/lock',
      '/partners/quotes',
      '/partners/integrations',
      '/ops/dashboard',
    ],
    restrictedRoutes: [
      '/pricing/adjustments',
      '/pricing/margins',
      '/admin/governance',
      '/advisory/ml',
      '/exceptions/concessions',
    ],
    expectedPricingBehavior: {
      marginProfile: 'correspondent',
      expectedRateRange: { min: 5.750, max: 7.500 },
      eligibleProducts: ['CONV', 'FHA', 'VA', 'USDA'],
    },
  },
  PRICING_ANALYST: {
    id: 'PRICING_ANALYST',
    name: 'Pricing Analyst',
    description: 'Analyzes pricing waterfalls, adjustments, and margins',
    roles: ['pricing_analyst'],
    permissions: ['quote:view', 'pricing:waterfall', 'pricing:adjustments', 'pricing:margins', 'scenario:analyze', 'eligibility:view'],
    defaultTenant: 'pricing-tenant-001',
    testScenarios: ['primePurchase', 'nearPrimeRefi', 'subPrimeCashOut'],
    accessibleRoutes: [
      '/quote/:runId/offers',
      '/quote/:runId/offers/:optionId',
      '/quote/:runId/pricing-waterfall',
      '/pricing/adjustments',
      '/pricing/margins',
      '/quote/:runId/eligibility',
      '/quote/:runId/what-if',
      '/quote/:runId/what-if/fico-sensitivity',
      '/quote/:runId/what-if/ltv-sensitivity',
      '/ops/rate-feeds',
      '/ops/performance',
    ],
    restrictedRoutes: [
      '/quote/start',
      '/quote/:runId/lock',
      '/admin/governance',
      '/advisory/ml',
      '/exceptions/concessions',
      '/partners/quotes',
      '/compliance/evidence',
    ],
    expectedPricingBehavior: {
      marginProfile: 'analyst',
      expectedRateRange: { min: 5.5, max: 8.5 },
      eligibleProducts: ['CONV', 'FHA', 'VA', 'USDA', 'JUMBO', 'NON_QM'],
    },
  },
  LOCK_DESK: {
    id: 'LOCK_DESK',
    name: 'Lock Desk Operator',
    description: 'Manages rate locks, extensions, relocks, and float-downs',
    roles: ['lock_desk'],
    permissions: ['quote:view', 'lock:create', 'lock:extend', 'lock:relock', 'lock:floatdown', 'lock:view'],
    defaultTenant: 'lock-tenant-001',
    testScenarios: ['primePurchase', 'nearPrimeRefi'],
    accessibleRoutes: [
      '/quote/:runId/offers',
      '/quote/:runId/offers/:optionId',
      '/quote/:runId/lock',
      '/quote/:runId/pricing-waterfall',
      '/quote/:runId/what-if/lock-period-comparison',
    ],
    restrictedRoutes: [
      '/quote/start',
      '/pricing/adjustments',
      '/pricing/margins',
      '/admin/governance',
      '/advisory/ml',
      '/exceptions/concessions',
      '/partners/quotes',
      '/compliance/evidence',
    ],
    expectedPricingBehavior: {
      marginProfile: 'standard',
      expectedRateRange: { min: 6.0, max: 8.0 },
      eligibleProducts: ['CONV', 'FHA', 'VA', 'USDA'],
    },
  },
  OPS_LEAD: {
    id: 'OPS_LEAD',
    name: 'Operations Lead',
    description: 'Oversees rate feed operations, pipeline, and performance',
    roles: ['operations_lead'],
    permissions: ['ops:ratefeeds', 'ops:cases', 'ops:performance', 'quote:view', 'partner:view'],
    defaultTenant: 'ops-tenant-001',
    testScenarios: [],
    accessibleRoutes: [
      '/ops/rate-feeds',
      '/ops/performance',
      '/ops/dashboard',
      '/ops/cases',
      '/partners/integrations',
      '/quote/:runId/offers',
      '/quote/:runId/journey',
    ],
    restrictedRoutes: [
      '/quote/start',
      '/quote/:runId/lock',
      '/pricing/adjustments',
      '/pricing/margins',
      '/admin/governance',
      '/advisory/ml',
      '/exceptions/concessions',
      '/compliance/evidence',
    ],
    expectedPricingBehavior: {
      marginProfile: 'standard',
      expectedRateRange: { min: 0, max: 0 },
      eligibleProducts: [],
    },
  },
  COMPLIANCE_OFFICER: {
    id: 'COMPLIANCE_OFFICER',
    name: 'Compliance Officer',
    description: 'Reviews compliance evidence, fair lending, privacy, security',
    roles: ['compliance_officer'],
    permissions: ['compliance:evidence', 'compliance:fairlending', 'compliance:privacy', 'compliance:security', 'audit:replay'],
    defaultTenant: 'compliance-tenant-001',
    testScenarios: ['primePurchase', 'subPrimeCashOut'],
    accessibleRoutes: [
      '/compliance/evidence',
      '/privacy/requests',
      '/security/events',
      '/audit/replay',
      '/quote/:runId/offers/:optionId',
      '/quote/:runId/eligibility',
    ],
    restrictedRoutes: [
      '/quote/start',
      '/quote/:runId/lock',
      '/pricing/adjustments',
      '/pricing/margins',
      '/admin/governance',
      '/advisory/ml',
      '/exceptions/concessions',
      '/partners/quotes',
      '/ops/rate-feeds',
    ],
    expectedPricingBehavior: {
      marginProfile: 'standard',
      expectedRateRange: { min: 0, max: 0 },
      eligibleProducts: [],
    },
  },
  GOVERNANCE_REVIEWER: {
    id: 'GOVERNANCE_REVIEWER',
    name: 'Governance Reviewer',
    description: 'Manages config lifecycle, catalog, rules, and release governance',
    roles: ['governance_reviewer'],
    permissions: ['governance:config', 'governance:catalog', 'governance:rules', 'governance:release', 'admin:products'],
    defaultTenant: 'governance-tenant-001',
    testScenarios: [],
    accessibleRoutes: [
      '/admin/products/catalog',
      '/admin/governance',
      '/custom-rules',
      '/quote/:runId/offers/:optionId',
      '/pricing/adjustments',
      '/pricing/margins',
    ],
    restrictedRoutes: [
      '/quote/start',
      '/quote/:runId/lock',
      '/advisory/ml',
      '/exceptions/concessions',
      '/partners/quotes',
      '/ops/rate-feeds',
      '/compliance/evidence',
    ],
    expectedPricingBehavior: {
      marginProfile: 'analyst',
      expectedRateRange: { min: 0, max: 0 },
      eligibleProducts: [],
    },
  },
  CAPITAL_MARKETS: {
    id: 'CAPITAL_MARKETS',
    name: 'Capital Markets / ML Engineer',
    description: 'Manages ML advisory insights, model governance, drift monitoring',
    roles: ['capital_markets', 'ml_engineer'],
    permissions: ['ml:advisory', 'ml:governance', 'ml:drift', 'ml:feedback', 'pricing:waterfall'],
    defaultTenant: 'ml-tenant-001',
    testScenarios: ['primePurchase', 'nearPrimeRefi'],
    accessibleRoutes: [
      '/advisory/ml',
      '/advisory/ml/governance',
      '/advisory/ml/drift',
      '/quote/:runId/offers/:optionId',
      '/quote/:runId/pricing-waterfall',
      '/pricing/margins',
    ],
    restrictedRoutes: [
      '/quote/start',
      '/quote/:runId/lock',
      '/admin/governance',
      '/exceptions/concessions',
      '/partners/quotes',
      '/ops/rate-feeds',
      '/compliance/evidence',
    ],
    expectedPricingBehavior: {
      marginProfile: 'analyst',
      expectedRateRange: { min: 5.5, max: 8.5 },
      eligibleProducts: ['CONV', 'FHA', 'VA', 'USDA', 'JUMBO', 'NON_QM'],
    },
  },
  ADMIN: {
    id: 'ADMIN',
    name: 'System Administrator',
    description: 'Full system access for tenant setup and configuration',
    roles: ['admin'],
    permissions: ['*'],
    defaultTenant: 'admin-tenant-001',
    testScenarios: ['primePurchase', 'nearPrimeRefi', 'subPrimeCashOut', 'wholesaleWithCoBorrower'],
    accessibleRoutes: [
      '/quote/start',
      '/quote/:runId/offers',
      '/quote/:runId/offers/:optionId',
      '/quote/:runId/lock',
      '/quote/:runId/pricing-waterfall',
      '/quote/:runId/eligibility',
      '/quote/:runId/what-if',
      '/pricing/adjustments',
      '/pricing/margins',
      '/ops/rate-feeds',
      '/ops/performance',
      '/ops/dashboard',
      '/admin/products/catalog',
      '/admin/governance',
      '/custom-rules',
      '/advisory/ml',
      '/advisory/ml/governance',
      '/advisory/ml/drift',
      '/exceptions/concessions',
      '/partners/quotes',
      '/partners/integrations',
      '/compliance/evidence',
      '/privacy/requests',
      '/security/events',
      '/audit/replay',
    ],
    restrictedRoutes: [],
    expectedPricingBehavior: {
      marginProfile: 'analyst',
      expectedRateRange: { min: 0, max: 0 },
      eligibleProducts: ['CONV', 'FHA', 'VA', 'USDA', 'JUMBO', 'NON_QM'],
    },
  },
};

export function getPersona(role: PersonaRole): Persona {
  return personas[role];
}

export function getAccessibleRoutes(role: PersonaRole): string[] {
  return personas[role].accessibleRoutes;
}

export function getRestrictedRoutes(role: PersonaRole): string[] {
  return personas[role].restrictedRoutes;
}

export function getTestScenarios(role: PersonaRole): string[] {
  return personas[role].testScenarios;
}

export function getExpectedPricingBehavior(role: PersonaRole) {
  return personas[role].expectedPricingBehavior;
}