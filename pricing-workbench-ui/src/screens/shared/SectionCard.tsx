import { useState, type ReactNode } from 'react';
import type { ScreenVisualState } from '../contract/ScreenProps';

export interface SectionCardProps {
  id: string;
  title: string;
  eyebrow: string;
  summary: string;
  status: ScreenVisualState;
  defaultExpanded?: boolean;
  children: ReactNode;
  onToggle?: (expanded: boolean) => void;
}

export function SectionCard({ id, title, eyebrow, summary, status, defaultExpanded = true, children, onToggle }: SectionCardProps) {
  const [expanded, setExpanded] = useState(defaultExpanded);
  const headingId = `${id}-heading`;
  return (
    <article className="section-card" aria-labelledby={headingId}>
      <div className="section-card__header">
        <div>
          <p className="section-card__eyebrow">{eyebrow}</p>
          <h2 id={headingId}>{title}</h2>
          <p className="section-card__summary">{summary}</p>
        </div>
        <div>
          <span className={`section-card__status section-card__status--${status}`}>{status}</span>
          <button type="button" className="ds-control ds-button ds-variant-ghost ds-size-sm" aria-expanded={expanded} aria-controls={`${id}-body`} onClick={() => { setExpanded((current) => { onToggle?.(!current); return !current; }); }}>
            {expanded ? 'Collapse' : 'Expand'}
          </button>
        </div>
      </div>
      {expanded ? <div id={`${id}-body`} className="section-card__body">{children}</div> : null}
    </article>
  );
}
