import ExceptionConcessionsScreen from './ExceptionConcessionsScreen';

export const exceptionConcessionsScreenModule = {
  id: 'exception-concessions',
  label: 'Exception concessions',
  routePattern: '/exceptions/concessions',
  breadcrumb: 'Exception Concessions',
  screenPackage: 'screens/exceptionConcessions',
  dataBoundary: 'lib/api/exceptionConcessions',
  stateCoverage: ['load-state', 'concession-request', 'eligibility-exception', 'authority-matrix', 'manual-price-guard', 'risk-events', 'history-replay-export'],
  evidenceTarget: '.local-harness/evidence/PII-24-S17/exception-concessions.json',
  match: (pathname: string) => pathname.startsWith('/exceptions/concessions'),
  Component: ExceptionConcessionsScreen,
};

export { ExceptionConcessionsScreen };
export default ExceptionConcessionsScreen;
