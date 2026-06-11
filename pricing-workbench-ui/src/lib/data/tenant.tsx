import { createContext, type ReactNode, useContext, useEffect, useRef } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { invalidateTenant } from './invalidation';

const TenantContext = createContext<string | null>(null);

export function TenantContextProvider({ tenantId, children }: { tenantId: string; children: ReactNode }) {
  const queryClient = useQueryClient();
  const previousTenantId = useRef<string | null>(null);

  useEffect(() => {
    if (previousTenantId.current && previousTenantId.current !== tenantId) {
      void invalidateTenant(queryClient, previousTenantId.current);
    }

    previousTenantId.current = tenantId;
  }, [queryClient, tenantId]);

  return <TenantContext.Provider value={tenantId}>{children}</TenantContext.Provider>;
}

export function useTenantId(): string {
  const tenantId = useContext(TenantContext);
  if (tenantId) return tenantId;

  const urlTenant = new URLSearchParams(window.location.search).get('tenantId');
  if (urlTenant) return urlTenant;

  throw new Error('Tenant id is required from TenantContextProvider or URL tenantId.');
}
