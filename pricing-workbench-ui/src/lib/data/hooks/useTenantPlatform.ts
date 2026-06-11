import { fetchTenantPlatformCoverage } from '../../api/tenantPlatform';
import { queryKeys } from '../queryKeys';
import { useTypedInfiniteQuery, useTypedMutation, useTypedQuery } from './common';

export function useTenantPlatformCoverageQuery() {
  return useTypedQuery(queryKeys.tenantPlatform.coverage(), () => fetchTenantPlatformCoverage(), { staleTime: 300_000 });
}

export function useTenantPlatformMutation<TData, TVariables>(mutationFn: (variables: TVariables) => Promise<TData>) {
  return useTypedMutation(mutationFn);
}

export function useTenantPlatformInfiniteQuery<TPage, TPageParam>(key: readonly unknown[], queryFn: (pageParam: TPageParam) => Promise<TPage>, initialPageParam: TPageParam) {
  return useTypedInfiniteQuery(key, queryFn, initialPageParam, { getNextPageParam: () => undefined });
}
