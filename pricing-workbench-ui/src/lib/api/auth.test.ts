import { afterEach, describe, expect, it, vi } from 'vitest';
import { AUTH_SECURITY_BLOCKERS, getAuthClientConfig, login, registerUser } from './auth';

const tenantAuthBaseUrl = 'https://tenant-context-auth.example.test';
const bffBaseUrl = 'https://pricing-bff.example.test';

function jsonResponse(body: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } }));
}

describe('auth api client security boundaries', () => {
  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
  });

  it('routes password login only to configured tenant-context auth base and not the BFF base', async () => {
    vi.stubEnv('VITE_TENANT_CONTEXT_AUTH_BASE_URL', `${tenantAuthBaseUrl}/`);
    vi.stubEnv('VITE_BFF_API_BASE_URL', `${bffBaseUrl}/`);
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      expect(String(input)).toBe(`${tenantAuthBaseUrl}/api/auth/login`);
      expect(String(input)).not.toContain(bffBaseUrl);
      expect(init?.method).toBe('POST');
      return jsonResponse({ user: { id: 'u-1', email: 'user@example.test', fullName: 'Test User', role: 'pricing_analyst' } });
    });
    vi.stubGlobal('fetch', fetchMock);

    await login('user@example.test', 'test-password-value');

    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('blocks credential submission when only a BFF base URL is configured', async () => {
    vi.stubEnv('VITE_BFF_API_BASE_URL', bffBaseUrl);
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    await expect(login('user@example.test', 'test-password-value')).rejects.toThrow(/Tenant-context authentication base URL is required/);

    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('blocks credential submission when tenant auth and BFF bases are the same configured endpoint', async () => {
    vi.stubEnv('VITE_TENANT_CONTEXT_AUTH_BASE_URL', bffBaseUrl);
    vi.stubEnv('VITE_BFF_API_BASE_URL', `${bffBaseUrl}/`);
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    await expect(registerUser({
      email: 'user@example.test',
      password: 'test-password-value',
      fullName: 'Test User',
      role: 'pricing_analyst',
    })).rejects.toThrow(/must not match the BFF API base URL/);

    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('exposes auth config as tenant auth, tenant API, and BFF API separately', () => {
    vi.stubEnv('VITE_TENANT_CONTEXT_AUTH_BASE_URL', `${tenantAuthBaseUrl}/`);
    vi.stubEnv('VITE_TENANT_CONTEXT_API_BASE_URL', 'https://tenant-context-api.example.test/');
    vi.stubEnv('VITE_BFF_API_BASE_URL', `${bffBaseUrl}/`);

    expect(getAuthClientConfig()).toEqual({
      tenantContextAuthBaseUrl: tenantAuthBaseUrl,
      tenantContextApiBaseUrl: 'https://tenant-context-api.example.test',
      bffApiBaseUrl: bffBaseUrl,
    });
    expect(AUTH_SECURITY_BLOCKERS).toEqual(expect.arrayContaining([
      expect.stringContaining('OIDC/PKCE'),
      expect.stringContaining('must not submit raw credentials'),
    ]));
  });
});
