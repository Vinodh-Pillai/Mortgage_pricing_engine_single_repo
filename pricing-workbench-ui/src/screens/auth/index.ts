import { lazy } from 'react';
import { createScreenModule, getEvidenceTarget } from '../contract/ScreenModule';

export const loginScreenModule = createScreenModule({
  id: 'login',
  label: 'Login',
  routePattern: '/login',
  breadcrumb: 'Login',
  screenPackage: 'screens/auth',
  dataBoundary: 'lib/auth/AuthContext',
  stateCoverage: ['loading', 'empty', 'blocked', 'needs-attention', 'ready'],
  evidenceTarget: getEvidenceTarget('login-screen', 'PII-25-S03'),
  personaVisibility: ['*'],
  dependencyStatus: 'Public route backed by PostgreSQL authentication through /api/auth endpoints.',
  adapterStatus: 'Uses AuthContext.login(email, password) and HttpOnly cookie session state.',
  Component: lazy(() => import('./LoginScreen').then((module) => ({ default: module.LoginScreen }))),
});

export const loginScreenRoutes = ['/login'];
export const loginScreenStateCoverage = ['load-state', 'empty', 'ready'];
export const loginScreenEvidenceTarget = loginScreenModule.evidenceTarget;

export { LoginScreen } from './LoginScreen';
export { PersonaCard, roleIconFor, visiblePermissionChips } from './PersonaCard';
