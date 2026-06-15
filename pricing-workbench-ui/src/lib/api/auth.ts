export type UserRole =
  | 'loan_officer'
  | 'pricing_analyst'
  | 'operations_lead'
  | 'governance_reviewer'
  | 'admin'
  | 'partner_manager'
  | 'compliance_officer'
  | 'borrower';

export interface User {
  id: string;
  email: string;
  fullName: string;
  role: UserRole;
  name?: string;
}

export interface AuthResponse {
  user: User;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest extends LoginRequest {
  fullName: string;
  role: UserRole;
}

export type BackendUserRole = UserRole;
export type AuthUser = User;
export type LoginPayload = LoginRequest;
export type RegisterPayload = RegisterRequest & { name?: string };

export interface AuthClientConfig {
  tenantContextAuthBaseUrl: string;
  tenantContextApiBaseUrl: string;
  bffApiBaseUrl: string;
}

export const AUTH_CONFIG = {
  tenantContextAuthBaseUrl: 'VITE_TENANT_CONTEXT_AUTH_BASE_URL',
  tenantContextApiBaseUrl: 'VITE_TENANT_CONTEXT_API_BASE_URL',
  bffApiBaseUrl: 'VITE_BFF_API_BASE_URL',
} as const;

export const AUTH_SECURITY_BLOCKERS = [
  'Password login and registration require VITE_TENANT_CONTEXT_AUTH_BASE_URL.',
  'The UI must not submit raw credentials to VITE_BFF_API_BASE_URL.',
  'Full OIDC/PKCE or server-side BFF auth code exchange remains blocked until IdP issuer, client id, redirect URIs, scopes, and token/session ownership are supplied by approved configuration.',
] as const;

type RawAuthResponse = AuthResponse | { user?: Partial<User> & { name?: string }; error?: string; message?: string };

function normalizeUser(user: Partial<User> & { name?: string }): User {
  const fullName = user.fullName ?? user.name ?? user.email ?? 'Authenticated user';
  return {
    id: String(user.id ?? user.email ?? ''),
    email: String(user.email ?? ''),
    fullName,
    name: user.name ?? fullName,
    role: user.role as UserRole,
  };
}

function normalizeAuthResponse(response: RawAuthResponse): AuthResponse {
  if (!response.user) throw new Error('Authentication response did not include a user');
  return { user: normalizeUser(response.user) };
}

function normalizeBaseUrl(value: unknown): string {
  return typeof value === 'string' ? value.trim().replace(/\/$/, '') : '';
}

export function getAuthClientConfig(): AuthClientConfig {
  return {
    tenantContextAuthBaseUrl: normalizeBaseUrl(import.meta.env.VITE_TENANT_CONTEXT_AUTH_BASE_URL),
    tenantContextApiBaseUrl: normalizeBaseUrl(import.meta.env.VITE_TENANT_CONTEXT_API_BASE_URL),
    bffApiBaseUrl: normalizeBaseUrl(import.meta.env.VITE_BFF_API_BASE_URL),
  };
}

function canonicalConfiguredBaseUrl(value: string): string {
  if (!value) return '';
  try {
    return new URL(value).toString().replace(/\/$/, '');
  } catch {
    return value.replace(/\/$/, '');
  }
}

function sameConfiguredBaseUrl(left: string, right: string): boolean {
  return Boolean(left && right && canonicalConfiguredBaseUrl(left) === canonicalConfiguredBaseUrl(right));
}

function resolveAuthApiBaseUrl(credentialRequest: boolean): string {
  const config = getAuthClientConfig();
  const credentialAuthBaseUrl = config.tenantContextAuthBaseUrl;

  if (credentialRequest) {
    if (!credentialAuthBaseUrl) {
      throw new Error('Tenant-context authentication base URL is required before submitting credentials. Configure VITE_TENANT_CONTEXT_AUTH_BASE_URL; full OIDC/PKCE or server-side BFF auth flow remains blocked by missing IdP configuration.');
    }
    if (sameConfiguredBaseUrl(credentialAuthBaseUrl, config.bffApiBaseUrl)) {
      throw new Error('Credential authentication base URL must not match the BFF API base URL. Configure VITE_TENANT_CONTEXT_AUTH_BASE_URL separately before submitting credentials.');
    }
    return credentialAuthBaseUrl;
  }

  return credentialAuthBaseUrl || config.bffApiBaseUrl;
}

async function authFetch<T>(path: string, init: RequestInit = {}, options: { credentialRequest?: boolean } = {}): Promise<T> {
  const authApiBaseUrl = resolveAuthApiBaseUrl(Boolean(options.credentialRequest));
  const response = await fetch(`${authApiBaseUrl}/api/auth${path}`, {
    ...init,
    credentials: 'include',
    headers: {
      ...(init.body ? { 'Content-Type': 'application/json' } : {}),
      ...init.headers,
    },
  });
  if (!response.ok) {
    const errorBody = await response.json().catch(() => undefined) as { error?: string; message?: string } | undefined;
    throw new Error(errorBody?.error ?? errorBody?.message ?? `Authentication request failed with ${response.status}`);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export async function login(email: string, password: string): Promise<AuthResponse> {
  const response = await authFetch<RawAuthResponse>('/login', { method: 'POST', body: JSON.stringify({ email, password } satisfies LoginRequest) }, { credentialRequest: true });
  return normalizeAuthResponse(response);
}

export async function register(email: string, password: string, fullName: string, role: UserRole): Promise<AuthResponse> {
  const response = await authFetch<RawAuthResponse>('/register', { method: 'POST', body: JSON.stringify({ email, password, fullName, role } satisfies RegisterRequest) }, { credentialRequest: true });
  return normalizeAuthResponse(response);
}

export function logout() {
  return authFetch<void>('/logout', { method: 'POST' });
}

export async function getCurrentUser(): Promise<AuthResponse> {
  const response = await authFetch<RawAuthResponse>('/me');
  return normalizeAuthResponse(response);
}

export function loginWithPassword(payload: LoginPayload) {
  return login(payload.email, payload.password);
}

export function registerUser(payload: RegisterPayload) {
  return register(payload.email, payload.password, payload.fullName ?? payload.name ?? '', payload.role);
}

export const logoutUser = logout;
export const fetchCurrentUser = getCurrentUser;
