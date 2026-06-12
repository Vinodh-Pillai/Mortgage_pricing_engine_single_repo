import type { ReactNode } from 'react';
import type { ScreenVisualState } from '../contract/ScreenProps';

export interface PageStateWrapperProps {
  state: ScreenVisualState;
  title: string;
  emptyMessage?: string;
  blockedMessage?: string;
  attentionMessage?: string;
  children: ReactNode;
}

export function PageStateWrapper({ state, title, emptyMessage, blockedMessage, attentionMessage, children }: PageStateWrapperProps) {
  if (state === 'loading') {
    return (
      <section className="page-state page-state--loading" aria-labelledby="page-loading-heading">
        <p className="page-state__eyebrow">Visual state</p>
        <h2 id="page-loading-heading">Loading {title}</h2>
        <p role="status">Loading screen data and setup evidence...</p>
        <div className="page-state__skeleton" aria-hidden="true"><span /><span /><span /></div>
      </section>
    );
  }

  if (state === 'empty') {
    return (
      <section className="page-state page-state--empty" aria-labelledby="page-empty-heading">
        <p className="page-state__eyebrow">Visual state</p>
        <h2 id="page-empty-heading">No {title.toLowerCase()} records yet</h2>
        <p>{emptyMessage ?? 'No records are available for this workbench area.'}</p>
      </section>
    );
  }

  if (state === 'blocked') {
    return (
      <section className="page-state page-state--blocked" aria-labelledby="page-blocked-heading" role="alert">
        <p className="page-state__eyebrow">Visual state</p>
        <h2 id="page-blocked-heading">{title} blocked</h2>
        <p>{blockedMessage ?? 'Required connected-service configuration is not available. The UI records the blocked state without inventing business data.'}</p>
      </section>
    );
  }

  if (state === 'needs-attention') {
    return (
      <section className="page-state page-state--needs-attention" aria-labelledby="page-attention-heading" role="alert">
        <p className="page-state__eyebrow">Visual state</p>
        <h2 id="page-attention-heading">{title} needs attention</h2>
        <p>{attentionMessage ?? 'Review the highlighted records before continuing.'}</p>
        {children}
      </section>
    );
  }

  return <>{children}</>;
}
