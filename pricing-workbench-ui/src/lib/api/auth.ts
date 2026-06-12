export type BackendUserRole =
  | 'loan_officer'
  | 'pricing_analyst'
  | 'operations_lead'
  | 'governance_reviewer'
  | 'admin'
  | 'partner_manager'
  | 'compliance_officer'
  | 'borrower';

export interface AuthUser {
  id: string;
  email: string;
  name: string;
  role: BackendUserRole;
}

export interface AuthResponse {
  user: AuthUser;
}

export interface LoginPayload {
  email: string;
  password: string;
}

export interface RegisterPayload extends LoginPayload {
  name: string;
  role: BackendUserRole;
}

const authApiBaseUrl = (import.meta.env.VITE_TENANT_CONTEXT_API_BASE_URL ?? '').replace(/\/$/, '');

async function authFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`${authApiBaseUrl}/api/auth${path}`, {
    ...init,
    credentials: 'include',
    headers: {
      ...(init.body ? { 'Content-Type': 'application/json' } : {}),
      ...init.headers,
    },
  });
  if (!response.ok) {
    const errorBody = await response.json().catch(() => undefined) as { error?: string } | undefined;
    throw new Error(errorBody?.error ?? `Authentication request failed with ${response.status}`);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export function loginWithPassword(payload: LoginPayload) {
  return authFetch<AuthResponse>('/login', { method: 'POST', body: JSON.stringify(payload) });
}

export function registerUser(payload: RegisterPayload) {
  return authFetch<AuthResponse>('/register', { method: 'POST', body: JSON.stringify(payload) });
}

export function logoutUser() {
  return authFetch<void>('/logout', { method: 'POST' });
}

export function fetchCurrentUser() {
  return authFetch<AuthResponse>('/me');
}
