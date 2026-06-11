import { useEffect } from 'react';
import type { ScreenProps } from '../contract/ScreenProps';
import { EligibilityLayout } from './EligibilityLayout';
import { eligibleEligibilityFixture, type EligibilityModuleView } from './fixtures';

export type QuoteEligibilityScreenProps = Partial<ScreenProps> & {
  module?: EligibilityModuleView;
  onNavigate?: (path: string) => void;
};

export default function QuoteEligibilityScreen({ tenantId = 'tenant-fixture', runId, optionId, uiTraceId = 'pii-24-s13-local-trace', module = eligibleEligibilityFixture, onEvidenceCapture, onNavigate }: QuoteEligibilityScreenProps) {
  const activeRunId = runId ?? module.runId;
  const activeOptionId = optionId ?? module.optionId;
  const visualState = stateForEligibility(module);

  useEffect(() => {
    onEvidenceCapture?.({
      screenId: 'quote-eligibility',
      timestamp: new Date().toISOString(),
      state: visualState,
      dataRefs: [tenantId, activeRunId, activeOptionId, module.decisionId, module.correlationId, module.uiTraceId, uiTraceId],
      blockers: visualState === 'blocked' || visualState === 'needs-attention' ? module.reasonCodes.map((reason) => reason.reasonCode) : [],
    });
  }, [activeOptionId, activeRunId, module, onEvidenceCapture, tenantId, uiTraceId, visualState]);

  return <EligibilityLayout module={{ ...module, runId: activeRunId, optionId: activeOptionId }} runId={activeRunId} optionId={activeOptionId} onNavigate={onNavigate ?? (() => undefined)} />;
}

export function stateForEligibility(module: EligibilityModuleView) {
  if (module.status === 'LOADING') return 'loading';
  if (module.status === 'BLOCKED') return 'blocked';
  if (!module.decisionId) return 'empty';
  if (module.decision !== 'ELIGIBLE' || module.reasonCodes.some((reason) => reason.severity !== 'info') || module.cache.freshness !== 'FRESH') return 'needs-attention';
  return 'ready';
}
