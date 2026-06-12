import RateFeedOperationsScreen from './RateFeedOperationsScreen';

export const rateFeedOperationsScreenModule = {
  id: 'rate-feed-ops',
  label: 'Rate feed operations',
  routePattern: '/ops/rate-feeds',
  breadcrumb: 'Rate Feed Operations',
  screenPackage: 'screens/rateFeedOps',
  dataBoundary: 'lib/api/rateFeedOps',
  stateCoverage: ['load-state', 'workflow-steps', 'row-blockers', 'replay-evidence'],
  evidenceTarget: '.local-harness/evidence/PII-24-S18/rate-feed-ops.json',
  match: (pathname: string) => pathname.startsWith('/ops/rate-feeds'),
  Component: RateFeedOperationsScreen,
};

export { RateFeedOperationsScreen };
export default RateFeedOperationsScreen;
