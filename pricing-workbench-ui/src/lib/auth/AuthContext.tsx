import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { getCurrentUser, login as loginRequest, logout as logoutRequest, type User, type UserRole } from '../api/auth';
import { canAccessRoute as personaCanAccessRoute, hasPermission as personaHasPermission, permissionsForRoute, type Permission, type Persona, type PersonaRole } from './personas';

export interface AuthEventDetail {
  type: 'login' | 'logout' | 'session-refresh' | 'permission-check' | 'route-access';
  userId: string | null;
  target?: string;
  allowed?: boolean;
}

export interface AuthContextType {
  user: User | null;
  currentUser: User | null;
  currentPersona: Persona | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  authError: string | null;
  login: (email: string, password: string) => Promise<User>;
  logout: () => Promise<void>;
  refreshCurrentUser: () => Promise<User | null>;
  hasPermission: (permission: Permission) => boolean;
  canAccessRoute: (route: string) => boolean;
}

export const rolePermissionMatrix: Record<UserRole, readonly Permission[]> = {
  loan_officer: ['quote:create', 'quote:read', 'quote:update', 'scenario:create', 'scenario:read', 'scenario:update', 'lock:create', 'lock:read', 'lock:update', 'eligibility:read', 'borrower:manage'],
  pricing_analyst: ['quote:read', 'pricing:read', 'pricing:waterfall', 'margin:read', 'margin:analyze', 'scenario:read', 'scenario:what-if', 'adjustment:read', 'rate-feed:read', 'product:read', 'pricing:analysis'],
  operations_lead: ['quote:read', 'lock:create', 'lock:read', 'lock:update', 'lock:manage', 'partner:read', 'partner:manage', 'ops:read', 'ops:manage', 'rate-feed:read', 'rate-feed:manage', 'rate-sheet:read', 'rate-sheet:manage', 'tenant:manage'],
  governance_reviewer: ['quote:read', 'compliance:read', 'compliance:manage', 'audit:read', 'audit:replay', 'governance:read', 'governance:manage', 'rules:read', 'rules:manage', 'model:read', 'model:governance', 'quality:read'],
  admin: ['*'],
  partner_manager: ['partner:read', 'partner:manage', 'partner:quotes', 'partner:integrations', 'webhook:manage', 'quote:read', 'lock:read'],
  compliance_officer: ['compliance:read', 'compliance:manage', 'audit:read', 'audit:replay', 'privacy:read', 'privacy:manage', 'security:read', 'security:events'],
  borrower: ['quote:create', 'quote:read', 'offer:compare', 'scenario:create', 'eligibility:read'],
};

export const ACTIVE_PERSONA_STORAGE_KEY = 'wcpe:activePersona';
const AUTH_USER_STORAGE_KEY = 'wcpe:authUser';

const AuthContext = createContext<AuthContextType | undefined>(undefined);

function emitAuthEvent(detail: AuthEventDetail) {
  if (typeof window === 'undefined' || typeof window.dispatchEvent !== 'function') return;
  window.dispatchEvent(new CustomEvent<AuthEventDetail>('wcpe:auth', { detail }));
}

function roleToPersonaRole(role: UserRole): PersonaRole {
  return role.replace(/_/g, '-') as PersonaRole;
}

function displayNameFor(user: User) {
  return user.fullName || user.name || user.email;
}

function initialsFor(name: string) {
  return name.split(/\s+/).filter(Boolean).map((part) => part[0]).join('').slice(0, 2).toUpperCase() || 'U';
}

function personaFromUser(user: User): Persona {
  const name = displayNameFor(user);
  return {
    id: user.id,
    name,
    role: roleToPersonaRole(user.role),
    email: user.email,
    avatar: initialsFor(name),
    description: 'Authenticated workbench user.',
    permissions: [...rolePermissionMatrix[user.role]],
    defaultRoute: '/pipeline',
  };
}

function readStoredUser(): User | null {
  if (typeof window === 'undefined') return null;
  const raw = window.sessionStorage.getItem(AUTH_USER_STORAGE_KEY) ?? window.localStorage.getItem(AUTH_USER_STORAGE_KEY);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as User;
    return parsed?.id && parsed?.email && parsed?.role ? parsed : null;
  } catch {
    return null;
  }
}

function storeUser(user: User | null) {
  if (typeof window === 'undefined') return;
  if (!user) {
    window.sessionStorage.removeItem(AUTH_USER_STORAGE_KEY);
    window.localStorage.removeItem(AUTH_USER_STORAGE_KEY);
    return;
  }
  window.sessionStorage.setItem(AUTH_USER_STORAGE_KEY, JSON.stringify(user));
}

function roleHasPermission(role: UserRole, permission: Permission): boolean {
  const permissions = rolePermissionMatrix[role] ?? [];
  if (permissions.includes('*')) return true;
  if (permissions.includes(permission)) return true;
  const [domain] = permission.split(':');
  return permissions.includes(`${domain}:*` as Permission) || permissions.includes('admin:*');
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(() => readStoredUser());
  const userRef = useRef<User | null>(user);
  const [isLoading, setIsLoading] = useState(true);
  const [authError, setAuthError] = useState<string | null>(null);

  const currentPersona = useMemo(() => (user ? personaFromUser(user) : null), [user]);

  const applyUser = useCallback((nextUser: User | null) => {
    userRef.current = nextUser;
    storeUser(nextUser);
    setUser(nextUser);
    return nextUser;
  }, []);

  const refreshCurrentUser = useCallback(async () => {
    setIsLoading(true);
    try {
      const response = await getCurrentUser();
      setAuthError(null);
      const nextUser = response.user;
      applyUser(nextUser);
      emitAuthEvent({ type: 'session-refresh', userId: nextUser.id, allowed: true });
      return nextUser;
    } catch {
      const currentUser = userRef.current;
      if (currentUser) return currentUser;
      applyUser(null);
      emitAuthEvent({ type: 'session-refresh', userId: null, allowed: false });
      return null;
    } finally {
      setIsLoading(false);
    }
  }, [applyUser]);

  useEffect(() => {
    void refreshCurrentUser();
  }, [refreshCurrentUser]);

  const login = useCallback(async (email: string, password: string) => {
    setIsLoading(true);
    try {
      const response = await loginRequest(email, password);
      const nextUser = response.user;
      applyUser(nextUser);
      setAuthError(null);
      emitAuthEvent({ type: 'login', userId: nextUser.id, allowed: true });
      return nextUser;
    } catch (exception) {
      const message = exception instanceof Error ? exception.message : 'Unable to sign in';
      setAuthError(message);
      applyUser(null);
      emitAuthEvent({ type: 'login', userId: null, allowed: false });
      throw exception;
    } finally {
      setIsLoading(false);
    }
  }, [applyUser]);

  const logout = useCallback(async () => {
    const userId = user?.id ?? null;
    setIsLoading(true);
    try {
      await logoutRequest();
    } finally {
      applyUser(null);
      setIsLoading(false);
      emitAuthEvent({ type: 'logout', userId, allowed: true });
    }
  }, [applyUser, user?.id]);

  const hasPermission = useCallback((permission: Permission) => {
    const allowed = user ? roleHasPermission(user.role, permission) : false;
    emitAuthEvent({ type: 'permission-check', userId: user?.id ?? null, target: permission, allowed });
    return allowed;
  }, [user]);

  const canAccessRoute = useCallback((route: string) => {
    let allowed = false;
    if (user) {
      const rule = permissionsForRoute(route);
      if (!rule) allowed = false;
      else if (rule.permissions.length === 0) allowed = true;
      else allowed = rule.match === 'all'
        ? rule.permissions.every((permission) => roleHasPermission(user.role, permission))
        : rule.permissions.some((permission) => roleHasPermission(user.role, permission));
    }
    emitAuthEvent({ type: 'route-access', userId: user?.id ?? null, target: route, allowed });
    return allowed;
  }, [user]);

  const value = useMemo<AuthContextType>(() => ({
    user,
    currentUser: user,
    currentPersona,
    isAuthenticated: user !== null,
    isLoading,
    authError,
    login,
    logout,
    refreshCurrentUser,
    hasPermission,
    canAccessRoute,
  }), [authError, canAccessRoute, currentPersona, hasPermission, isLoading, login, logout, refreshCurrentUser, user]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}

export function useOptionalAuth() {
  return useContext(AuthContext);
}

export { personaCanAccessRoute, personaHasPermission };
