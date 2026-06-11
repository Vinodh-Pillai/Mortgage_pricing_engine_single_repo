import { fetchMlAdvisoryInsights } from '../../api/mlAdvisoryInsights';
import { queryKeys } from '../queryKeys';
import { useTypedInfiniteQuery, useTypedMutation, useTypedQuery } from './common';

export function useMlAdvisoryInsightsQuery() {
  return useTypedQuery(queryKeys.mlAdvisory.insights(), () => fetchMlAdvisoryInsights(), { staleTime: 300_000 });
}

export function useMlAdvisoryMutation<TData, TVariables>(mutationFn: (variables: TVariables) => Promise<TData>) {
  return useTypedMutation(mutationFn);
}

export function useMlAdvisoryInfiniteQuery<TPage, TPageParam>(key: readonly unknown[], queryFn: (pageParam: TPageParam) => Promise<TPage>, initialPageParam: TPageParam) {
  return useTypedInfiniteQuery(key, queryFn, initialPageParam, { getNextPageParam: () => undefined });
}
