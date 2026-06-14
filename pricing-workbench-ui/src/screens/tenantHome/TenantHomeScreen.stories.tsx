import { TenantHomeScreen } from './TenantHomeScreen';
import type { AuthorizedProduct, TenantHomeTenantContext } from '../../lib/api/tenantHome';

const tenants: TenantHomeTenantContext[] = [
  { tenantId: 'storybook-tenant-a', tenantName: 'Storybook Tenant A', status: 'ACTIVE', userId: 'storybook-user' },
  { tenantId: 'storybook-tenant-b', tenantName: 'Storybook Tenant B', status: 'ACTIVE', userId: 'storybook-user' },
];

const products: AuthorizedProduct[] = [
  { productCode: 'STORY_CONV', productName: 'Storybook conventional placeholder', productType: 'CONVENTIONAL', investorCode: 'BACKEND_REF', channelCode: 'RETAIL', status: 'ACTIVE', lastUpdated: 'storybook-ref' },
  { productCode: 'STORY_FHA', productName: 'Storybook FHA placeholder', productType: 'FHA', investorCode: 'BACKEND_REF', channelCode: 'WHOLESALE', status: 'PENDING', lastUpdated: 'storybook-ref' },
];

export default {
  title: 'PII-54/Tenant Home Screen',
  component: TenantHomeScreen,
};

export const Ready = () => <TenantHomeScreen tenants={tenants} initialProducts={products} userId="storybook-user" />;
export const SingleTenant = () => <TenantHomeScreen tenants={tenants.slice(0, 1)} initialProducts={products.slice(0, 1)} userId="storybook-user" />;
export const Empty = () => <TenantHomeScreen tenants={tenants} initialProducts={[]} userId="storybook-user" />;
