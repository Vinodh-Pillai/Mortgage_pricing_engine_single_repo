import { fetchComplianceEvidenceRegistry } from '../../api/complianceEvidence';
import { queryKeys } from '../queryKeys';
import { useTypedInfiniteQuery, useTypedMutation, useTypedQuery } from './common';

export function useComplianceEvidenceQuery() {
  return useTypedQuery(queryKeys.compliance.evidence(), () => fetchComplianceEvidenceRegistry(), { staleTime: 300_000 });
}

export function useComplianceMutation<TData, TVariables>(mutationFn: (variables: TVariables) => Promise<TData>) {
  return useTypedMutation(mutationFn);
}

export function useComplianceInfiniteQuery<TPage, TPageParam>(key: readonly unknown[], queryFn: (pageParam: TPageParam) => Promise<TPage>, initialPageParam: TPageParam) {
  return useTypedInfiniteQuery(key, queryFn, initialPageParam, { getNextPageParam: () => undefined });
}
