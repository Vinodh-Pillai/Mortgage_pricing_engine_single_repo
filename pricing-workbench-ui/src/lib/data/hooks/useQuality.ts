import { fetchQualityDashboard, requestQualityEvidenceExport } from '../../api/quality';
import { queryKeys } from '../queryKeys';
import { useTypedInfiniteQuery, useTypedMutation, useTypedQuery } from './common';

export function useQualityDashboardQuery() {
  return useTypedQuery(queryKeys.quality.dashboard(), () => fetchQualityDashboard(), { staleTime: 300_000 });
}

export function useQualityEvidenceExportMutation() {
  return useTypedMutation(() => requestQualityEvidenceExport());
}

export function useQualityMutation<TData, TVariables>(mutationFn: (variables: TVariables) => Promise<TData>) {
  return useTypedMutation(mutationFn);
}

export function useQualityInfiniteQuery<TPage, TPageParam>(key: readonly unknown[], queryFn: (pageParam: TPageParam) => Promise<TPage>, initialPageParam: TPageParam) {
  return useTypedInfiniteQuery(key, queryFn, initialPageParam, { getNextPageParam: () => undefined });
}
