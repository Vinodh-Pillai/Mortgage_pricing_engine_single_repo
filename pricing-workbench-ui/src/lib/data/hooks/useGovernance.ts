import { fetchAdminGovernance } from '../../api/adminGovernance';
import { fetchAuditReplayWorkbench } from '../../api/auditReplay';
import { fetchCustomRuleEvidence } from '../../api/customRules';
import { fetchProductCatalogManager } from '../../api/products';
import { queryKeys } from '../queryKeys';
import { useTypedInfiniteQuery, useTypedMutation, useTypedQuery } from './common';

export function useAdminGovernanceQuery() {
  return useTypedQuery(queryKeys.governance.admin(), () => fetchAdminGovernance(), { staleTime: 300_000 });
}

export function useCatalogQuery() {
  return useTypedQuery(queryKeys.governance.catalog(), () => fetchProductCatalogManager(), { staleTime: 300_000 });
}

export function useCustomRulesQuery() {
  return useTypedQuery(queryKeys.governance.customRules(), () => fetchCustomRuleEvidence(), { staleTime: 300_000 });
}

export function useAuditReplayQuery() {
  return useTypedQuery(queryKeys.governance.auditReplay(), () => fetchAuditReplayWorkbench(), { staleTime: 300_000 });
}

export function useGovernanceMutation<TData, TVariables>(mutationFn: (variables: TVariables) => Promise<TData>) {
  return useTypedMutation(mutationFn);
}

export function useGovernanceInfiniteQuery<TPage, TPageParam>(key: readonly unknown[], queryFn: (pageParam: TPageParam) => Promise<TPage>, initialPageParam: TPageParam) {
  return useTypedInfiniteQuery(key, queryFn, initialPageParam, { getNextPageParam: () => undefined });
}
