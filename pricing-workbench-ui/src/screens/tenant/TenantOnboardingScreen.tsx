import { MajorFunctionalityPage, type EvidenceCapture, type FunctionalityPageConfig } from '../shared/MajorFunctionalityPage';
import type { ScreenVisualState } from '../contract/ScreenProps';

type TenantRow = { id: string; area: string; owner: string; status: string };

export const tenantOnboardingEvidenceTarget = '.local-harness/evidence/PII-25-S04/tenant-onboarding.json';

const tenantConfig: FunctionalityPageConfig<TenantRow> = {
  screenId: 'tenant-onboarding',
  evidenceTarget: tenantOnboardingEvidenceTarget,
  breadcrumb: 'Onboarding / Tenant',
  eyebrow: 'Tenant service setup',
  title: 'Tenant Onboarding',
  summary: 'Guides tenant setup, identity, channels, integrations, compliance readiness, and launch review.',
  dataBoundary: 'tenant-service: GET/POST /api/v1/tenants',
  sections: [
    { id: 'workspace-setup', eyebrow: 'Step 1', title: 'Workspace Setup', summary: 'Name, operating contact, and launch intent metadata.', status: 'ready', items: ['Workspace name', 'Operations contact', 'Launch goal'] },
    { id: 'identity-configuration', eyebrow: 'Step 2', title: 'Identity Configuration', summary: 'Identity provider readiness and role mapping references.', status: 'needs-attention', items: ['Identity provider reference', 'Admin role map', 'Access evidence'] },
    { id: 'channel-enablement', eyebrow: 'Step 3', title: 'Channel Enablement', summary: 'Enable channel records from service-owned metadata.', status: 'ready', items: ['Retail channel', 'Broker channel', 'Partner channel'] },
    { id: 'integration-endpoints', eyebrow: 'Step 4', title: 'Integration Endpoints', summary: 'Endpoint setup refs and connectivity status.', status: 'blocked', items: ['LOS endpoint ref', 'Document endpoint ref', 'Event subscription ref'] },
    { id: 'compliance-settings', eyebrow: 'Step 5', title: 'Compliance Settings', summary: 'Compliance control ownership and audit evidence refs.', status: 'needs-attention', items: ['Disclosure owner', 'Audit retention ref', 'Fair lending review ref'] },
    { id: 'launch-checklist', eyebrow: 'Step 6', title: 'Launch Checklist', summary: 'Final readiness checklist before tenant activation.', status: 'empty', items: ['Pilot cohort', 'Training confirmation', 'Rollback contact'] },
  ],
  metrics: [
    { label: 'Sections', value: '6', help: 'Progressive onboarding areas' },
    { label: 'Readiness', value: '5 areas', help: 'Loading, empty, blocked, attention, and ready views are covered.' },
    { label: 'Evidence', value: 'Ready', help: 'Actions record review evidence for audit follow-up.' },
  ],
  rows: [
    { id: 'tenant-workspace', area: 'Workspace Setup', owner: 'Operations', status: 'ready' },
    { id: 'tenant-identity', area: 'Identity Configuration', owner: 'Admin', status: 'needs-attention' },
    { id: 'tenant-integrations', area: 'Integration Endpoints', owner: 'Operations', status: 'blocked' },
  ],
  columns: [
    { key: 'area', header: 'Area' },
    { key: 'owner', header: 'Owner' },
    { key: 'status', header: 'Status', render: (row) => <span className={`functionality-badge functionality-badge--${row.status}`}>{row.status}</span> },
  ],
  tableCaption: 'Tenant onboarding readiness records',
  primaryActions: [{ id: 'save-tenant-draft', label: 'Save tenant draft', variant: 'primary' }],
  secondaryActions: [{ id: 'review-tenant-evidence', label: 'Review evidence' }],
  emptyMessage: 'No tenant onboarding draft has been started for this workspace.',
  blockedMessage: 'Tenant onboarding is blocked until required tenant-service metadata and endpoint refs are configured.',
  attentionMessage: 'Identity and compliance records need review before launch.',
};

export function TenantOnboardingScreen({ visualState, onEvidenceCapture }: { visualState?: ScreenVisualState; onEvidenceCapture?: EvidenceCapture }) {
  return <MajorFunctionalityPage config={tenantConfig} visualState={visualState} onEvidenceCapture={onEvidenceCapture} />;
}

export default TenantOnboardingScreen;
