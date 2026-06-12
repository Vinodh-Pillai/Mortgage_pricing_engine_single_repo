import { lazy } from 'react';
import type React from 'react';
import { createScreenModule, getEvidenceTarget } from '../contract/ScreenModule';
import type { ScreenProps, ScreenVisualState } from '../contract/ScreenProps';
import ComplianceEvidenceScreen from './ComplianceEvidenceScreen';

export { default as ComplianceEvidenceScreen, exportComplianceEvidenceJson, stateForComplianceEvidence } from './ComplianceEvidenceScreen';
export { complianceEvidenceFixture, emptyComplianceEvidenceFixture } from './fixtures';

export const complianceEvidenceScreenModule = createScreenModule({
  id: 'compliance-evidence',
  label: 'Compliance evidence registry',
  routePattern: '/compliance/evidence',
  breadcrumb: 'Compliance Evidence',
  screenPackage: 'screens/complianceEvidence',
  dataBoundary: 'lib/api/complianceEvidence.fetchComplianceEvidenceRegistry',
  stateCoverage: ['loading', 'empty', 'blocked', 'needs-attention', 'ready', 'load-state'] as unknown as ScreenVisualState[],
  dependencyStatus: 'Consumes compliance-service registry evidence when connected runtime is available.',
  adapterStatus: 'Renders configured evidence and story fixture labels only; no compliance rules or regulatory calculations run in the browser.',
  evidenceTarget: getEvidenceTarget('compliance-evidence-registry', 'PII-24-S28'),
  match: (pathname) => pathname.startsWith('/compliance') || pathname.startsWith('/privacy/requests') || pathname.startsWith('/security/events'),
  Component: lazy(async () => ({ default: ComplianceEvidenceScreen })) as unknown as React.LazyExoticComponent<React.ComponentType<ScreenProps>>,
});
