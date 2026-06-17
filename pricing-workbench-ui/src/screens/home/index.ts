import type { WorkbenchScreenModule } from '../workbenchShell/WorkbenchShell';
import { HomeScreen } from './HomeScreen';

export const homeEvidenceTarget = '.local-harness/evidence/PII-26-S02/home-screen.json';
export const homeStateCoverage = ['load-state', 'empty', 'ready'];

export const homeScreenModule: WorkbenchScreenModule = {
  id: 'home',
  label: 'Home',
  routePattern: '/home',
  breadcrumb: 'Home',
  screenPackage: 'screens/home',
  dataBoundary: 'lib/activity localStorage with backend activity API blocked pending integration',
  stateCoverage: homeStateCoverage,
  personaVisibility: ['*'],
  dependencyStatus: 'Local recent activity is available; backend activity API and audit sync are recorded as integration gaps.',
  adapterStatus: 'Home screen uses local activity fallback until /api/activity is available.',
  evidenceTarget: homeEvidenceTarget,
  match: (pathname) => pathname === '/home' || pathname.startsWith('/home/'),
  Component: HomeScreen,
};

export { HomeScreen } from './HomeScreen';
export { QuickActions, quickActions, hasPermission, type QuickAction, type HomeRole } from './QuickActions';
export { RecentActivity } from './RecentActivity';
