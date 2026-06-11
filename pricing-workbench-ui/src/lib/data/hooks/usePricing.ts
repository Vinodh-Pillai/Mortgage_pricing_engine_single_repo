import { fetchAdjustmentEvidence } from '../../api/adjustments';
import { fetchMarginProfitability } from '../../api/marginProfitability';
import { queryKeys } from '../queryKeys';
import { useTypedInfiniteQuery, useTypedMutation, useTypedQuery } from './common';

export function useAdjustmentsQuery(tenantContext: string) {
  return useTypedQuery(queryKeys.pricing.adjustments(tenantContext), () => fetchAdjustmentEvidence(tenantContext), { staleTime: 60_000 });
}

export function useMarginsQuery(tenantContext: string) {
  return useTypedQuery(queryKeys.pricing.margins(tenantContext), () => fetchMarginProfitability(tenantContext), { staleTime: 60_000 });
}

export function usePricingMutation<TData, TVariables>(mutationFn: (variables: TVariables) => Promise<TData>) {
  return useTypedMutation(mutationFn);
}

export function usePricingInfiniteQuery<TPage, TPageParam>(key: readonly unknown[], queryFn: (pageParam: TPageParam) => Promise<TPage>, initialPageParam: TPageParam) {
  return useTypedInfiniteQuery(key, queryFn, initialPageParam, { getNextPageParam: () => undefined });
}
