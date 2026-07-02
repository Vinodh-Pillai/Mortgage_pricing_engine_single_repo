import { describe, expect, it, vi } from 'vitest';
import { fetchPartnerIntegrationAlerts } from './partnerTransport';

describe('partner transport API defensive normalization', () => {
  it('returns an empty fail-closed alerts view when the BFF route is unavailable', async () => {
    const fetchMock = vi.fn(async () => ({
      ok: false,
      status: 404,
      json: async () => ({}),
    }));

    const view = await fetchPartnerIntegrationAlerts('partner-preview', fetchMock as unknown as typeof fetch);

    expect(view.partnerId).toBe('partner-preview');
    expect(view.dependencyStatus).toBe('PARTNER_INTEGRATION_ALERT_CONTRACT_BLOCKED');
    expect(view.alerts).toEqual([]);
    expect(view.rulesStatus).toBe('ALERT_RULES_CONTRACT_REQUIRED');
    expect(view.fallbackReason).toContain('404');
  });

  it('normalizes missing alert arrays without inventing alert records', async () => {
    const fetchMock = vi.fn(async () => ({
      ok: true,
      status: 200,
      json: async () => ({ partnerId: 'partner-preview', tenantContext: 'tenant-test' }),
    }));

    const view = await fetchPartnerIntegrationAlerts('partner-preview', fetchMock as unknown as typeof fetch);

    expect(view.tenantContext).toBe('tenant-test');
    expect(view.alerts).toEqual([]);
    expect(view.fallbackReason).toContain('live alert contract');
    expect(view.fallbackReason).not.toMatch(/fallback/i);
  });
});
