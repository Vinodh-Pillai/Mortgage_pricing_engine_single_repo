import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import type { MetadataState } from '../../lib/api/quoteRuns';
import { fetchIntakeMetadata } from './metadata';
import { QuoteIntakeFlow } from './QuoteIntakeFlow';

export function QuoteIntakeScreen({ tenantId = 'ui-preview-tenant', mode }: { tenantId?: string; mode?: 'pipeline' | 'quickquote' }) {
  const [metadataState, setMetadataState] = useState<MetadataState>({ kind: 'loading' });
  const location = useLocation();
  const navigate = useNavigate();
  const resolvedMode = mode ?? (location.pathname === '/quote/start' ? 'quickquote' : 'pipeline');

  useEffect(() => {
    let active = true;
    fetchIntakeMetadata(tenantId)
      .then((metadata) => { if (active) setMetadataState({ kind: 'loaded', metadata }); })
      .catch((error: unknown) => { if (active) setMetadataState({ kind: 'unreachable', message: error instanceof Error ? error.message : 'Scenario intake metadata is unavailable.' }); });
    return () => { active = false; };
  }, [tenantId]);

  return <QuoteIntakeFlow tenantId={tenantId} mode={resolvedMode} metadataState={metadataState} onNavigate={(route) => navigate(route)} />;
}

export default QuoteIntakeScreen;
