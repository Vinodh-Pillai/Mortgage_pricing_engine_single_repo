import { afterEach, describe, expect, it, vi } from 'vitest';
import { AUTH_SECURITY_BLOCKERS, getAuthClientConfig, login, registerUser } from './auth';

const bffBaseUrl = 'https://pricing-bff.example.test';

function jsonResponse(body: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } }));
}

describe('auth api client security boundaries', () => {
  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
  });

  it('routes password login only to the configured BFF base URL', async () => {
    vi.stubEnv('VITE_BFF_API_BASE_URL', `${bffBaseUrl}/`);
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      expect(String(input)).toBe(`${bffBaseUrl}/api/auth/login`);
      expect(init?.method).toBe('POST');
      return jsonResponse({ user: { id: 'u-1', email: 'user@example.test', fullName: 'Test User', role: 'pricing_analyst' } });
    });
    vi.stubGlobal('fetch', fetchMock);

    await login('user@example.test', 'test-password-value');

    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('blocks authentication when the BFF base URL is missing', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    await expect(login('user@example.test', 'test-password-value')).rejects.toThrow(/BFF API base URL is required/);

    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('routes registration through the configured BFF base URL', async () => {
    vi.stubEnv('VITE_BFF_API_BASE_URL', `${bffBaseUrl}/`);
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      expect(String(input)).toBe(`${bffBaseUrl}/api/auth/register`);
      expect(init?.method).toBe('POST');
      return jsonResponse({ user: { id: 'u-1', email: 'user@example.test', fullName: 'Test User', role: 'pricing_analyst' } });
    });
    vi.stubGlobal('fetch', fetchMock);

    await registerUser({
      email: 'user@example.test',
      password: 'test-password-value',
      fullName: 'Test User',
      role: 'pricing_analyst',
    });

    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('exposes auth config as BFF API only', () => {
    vi.stubEnv('VITE_BFF_API_BASE_URL', `${bffBaseUrl}/`);

    expect(getAuthClientConfig()).toEqual({
      bffApiBaseUrl: bffBaseUrl,
    });
    expect(AUTH_SECURITY_BLOCKERS).toEqual(expect.arrayContaining([
      expect.stringContaining('OIDC/PKCE'),
      expect.stringContaining('only to VITE_BFF_API_BASE_URL'),
    ]));
  });
});
