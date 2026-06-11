import { render, renderHook } from '@testing-library/react';
import { QueryClient } from '@tanstack/react-query';
import { describe, expect, it, vi } from 'vitest';
import { QueryProvider, createWorkbenchQueryClient } from './QueryProvider';
import { TenantContextProvider, useTenantId } from './tenant';

describe('TenantTest', () => {
  it('tenantIdScopedInQueryKeys', () => {
    const wrapper = ({ children }: { children: React.ReactNode }) => (
      <QueryProvider>
        <TenantContextProvider tenantId="tenant-a">{children}</TenantContextProvider>
      </QueryProvider>
    );

    const { result } = renderHook(() => useTenantId(), { wrapper });

    expect(result.current).toBe('tenant-a');
  });

  it('usesUrlTenantWhenProviderIsMissing', () => {
    window.history.pushState({}, '', '?tenantId=tenant-from-url');

    const { result } = renderHook(() => useTenantId());

    expect(result.current).toBe('tenant-from-url');
  });

  it('throwsWhenTenantSourceIsMissing', () => {
    window.history.pushState({}, '', '/');

    expect(() => renderHook(() => useTenantId())).toThrow('Tenant id is required');
  });

  it('invalidatesPreviousTenantWhenTenantChanges', () => {
    const client = createWorkbenchQueryClient();
    const invalidateSpy = vi.spyOn(client, 'invalidateQueries');

    const { rerender } = render(
      <QueryProvider client={client}>
        <TenantContextProvider tenantId="tenant-a">tenant</TenantContextProvider>
      </QueryProvider>,
    );

    expect(invalidateSpy).not.toHaveBeenCalled();

    rerender(
      <QueryProvider client={client}>
        <TenantContextProvider tenantId="tenant-b">tenant</TenantContextProvider>
      </QueryProvider>,
    );

    const predicate = invalidateSpy.mock.calls[0]?.[0]?.predicate;
    expect(predicate).toBeDefined();
    expect(
      predicate?.({ queryKey: ['quoteRuns', 'offers', 'tenant-a'] } as unknown as Parameters<NonNullable<typeof predicate>>[0]),
    ).toBe(true);
    expect(
      predicate?.({ queryKey: ['quoteRuns', 'offers', 'tenant-b'] } as unknown as Parameters<NonNullable<typeof predicate>>[0]),
    ).toBe(false);
  });
});
