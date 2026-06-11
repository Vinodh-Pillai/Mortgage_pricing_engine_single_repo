import { useInfiniteQuery, useMutation, useQuery, type QueryKey, type UseInfiniteQueryOptions, type UseMutationOptions, type UseQueryOptions } from '@tanstack/react-query';

export function useTypedQuery<TData, TError = Error>(
  queryKey: QueryKey,
  queryFn: () => Promise<TData>,
  options?: Omit<UseQueryOptions<TData, TError, TData>, 'queryKey' | 'queryFn'>,
) {
  return useQuery<TData, TError, TData>({ queryKey, queryFn, ...options });
}

export function useTypedMutation<TData, TVariables, TError = Error>(
  mutationFn: (variables: TVariables) => Promise<TData>,
  options?: Omit<UseMutationOptions<TData, TError, TVariables>, 'mutationFn'>,
) {
  return useMutation<TData, TError, TVariables>({ mutationFn, ...options });
}

export function useTypedInfiniteQuery<TPage, TError = Error, TPageParam = unknown>(
  queryKey: QueryKey,
  queryFn: (pageParam: TPageParam) => Promise<TPage>,
  initialPageParam: TPageParam,
  options: Omit<UseInfiniteQueryOptions<TPage, TError, TPage, QueryKey, TPageParam>, 'queryKey' | 'queryFn' | 'initialPageParam'>,
) {
  return useInfiniteQuery<TPage, TError, TPage, QueryKey, TPageParam>({
    queryKey,
    queryFn: ({ pageParam }) => queryFn(pageParam as TPageParam),
    initialPageParam,
    ...options,
  });
}
