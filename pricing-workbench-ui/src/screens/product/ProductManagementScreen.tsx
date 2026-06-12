import { MajorFunctionalityPage, type EvidenceCapture, type FunctionalityPageConfig } from '../shared/MajorFunctionalityPage';
import type { ScreenVisualState } from '../contract/ScreenProps';

type ProductRow = { id: string; name: string; channel: string; status: string };

export const productManagementEvidenceTarget = '.local-harness/evidence/PII-25-S04/product-management.json';

const productConfig: FunctionalityPageConfig<ProductRow> = {
  screenId: 'product-management',
  evidenceTarget: productManagementEvidenceTarget,
  breadcrumb: 'Products / Catalog',
  eyebrow: 'Product catalog service',
  title: 'Product Management',
  summary: 'Manages catalog taxonomy, product definitions, channel mapping, investor eligibility references, pricing-rule references, and version history without hardcoding pricing rules.',
  dataBoundary: 'product-service: GET/POST /api/v1/products',
  sections: [
    { id: 'product-taxonomy', eyebrow: 'Catalog', title: 'Product Taxonomy', summary: 'Backend-owned grouping and display hierarchy.', status: 'ready', items: ['Family', 'Program', 'Variant'] },
    { id: 'product-definitions', eyebrow: 'Definitions', title: 'Product Definitions', summary: 'Draft definitions and lifecycle state.', status: 'ready', items: ['Draft metadata', 'Owner', 'Borrower need'] },
    { id: 'channel-mapping', eyebrow: 'Channels', title: 'Channel Mapping', summary: 'Channel availability records.', status: 'needs-attention', items: ['Retail', 'Broker', 'Partner'] },
    { id: 'investor-eligibility', eyebrow: 'Eligibility', title: 'Investor Eligibility', summary: 'Investor-owned eligibility references only.', status: 'blocked', items: ['Investor ref', 'Eligibility ref', 'Contract status'] },
    { id: 'pricing-rules', eyebrow: 'Rules', title: 'Pricing Rules', summary: 'Rule set references without UI-side calculations.', status: 'needs-attention', items: ['Rule package ref', 'Validation ref', 'Approval ref'] },
    { id: 'version-history', eyebrow: 'Versions', title: 'Version History', summary: 'Snapshots, audit trail, and lifecycle history.', status: 'ready', items: ['Snapshot', 'Audit event', 'Rollback ref'] },
  ],
  metrics: [
    { label: 'Bulk actions', value: '4', help: 'Activate, deactivate, clone, export' },
    { label: 'Editable grid', value: 'Inline', help: 'Local UI state only' },
    { label: 'Evidence', value: 'Enabled', help: 'Action and state capture' },
  ],
  rows: [
    { id: 'product-alpha', name: 'Purchase product draft', channel: 'Retail', status: 'ready' },
    { id: 'product-beta', name: 'Refinance product draft', channel: 'Broker', status: 'needs-attention' },
    { id: 'product-gamma', name: 'Partner product draft', channel: 'Partner', status: 'blocked' },
  ],
  columns: [
    { key: 'name', header: 'Product' },
    { key: 'channel', header: 'Channel' },
    { key: 'status', header: 'Status', render: (row) => <span className={`functionality-badge functionality-badge--${row.status}`}>{row.status}</span> },
  ],
  tableCaption: 'Product catalog records',
  primaryActions: [{ id: 'new-product', label: 'New product', variant: 'primary' }],
  secondaryActions: [{ id: 'activate-products', label: 'Activate' }, { id: 'deactivate-products', label: 'Deactivate' }, { id: 'clone-products', label: 'Clone' }, { id: 'export-products', label: 'Export' }],
  emptyMessage: 'No product catalog rows match the current filter.',
  blockedMessage: 'Product management is blocked until product-service catalog and investor eligibility contracts are configured.',
  attentionMessage: 'Review channel mapping and pricing-rule references before publication.',
};

export function ProductManagementScreen({ visualState, onEvidenceCapture }: { visualState?: ScreenVisualState; onEvidenceCapture?: EvidenceCapture }) {
  return <MajorFunctionalityPage config={productConfig} visualState={visualState} onEvidenceCapture={onEvidenceCapture} />;
}

export default ProductManagementScreen;
