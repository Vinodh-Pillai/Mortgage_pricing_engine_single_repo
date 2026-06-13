import { matchPath } from 'react-router-dom';
import { FairLendingDashboard } from './FairLendingDashboard';

export const fairLendingEvidenceTarget = '.local-harness/evidence/PII-31-S01/fair-lending-dashboard.json';
export const fairLendingStateCoverage = ['loading', 'blocked', 'ready', 'violation', 'drilldown'];

export const fairLendingScreenModule = {
  id: 'fair-lending-dashboard',
  label: 'Fair lending analysis',
  routePattern: '/compliance/fair-lending',
  breadcrumb: 'Fair Lending Analysis',
  screenPackage: 'screens/fairLending',
  dataBoundary: 'lib/api/fairLending',
  stateCoverage: fairLendingStateCoverage,
  personaVisibility: ['compliance-officer', 'governance-reviewer', 'admin'],
  evidenceTarget: fairLendingEvidenceTarget,
  match: (pathname: string) => Boolean(matchPath({ path: '/compliance/fair-lending', end: true }, pathname)),
  Component: FairLendingDashboard,
};

export { FairLendingDashboard };
