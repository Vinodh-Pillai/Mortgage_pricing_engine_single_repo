import { confirmLock } from '../../api/locks';
import { fetchOfferComparison, fetchQuoteDetail, selectOffer } from '../../api/offers';
import { fetchPricingWaterfall, fetchQuoteJourneyMap, fetchScenarioIntakeMetadata, launchQuoteRun, type BorrowerIntake } from '../../api/quoteRuns';
import { fetchScenarioAnalysisWorkspace, recalculateScenarioAnalysis, type ScenarioRecalculationRequest } from '../../api/scenarioAnalysis';
import { queryKeys } from '../queryKeys';
import { useTypedInfiniteQuery, useTypedMutation, useTypedQuery } from './common';

export function useQuoteRunIntakeMetadataQuery(tenantId: string) {
  return useTypedQuery(queryKeys.quoteRuns.intakeMetadata(tenantId), () => fetchScenarioIntakeMetadata(tenantId), { staleTime: 300_000 });
}

export function useQuoteRunOffersQuery(tenantId: string, runId: string) {
  return useTypedQuery(queryKeys.quoteRuns.offers(tenantId, runId), () => fetchOfferComparison(tenantId, runId), { staleTime: 30_000 });
}

export function useQuoteRunOfferDetailQuery(tenantId: string, runId: string, offerId: string) {
  return useTypedQuery(queryKeys.quoteRuns.offerDetail(tenantId, runId, offerId), () => fetchQuoteDetail(tenantId, runId, offerId), { staleTime: 60_000 });
}

export function usePricingWaterfallQuery(tenantId: string, runId: string) {
  return useTypedQuery(queryKeys.quoteRuns.pricingWaterfall(tenantId, runId), () => fetchPricingWaterfall(tenantId, runId), { staleTime: 60_000 });
}

export function useQuoteJourneyMapQuery(tenantId: string, runId: string) {
  return useTypedQuery(queryKeys.quoteRuns.journeyMap(tenantId, runId), () => fetchQuoteJourneyMap(tenantId, runId), { staleTime: 60_000 });
}

export function useScenarioAnalysisQuery(tenantId: string, runId: string) {
  return useTypedQuery(queryKeys.quoteRuns.whatIf(tenantId, runId), () => fetchScenarioAnalysisWorkspace(tenantId, runId), { staleTime: 30_000 });
}

export function useLaunchQuoteRunMutation(tenantId: string) {
  return useTypedMutation((intake: BorrowerIntake) => launchQuoteRun(tenantId, intake));
}

export function useSelectOfferMutation(tenantId: string, runId: string) {
  return useTypedMutation((variables: { offerId: string; sourceScenarioId?: string | null; scenarioVersion?: number | null; lockEligibilityRefs?: string[]; snapshotRefs?: string[]; auditIds?: string[] }) =>
    selectOffer(tenantId, runId, variables.offerId, variables.sourceScenarioId, variables.scenarioVersion, variables.lockEligibilityRefs, variables.snapshotRefs, variables.auditIds),
  );
}

export function useConfirmLockMutation(tenantId: string, runId: string) {
  return useTypedMutation((variables: { selectedOfferId: string; disclosuresAccepted: boolean }) => confirmLock(tenantId, runId, variables.selectedOfferId, variables.disclosuresAccepted));
}

export function useRecalculateScenarioMutation(tenantId: string, runId: string) {
  return useTypedMutation((request: ScenarioRecalculationRequest) => recalculateScenarioAnalysis(tenantId, runId, request));
}

export function useQuoteRunsQuery<TData>(key: readonly unknown[], queryFn: () => Promise<TData>) {
  return useTypedQuery(key, queryFn);
}

export function useQuoteRunsMutation<TData, TVariables>(mutationFn: (variables: TVariables) => Promise<TData>) {
  return useTypedMutation(mutationFn);
}

export function useQuoteRunsInfiniteQuery<TPage, TPageParam>(key: readonly unknown[], queryFn: (pageParam: TPageParam) => Promise<TPage>, initialPageParam: TPageParam) {
  return useTypedInfiniteQuery(key, queryFn, initialPageParam, { getNextPageParam: () => undefined });
}
