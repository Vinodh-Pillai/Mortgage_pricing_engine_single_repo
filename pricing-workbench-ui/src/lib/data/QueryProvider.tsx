import { QueryClient, QueryClientProvider, QueryErrorResetBoundary } from '@tanstack/react-query';
import { ReactQueryDevtools } from '@tanstack/react-query-devtools';
import { type ReactNode, useState } from 'react';
import { isRetryableError } from './errors';
import { stableQueryKeyHash } from './queryKeys';

export const DATA_LAYER_DEFAULTS = {
  staleTime: 30_000,
  gcTime: 5_000_000,
  retry: 2,
} as const;

export function createWorkbenchQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: DATA_LAYER_DEFAULTS.staleTime,
        gcTime: DATA_LAYER_DEFAULTS.gcTime,
        retry: (failureCount, error) => failureCount < DATA_LAYER_DEFAULTS.retry && isRetryableError(error),
        retryDelay: (attemptIndex) => Math.min(1_000 * 2 ** attemptIndex, 30_000),
        refetchOnWindowFocus: false,
        refetchOnReconnect: 'always',
        queryKeyHashFn: stableQueryKeyHash,
      },
      mutations: {
        retry: (failureCount, error) => failureCount < DATA_LAYER_DEFAULTS.retry && isRetryableError(error),
        retryDelay: (attemptIndex) => Math.min(1_000 * 2 ** attemptIndex, 30_000),
      },
    },
  });
}

export function QueryProvider({ children, client }: { children: ReactNode; client?: QueryClient }) {
  const [queryClient] = useState(() => client ?? createWorkbenchQueryClient());

  return (
    <QueryErrorResetBoundary>
      {() => (
        <QueryClientProvider client={queryClient}>
          {children}
          {import.meta.env.DEV ? <ReactQueryDevtools initialIsOpen={false} /> : null}
        </QueryClientProvider>
      )}
    </QueryErrorResetBoundary>
  );
}
