import type { QueryClient, QueryKey } from '@tanstack/react-query';
import { queryKeys, queryKeyIncludesTenant } from './queryKeys';

export function invalidateQuoteRun(queryClient: QueryClient, tenantId: string, runId: string) {
  return queryClient.invalidateQueries({
    predicate: (query) => queryKeyIncludesTenant(query.queryKey, tenantId) && query.queryKey.includes(runId),
  });
}

export function invalidatePartner(queryClient: QueryClient, partnerId: string) {
  return queryClient.invalidateQueries({
    predicate: (query) => query.queryKey[0] === 'partner' && query.queryKey.includes(partnerId),
  });
}

export function invalidateTenant(queryClient: QueryClient, tenantId: string) {
  return queryClient.invalidateQueries({
    predicate: (query) => queryKeyIncludesTenant(query.queryKey, tenantId),
  });
}

export function invalidateByKey(queryClient: QueryClient, queryKey: QueryKey) {
  return queryClient.invalidateQueries({ queryKey });
}

export function invalidateByTag(queryClient: QueryClient, tag: QueryKey[number]) {
  return queryClient.invalidateQueries({ predicate: (query) => query.queryKey.includes(tag) });
}

export function invalidateAll(queryClient: QueryClient) {
  return queryClient.invalidateQueries();
}

export const invalidationKeys = queryKeys;
