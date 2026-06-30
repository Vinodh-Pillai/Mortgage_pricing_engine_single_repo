import { useState } from 'react';
import { createSupportHref, navigate, refresh, retry } from '../recovery';
import type { ErrorFallbackProps } from '../types';

export function ScreenErrorFallback(props: ErrorFallbackProps & { screenId?: string }) {
  const [detailsOpen, setDetailsOpen] = useState(false);
  const screenId = props.screenId ?? props.context?.screenId ?? 'screen';
  const message = props.error.message || 'This screen could not be loaded.';
  const retryLimitReached = props.retryCount >= 3;
  const sessionExpired = props.errorCode === 'SESSION_EXPIRED';

  return (
    <main role="alert" aria-live="assertive" data-boundary-level="screen" style={panelStyle}>
      <div aria-hidden="true" style={iconStyle}>!</div>
      <p style={eyebrowStyle}>LoanWeft</p>
      <h1>{screenId} is temporarily unavailable</h1>
      <p>{message}</p>
      <dl>
        <dt>Error code</dt>
        <dd>{props.errorCode}</dd>
        <dt>Correlation ID</dt>
        <dd>{props.uiTraceId}</dd>
      </dl>
      <div style={actionsStyle}>
        {retryLimitReached ? <p role="status">This error persisted after 3 retry attempts. Contact support with the correlation ID.</p> : <button type="button" onClick={() => retry(props.reset)}>Try Again</button>}
        {sessionExpired ? <a href="/login">Sign in again</a> : null}
        <button type="button" onClick={() => navigate()}>Go Back</button>
        <button type="button" onClick={() => refresh()}>Refresh Page</button>
        <a href={createSupportHref(props.error, { uiTraceId: props.uiTraceId })}>Contact Support</a>
      </div>
      <button type="button" aria-expanded={detailsOpen} onClick={() => setDetailsOpen((open) => !open)}>
        Technical Details
      </button>
      {detailsOpen ? <TechnicalDetails {...props} /> : null}
    </main>
  );
}

function TechnicalDetails(props: ErrorFallbackProps) {
  return (
    <pre style={detailsStyle}>
      {JSON.stringify(
        {
          message: props.error.message,
          stack: props.error.stack,
          componentStack: props.errorInfo?.componentStack,
          props: props.context?.props,
        },
        null,
        2,
      )}
    </pre>
  );
}

const panelStyle = { maxWidth: '48rem', margin: '4rem auto', padding: '2rem', border: '1px solid #d7dae0', borderRadius: '1rem' };
const iconStyle = { width: '3rem', height: '3rem', borderRadius: '999px', display: 'grid', placeItems: 'center', background: '#fee2e2', color: '#991b1b', fontWeight: 700 };
const eyebrowStyle = { color: '#475569', textTransform: 'uppercase' as const, letterSpacing: '0.08em' };
const actionsStyle = { display: 'flex', flexWrap: 'wrap' as const, gap: '0.75rem', margin: '1rem 0' };
const detailsStyle = { maxHeight: '16rem', overflow: 'auto', background: '#0f172a', color: '#e2e8f0', padding: '1rem', borderRadius: '0.75rem' };
