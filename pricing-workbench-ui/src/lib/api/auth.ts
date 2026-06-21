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
  bffApiBaseUrl: string;
}

export const AUTH_CONFIG = {
  bffApiBaseUrl: 'VITE_BFF_API_BASE_URL',
} as const;

export const AUTH_SECURITY_BLOCKERS = [
  'The UI must submit authentication requests only to the same-origin /api proxy or VITE_BFF_API_BASE_URL.',
  'The BFF owns the tenant-context-service authentication contract and session cookie forwarding.',
  'Full OIDC/PKCE remains blocked until IdP issuer, client id, redirect URIs, scopes, and token/session ownership are supplied by approved configuration.',
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
    bffApiBaseUrl: normalizeBaseUrl(import.meta.env.VITE_BFF_API_BASE_URL),
  };
}

function resolveAuthApiBaseUrl(): string {
  const config = getAuthClientConfig();
  return config.bffApiBaseUrl;
}

async function authFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const authApiBaseUrl = resolveAuthApiBaseUrl();
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
  const response = await authFetch<RawAuthResponse>('/login', { method: 'POST', body: JSON.stringify({ email, password } satisfies LoginRequest) });
  return normalizeAuthResponse(response);
}

export async function register(email: string, password: string, fullName: string, role: UserRole): Promise<AuthResponse> {
  const response = await authFetch<RawAuthResponse>('/register', { method: 'POST', body: JSON.stringify({ email, password, fullName, role } satisfies RegisterRequest) });
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
