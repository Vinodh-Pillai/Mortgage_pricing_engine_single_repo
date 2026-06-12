import { lazy } from 'react';
import { createScreenModule } from '../contract/ScreenModule';
import { allVisualStates } from '../shared/MajorFunctionalityPage';
import { lockManagementEvidenceTarget } from './LockManagementScreen';

export const lockManagementScreenModule = createScreenModule({
  id: 'lock-management',
  label: 'Lock Management',
  routePattern: '/locks',
  breadcrumb: 'Lock Management',
  screenPackage: 'screens/locks',
  dataBoundary: 'lock-service management APIs',
  stateCoverage: allVisualStates,
  personaVisibility: ['operations-lead', 'loan-officer', 'admin', 'partner-manager'],
  dependencyStatus: 'Lock-service expiration and investor delivery refs govern blocked and attention states.',
  adapterStatus: 'Screen renders lock statuses, bulk action controls, and evidence capture without changing backend state.',
  evidenceTarget: lockManagementEvidenceTarget,
  match: (pathname) => pathname === '/locks' || pathname.startsWith('/locks/') || /^\/quote\/[^/]+\/lock/.test(pathname),
  Component: lazy(() => import('./LockManagementScreen').then((module) => ({ default: module.LockManagementScreen }))),
});

export { LockManagementScreen, lockManagementEvidenceTarget } from './LockManagementScreen';
export default lockManagementScreenModule;
