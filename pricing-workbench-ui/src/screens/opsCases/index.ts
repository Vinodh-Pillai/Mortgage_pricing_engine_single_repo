import OpsCasesScreen from './OpsCasesScreen';

export const opsCasesScreenModule = {
  id: 'ops-cases',
  label: 'Operations cases',
  routePattern: '/ops/dashboard',
  breadcrumb: 'Operations Cases',
  screenPackage: 'screens/opsCases',
  dataBoundary: 'lib/api/opsCases',
  stateCoverage: ['load-state', 'empty', 'blocked', 'ready'],
  evidenceTarget: '.local-harness/evidence/PII-24-S19/ops-cases.json',
  match: (pathname: string) => pathname.startsWith('/ops/dashboard') || pathname.startsWith('/ops/queues') || pathname.startsWith('/ops/cases') || pathname.startsWith('/ops/escalations'),
  Component: OpsCasesScreen,
};

export { OpsCasesScreen };
export default OpsCasesScreen;
