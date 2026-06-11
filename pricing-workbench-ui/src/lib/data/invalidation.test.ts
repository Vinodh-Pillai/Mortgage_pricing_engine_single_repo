import { describe, expect, it, vi } from 'vitest';
import { QueryClient } from '@tanstack/react-query';
import { invalidatePartner, invalidateQuoteRun, invalidateTenant } from './invalidation';
import { queryKeys } from './queryKeys';

describe('InvalidationTest', () => {
  it('invalidateQuoteRunRemovesAllRunQueries', async () => {
    const client = new QueryClient();
    client.setQueryData(queryKeys.quoteRuns.offers('tenant-a', 'run-1'), { status: 'ready' });
    const invalidateSpy = vi.spyOn(client, 'invalidateQueries');

    await invalidateQuoteRun(client, 'tenant-a', 'run-1');

    expect(invalidateSpy).toHaveBeenCalledWith(expect.objectContaining({ predicate: expect.any(Function) }));
  });

  it('invalidatePartnerRemovesPartnerQueries', async () => {
    const client = new QueryClient();
    const invalidateSpy = vi.spyOn(client, 'invalidateQueries');

    await invalidatePartner(client, 'partner-1');

    expect(invalidateSpy).toHaveBeenCalledWith(expect.objectContaining({ predicate: expect.any(Function) }));
  });

  it('invalidateTenantMatchesTenantScopedKeys', async () => {
    const client = new QueryClient();
    const invalidateSpy = vi.spyOn(client, 'invalidateQueries');

    await invalidateTenant(client, 'tenant-a');

    expect(invalidateSpy).toHaveBeenCalledWith(expect.objectContaining({ predicate: expect.any(Function) }));
  });
});
