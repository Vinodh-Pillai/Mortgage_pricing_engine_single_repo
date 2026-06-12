import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { MetadataState } from '../../lib/api/quoteRuns';
import { fetchIntakeMetadata } from './metadata';
import { QuoteIntakeFlow } from './QuoteIntakeFlow';

export function QuoteIntakeScreen({ tenantId = 'ui-preview-tenant' }: { tenantId?: string }) {
  const [metadataState, setMetadataState] = useState<MetadataState>({ kind: 'loading' });
  const navigate = useNavigate();

  useEffect(() => {
    let active = true;
    fetchIntakeMetadata(tenantId)
      .then((metadata) => { if (active) setMetadataState({ kind: 'loaded', metadata }); })
      .catch((error: unknown) => { if (active) setMetadataState({ kind: 'unreachable', message: error instanceof Error ? error.message : 'Scenario intake metadata is unavailable.' }); });
    return () => { active = false; };
  }, [tenantId]);

  return <QuoteIntakeFlow tenantId={tenantId} metadataState={metadataState} onNavigate={(route) => navigate(route)} />;
}

export default QuoteIntakeScreen;
