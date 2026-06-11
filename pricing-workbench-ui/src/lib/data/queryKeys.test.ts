import { describe, expect, it } from 'vitest';
import { queryKeys, stableQueryKeyHash } from './queryKeys';

describe('QueryKeysTest', () => {
  it('quoteRunKeysAreStable', () => {
    expect(queryKeys.quoteRuns.status('tenant-a', 'run-1')).toEqual(['quoteRuns', 'status', 'tenant-a', 'run-1']);
    expect(stableQueryKeyHash(['a', { b: 1, a: 2 }])).toBe(stableQueryKeyHash(['a', { a: 2, b: 1 }]));
  });

  it('partnerKeysIncludePartnerId', () => {
    expect(queryKeys.partner.quotes('partner-1', 'open')).toEqual(['partner', 'quotes', 'partner-1', 'open']);
  });

  it('opsKeysAreNamespaced', () => {
    expect(queryKeys.ops.rateFeeds()[0]).toBe('ops');
  });

  it('tenantIdScopedInQueryKeys', () => {
    expect(queryKeys.quoteRuns.offers('tenant-a', 'run-1')).toContain('tenant-a');
  });
});
