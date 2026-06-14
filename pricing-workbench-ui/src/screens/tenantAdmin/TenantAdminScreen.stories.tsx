import { TenantAdminScreen } from './TenantAdminScreen';
import type { TenantAdminRecord } from '../../lib/api/tenants';

const tenants: TenantAdminRecord[] = [
  {
    tenantId: 'storybook-tenant-001',
    name: 'storybook-lender',
    displayName: 'Storybook Lender',
    status: 'ACTIVE',
    createdAt: 'storybook-ref',
    assignedUserCount: 3,
    country: 'US',
  },
];

export default {
  title: 'PII-54/Tenant Admin Screen',
  component: TenantAdminScreen,
};

export const ApiReady = () => <TenantAdminScreen evidence={tenants} />;
export const BlockedPreview = () => <TenantAdminScreen fetchImpl={() => Promise.reject(new Error('storybook blocked preview')) as Promise<Response>} />;
