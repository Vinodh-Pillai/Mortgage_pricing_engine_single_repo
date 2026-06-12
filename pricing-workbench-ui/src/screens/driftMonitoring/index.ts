import { lazy } from 'react';
import type React from 'react';
import { createScreenModule, getEvidenceTarget } from '../contract/ScreenModule';
import type { ScreenProps, ScreenVisualState } from '../contract/ScreenProps';
import { DriftMonitoringScreen } from './DriftLayout';

export { DriftMonitoringScreen } from './DriftLayout';
export { blockedDriftMonitoringView } from './fixtures';
export { driftMonitoringEvidenceTarget, driftMonitoringRoute, driftMonitoringStateCoverage } from './screenModule';

export const driftMonitoringScreenModule = createScreenModule({
  id: 'drift-monitoring',
  label: 'Drift Monitoring',
  routePattern: '/advisory/ml/drift',
  breadcrumb: 'Drift Monitoring',
  screenPackage: 'screens/driftMonitoring',
  dataBoundary: 'ml-advisory-service drift APIs',
  stateCoverage: ['loading', 'empty', 'blocked', 'needs-attention', 'ready', 'load-state', 'feature-drift', 'prediction-drift', 'population-stability', 'alerts', 'investigation'] as unknown as ScreenVisualState[],
  dependencyStatus: 'Consumes ML advisory drift APIs when configured; otherwise renders blocked setup state with evidence refs and story-approved thresholds.',
  adapterStatus: 'React UI renders drift monitoring state only and does not implement drift detection algorithms.',
  evidenceTarget: getEvidenceTarget('drift-monitoring', 'PII-24-S27'),
  Component: lazy(async () => ({ default: DriftMonitoringScreen })) as unknown as React.LazyExoticComponent<React.ComponentType<ScreenProps>>,
});
