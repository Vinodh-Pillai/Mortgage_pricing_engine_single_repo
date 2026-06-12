import { lazy } from 'react';
import { createScreenModule } from '../contract/ScreenModule';
import { allVisualStates } from '../shared/MajorFunctionalityPage';
import { rateSheetIntakeEvidenceTarget } from './RateSheetIntakeScreen';

export const rateSheetIntakeScreenModule = createScreenModule({
  id: 'rate-sheet-intake',
  label: 'Rate Sheet Intake',
  routePattern: '/pricing/rate-sheets',
  breadcrumb: 'Rate Sheet Intake',
  screenPackage: 'screens/ratesheet',
  dataBoundary: 'rate-sheet-service intake APIs',
  stateCoverage: allVisualStates,
  personaVisibility: ['pricing-analyst', 'admin', 'operations-lead'],
  dependencyStatus: 'Rate sheet service grid and validation contracts control publish readiness.',
  adapterStatus: 'Upload, validation, remediation, and publish states are represented with local evidence capture.',
  evidenceTarget: rateSheetIntakeEvidenceTarget,
  match: (pathname) => pathname.startsWith('/pricing/rate-sheets'),
  Component: lazy(() => import('./RateSheetIntakeScreen').then((module) => ({ default: module.RateSheetIntakeScreen }))),
});

export { RateSheetIntakeScreen, rateSheetIntakeEvidenceTarget } from './RateSheetIntakeScreen';
export default rateSheetIntakeScreenModule;
