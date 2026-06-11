import { useState } from 'react';
import { retry } from '../recovery';
import type { ErrorFallbackProps } from '../types';

export function ModuleErrorFallback(props: ErrorFallbackProps & { moduleId?: string }) {
  const [detailsOpen, setDetailsOpen] = useState(false);
  const moduleId = props.moduleId ?? props.context?.moduleId ?? 'module';

  return (
    <section role="alert" aria-live="polite" data-boundary-level="module" style={cardStyle}>
      <strong>{moduleId} could not load.</strong>
      <p>{props.error.message || 'The rest of the screen is still available.'}</p>
      <p>Reference {props.uiTraceId}</p>
      <div style={actionsStyle}>
        <button type="button" onClick={() => retry(props.reset)}>Retry</button>
        <button type="button" onClick={props.dismiss}>Dismiss</button>
        <button type="button" aria-expanded={detailsOpen} onClick={() => setDetailsOpen((open) => !open)}>Show Details</button>
      </div>
      {detailsOpen ? <pre style={detailsStyle}>{props.errorInfo?.componentStack ?? props.error.stack ?? props.error.message}</pre> : null}
    </section>
  );
}

const cardStyle = { padding: '1rem', border: '1px solid #f59e0b', borderRadius: '0.75rem', background: '#fffbeb' };
const actionsStyle = { display: 'flex', gap: '0.5rem', flexWrap: 'wrap' as const };
const detailsStyle = { whiteSpace: 'pre-wrap' as const, marginTop: '0.75rem' };
