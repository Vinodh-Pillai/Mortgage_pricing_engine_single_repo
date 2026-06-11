import { describe, expect, it } from 'vitest';
import { QueryClient } from '@tanstack/react-query';
import { createOptimisticUpdate } from './optimistic';

describe('OptimisticTest', () => {
  it('rollbackOnError', async () => {
    const client = new QueryClient();
    const key = ['quoteRuns', 'offers', 'tenant-a', 'run-1'];
    client.setQueryData(key, { selectedOfferId: null });
    const update = createOptimisticUpdate(client, key, (previous: { selectedOfferId: string | null } | undefined) => ({ ...previous, selectedOfferId: 'offer-1' }));
    const mutationContext = { client, meta: undefined } as Parameters<NonNullable<typeof update.onMutate>>[1];

    const context = await update.onMutate?.({}, mutationContext);
    update.onError?.(new Error('failed'), {}, context, mutationContext);

    expect(client.getQueryData(key)).toEqual({ selectedOfferId: null });
  });

  it('updatesCacheOnSuccess', async () => {
    const client = new QueryClient();
    const key = ['quoteRuns', 'offers', 'tenant-a', 'run-1'];
    const update = createOptimisticUpdate(client, key, () => ({ selectedOfferId: 'offer-1' }));
    const mutationContext = { client, meta: undefined } as Parameters<NonNullable<typeof update.onMutate>>[1];

    await update.onMutate?.({}, mutationContext);

    expect(client.getQueryData(key)).toEqual({ selectedOfferId: 'offer-1' });
  });
});
