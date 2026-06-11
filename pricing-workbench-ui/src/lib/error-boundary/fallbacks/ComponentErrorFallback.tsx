import { useState } from 'react';
import type { ErrorFallbackProps } from '../types';

export function ComponentErrorFallback(props: ErrorFallbackProps) {
  const [retrying, setRetrying] = useState(false);

  if (retrying) return <div aria-label="Retrying component" style={skeletonStyle} />;

  return (
    <span role="alert" data-boundary-level="component" style={inlineStyle}>
      Failed to load. <button type="button" onClick={() => { setRetrying(true); setTimeout(props.reset, 0); }}>Retry</button>
    </span>
  );
}

const inlineStyle = { display: 'inline-flex', alignItems: 'center', gap: '0.5rem', color: '#991b1b' };
const skeletonStyle = { display: 'inline-block', width: '10rem', height: '1.5rem', borderRadius: '0.5rem', background: '#e2e8f0' };
