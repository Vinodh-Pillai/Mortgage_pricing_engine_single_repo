import { lazy } from 'react';
import type React from 'react';
import { createScreenModule, getEvidenceTarget } from '../contract/ScreenModule';
import type { ScreenProps, ScreenVisualState } from '../contract/ScreenProps';
import PerformanceDashboardScreen from './PerformanceDashboardScreen';

export { default as PerformanceDashboardScreen } from './PerformanceDashboardScreen';
export { blockedPerformanceDashboardFixture, performanceDashboardFixture } from './fixtures';
export { exportPerformanceDashboardJson, stateForPerformanceDashboard } from './PerformanceDashboardScreen';

export const performanceDashboardScreenModule = createScreenModule({
  id: 'performance-dashboard',
  label: 'Performance Dashboard',
  routePattern: '/ops/performance',
  breadcrumb: 'Performance Dashboard',
  screenPackage: 'screens/observabilityPerformance',
  dataBoundary: 'lib/api/observabilityPerformance.fetchPerformanceDashboard',
  stateCoverage: ['loading', 'load-state', 'empty', 'blocked', 'needs-attention', 'ready', 'service-groups', 'freshness', 'blocked-evidence', 'recovery-owner'] as unknown as ScreenVisualState[],
  dependencyStatus: 'Consumes observability-service performance refs when connected runtime is available.',
  adapterStatus: 'Renders backend or deterministic fixture refs only; no local metrics collection, alerting rules, or runtime changes.',
  evidenceTarget: getEvidenceTarget('performance-dashboard', 'PII-24-S20'),
  match: (pathname) => pathname.startsWith('/ops/performance'),
  Component: lazy(async () => ({ default: PerformanceDashboardScreen })) as unknown as React.LazyExoticComponent<React.ComponentType<ScreenProps>>,
});
