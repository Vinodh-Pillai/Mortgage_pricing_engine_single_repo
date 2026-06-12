import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { fetchCurrentUser, loginWithPassword, logoutUser, type AuthUser, type BackendUserRole } from '../api/auth';
import {
  ACTIVE_PERSONA_STORAGE_KEY,
  canAccessRoute as personaCanAccessRoute,
  getPersonaByRole as findPersonaByRole,
  hasPermission as personaHasPermission,
  syntheticPersonas,
  type Permission,
  type Persona,
  type PersonaRole,
} from './personas';

export interface AuthEventDetail {
  type: 'login' | 'logout' | 'session-refresh' | 'permission-check' | 'route-access';
  personaId: string | null;
  target?: string;
  allowed?: boolean;
}

export interface AuthContextType {
  currentPersona: Persona | null;
  currentUser: AuthUser | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  authError: string | null;
  login: (email: string, password?: string) => Promise<Persona | null>;
  logout: () => Promise<void>;
  refreshCurrentUser: () => Promise<Persona | null>;
  hasPermission: (permission: Permission) => boolean;
  canAccessRoute: (route: string) => boolean;
  availablePersonas: Persona[];
  getPersonaByRole: (role: PersonaRole) => Persona | undefined;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

function emitAuthEvent(detail: AuthEventDetail) {
  if (typeof window === 'undefined' || typeof window.dispatchEvent !== 'function') return;
  window.dispatchEvent(new CustomEvent<AuthEventDetail>('wcpe:auth', { detail }));
}

const backendToPersonaRole: Record<BackendUserRole, PersonaRole> = {
  loan_officer: 'loan-officer',
  pricing_analyst: 'pricing-analyst',
  operations_lead: 'operations-lead',
  governance_reviewer: 'governance-reviewer',
  admin: 'admin',
  partner_manager: 'partner-manager',
  compliance_officer: 'compliance-officer',
  borrower: 'borrower',
};

function personaFromUser(user: AuthUser): Persona {
  const role = backendToPersonaRole[user.role];
  const basePersona = findPersonaByRole(role);
  return {
    ...(basePersona ?? {
      id: `user-${user.id}`,
      name: user.name,
      role,
      email: user.email,
      description: 'Authenticated workbench user.',
      permissions: [],
      defaultRoute: '/pipeline',
    }),
    id: user.id,
    name: user.name,
    email: user.email,
    role,
    avatar: basePersona?.avatar ?? user.name.split(/\s+/).map((part) => part[0]).join('').slice(0, 2).toUpperCase(),
  };
}

function backendRoleFromPersona(role: PersonaRole): BackendUserRole {
  return role.replace(/-/g, '_') as BackendUserRole;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [currentUser, setCurrentUser] = useState<AuthUser | null>(null);
  const [currentPersona, setCurrentPersona] = useState<Persona | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [authError, setAuthError] = useState<string | null>(null);

  const applyUser = useCallback((user: AuthUser | null) => {
    const persona = user ? personaFromUser(user) : null;
    setCurrentUser(user);
    setCurrentPersona(persona);
    return persona;
  }, []);

  const refreshCurrentUser = useCallback(async () => {
    setIsLoading(true);
    try {
      const response = await fetchCurrentUser();
      setAuthError(null);
      const persona = applyUser(response.user);
      emitAuthEvent({ type: 'session-refresh', personaId: persona?.id ?? null, allowed: true });
      return persona;
    } catch {
      applyUser(null);
      return null;
    } finally {
      setIsLoading(false);
    }
  }, [applyUser]);

  useEffect(() => {
    void refreshCurrentUser();
  }, [refreshCurrentUser]);

  const login = useCallback(async (email: string, password?: string) => {
    if (!password && import.meta.env.DEV) {
      const persona = syntheticPersonas.find((candidate) => candidate.id === email || candidate.email === email);
      if (persona) {
        setCurrentUser({ id: persona.id, email: persona.email, name: persona.name, role: backendRoleFromPersona(persona.role) });
        setCurrentPersona(persona);
        setAuthError(null);
        emitAuthEvent({ type: 'login', personaId: persona.id, allowed: true });
        return persona;
      }
    }
    try {
      const response = await loginWithPassword({ email, password: password ?? '' });
      const persona = applyUser(response.user);
      setAuthError(null);
      emitAuthEvent({ type: 'login', personaId: persona?.id ?? null, allowed: true });
      return persona;
    } catch (exception) {
      const message = exception instanceof Error ? exception.message : 'Unable to sign in';
      setAuthError(message);
      throw exception;
    }
  }, [applyUser]);

  const logout = useCallback(async () => {
    const personaId = currentPersona?.id ?? null;
    try {
      await logoutUser();
    } finally {
      applyUser(null);
      emitAuthEvent({ type: 'logout', personaId, allowed: true });
    }
  }, [applyUser, currentPersona?.id]);

  const hasPermission = useCallback((permission: Permission) => {
    const allowed = currentPersona ? personaHasPermission(currentPersona, permission) : false;
    emitAuthEvent({ type: 'permission-check', personaId: currentPersona?.id ?? null, target: permission, allowed });
    return allowed;
  }, [currentPersona]);

  const canAccessRoute = useCallback((route: string) => {
    const allowed = currentPersona ? personaCanAccessRoute(currentPersona, route) : false;
    emitAuthEvent({ type: 'route-access', personaId: currentPersona?.id ?? null, target: route, allowed });
    return allowed;
  }, [currentPersona]);

  const getPersonaByRole = useCallback((role: PersonaRole) => findPersonaByRole(role), []);

  const value = useMemo<AuthContextType>(() => ({
    currentPersona,
    currentUser,
    isAuthenticated: currentPersona !== null,
    isLoading,
    authError,
    login,
    logout,
    refreshCurrentUser,
    hasPermission,
    canAccessRoute,
    availablePersonas: syntheticPersonas,
    getPersonaByRole,
  }), [authError, canAccessRoute, currentPersona, currentUser, getPersonaByRole, hasPermission, isLoading, login, logout, refreshCurrentUser]);

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

export { ACTIVE_PERSONA_STORAGE_KEY };
