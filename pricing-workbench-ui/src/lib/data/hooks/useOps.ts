import { fetchPerformanceDashboard } from '../../api/observabilityPerformance';
import { addOpsCaseNote, assignOpsCase, fetchOpsCaseDetail, fetchOpsCases, updateOpsCaseStatus } from '../../api/opsCases';
import { fetchRateFeedOperations } from '../../api/rateFeedOps';
import { queryKeys } from '../queryKeys';
import { useTypedInfiniteQuery, useTypedMutation, useTypedQuery } from './common';

export function useRateFeedsQuery() {
  return useTypedQuery(queryKeys.ops.rateFeeds(), () => fetchRateFeedOperations(), { staleTime: 30_000, refetchInterval: 30_000 });
}

export function usePerformanceQuery() {
  return useTypedQuery(queryKeys.ops.performance(), () => fetchPerformanceDashboard(), { staleTime: 30_000, refetchInterval: 30_000 });
}

export function useOpsCasesQuery() {
  return useTypedQuery(queryKeys.ops.cases(), () => fetchOpsCases());
}

export function useOpsCaseDetailQuery(caseId: string) {
  return useTypedQuery(queryKeys.ops.caseDetail(caseId), () => fetchOpsCaseDetail(caseId));
}

export function useOpsMutation<TData, TVariables>(mutationFn: (variables: TVariables) => Promise<TData>) {
  return useTypedMutation(mutationFn);
}

export function useAssignOpsCaseMutation() {
  return useTypedMutation((variables: { caseId: string; owner: string }) => assignOpsCase(variables.caseId, variables.owner));
}

export function useAddOpsCaseNoteMutation() {
  return useTypedMutation((variables: { caseId: string; note: string }) => addOpsCaseNote(variables.caseId, variables.note));
}

export function useUpdateOpsCaseStatusMutation() {
  return useTypedMutation((variables: { caseId: string; status: string; reason: string; resolutionCode: string }) =>
    updateOpsCaseStatus(variables.caseId, variables.status, variables.reason, variables.resolutionCode),
  );
}

export function useOpsInfiniteQuery<TPage, TPageParam>(key: readonly unknown[], queryFn: (pageParam: TPageParam) => Promise<TPage>, initialPageParam: TPageParam) {
  return useTypedInfiniteQuery(key, queryFn, initialPageParam, { getNextPageParam: () => undefined });
}
