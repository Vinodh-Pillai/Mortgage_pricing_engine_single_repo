export const partnerIntegrationRoutes = [
  '/partners/integrations',
  '/partners/webhooks',
  '/partners/admin/safety',
  '/partners/alerts',
] as const;

export const partnerIntegrationStateCoverage = [
  'load-state',
  'quote-requests',
  'webhook-delivery',
  'retries',
  'dlq',
  'feed-adapters',
  'sftp-adapters',
  'health',
  'blocked',
] as const;

export const partnerIntegrationEvidenceTarget = '.local-harness/evidence/PII-24-S22/partner-integrations.json';

export type PartnerIntegrationRoute = (typeof partnerIntegrationRoutes)[number];
export type PartnerIntegrationState = (typeof partnerIntegrationStateCoverage)[number];
