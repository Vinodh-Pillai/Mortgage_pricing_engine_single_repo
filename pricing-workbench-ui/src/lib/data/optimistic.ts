import type { QueryClient, QueryKey, UseMutationOptions } from '@tanstack/react-query';

export type OptimisticUpdateContext<TPrevious> = {
  previousData: TPrevious | undefined;
};

export function createOptimisticUpdate<TData, TError, TVariables, TPrevious = TData>(
  queryClient: QueryClient,
  queryKey: QueryKey,
  update: (previous: TPrevious | undefined, variables: TVariables) => TPrevious,
): Pick<UseMutationOptions<TData, TError, TVariables, OptimisticUpdateContext<TPrevious>>, 'onMutate' | 'onError' | 'onSettled'> {
  return {
    onMutate: async (variables) => {
      await queryClient.cancelQueries({ queryKey });
      const previousData = queryClient.getQueryData<TPrevious>(queryKey);
      queryClient.setQueryData<TPrevious>(queryKey, update(previousData, variables));
      return { previousData };
    },
    onError: (_error, _variables, context) => {
      queryClient.setQueryData(queryKey, context?.previousData);
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey });
    },
  };
}

export function optimisticUpdate<TData, TError, TVariables, TPrevious = TData>(
  mutationFn: (variables: TVariables) => Promise<TData>,
  options: Pick<UseMutationOptions<TData, TError, TVariables, OptimisticUpdateContext<TPrevious>>, 'onMutate' | 'onError' | 'onSettled'>,
): UseMutationOptions<TData, TError, TVariables, OptimisticUpdateContext<TPrevious>> {
  return { mutationFn, ...options };
}
