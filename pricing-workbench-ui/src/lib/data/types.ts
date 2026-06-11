import type { UseMutationResult, UseQueryResult, UseInfiniteQueryResult } from '@tanstack/react-query';

export type QueryResult<TData, TError = Error> = UseQueryResult<TData, TError>;
export type MutationResult<TData, TVariables, TError = Error> = UseMutationResult<TData, TError, TVariables>;
export type InfiniteQueryResult<TData, TError = Error> = UseInfiniteQueryResult<TData, TError>;

export * from '../api/adminGovernance';
export * from '../api/adjustments';
export * from '../api/auditReplay';
export * from '../api/complianceEvidence';
export * from '../api/customRules';
export * from '../api/eligibility';
export * from '../api/exceptionConcessions';
export * from '../api/locks';
export * from '../api/marginProfitability';
export * from '../api/mlAdvisoryInsights';
export * from '../api/observabilityPerformance';
export * from '../api/offers';
export * from '../api/opsCases';
export * from '../api/partnerQuotes';
export * from '../api/partnerTransport';
export * from '../api/products';
export * from '../api/quality';
export * from '../api/quoteRuns';
export * from '../api/rateFeedOps';
export * from '../api/scenarioAnalysis';
export * from '../api/tenantPlatform';
export * from '../api/tenants';
export * from '../api/uiHealth';
