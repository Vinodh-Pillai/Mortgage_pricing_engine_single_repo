import { ProductAdminScreen } from './ProductAdminScreen';

export default {
  title: 'PII-54/Product Admin Screen',
  component: ProductAdminScreen,
};

export const BlockedPreview = () => <ProductAdminScreen fetchImpl={() => Promise.reject(new Error('storybook blocked preview')) as Promise<Response>} tenantContext="storybook-tenant" />;
