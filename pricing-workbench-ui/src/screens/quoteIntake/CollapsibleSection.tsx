import type { ReactNode } from 'react';

export type CollapsibleSectionProps = {
  id: string;
  title: string;
  summary: string;
  expanded: boolean;
  alwaysExpanded?: boolean;
  hasErrors?: boolean;
  children: ReactNode;
  onToggle: () => void;
};

export function CollapsibleSection({ id, title, summary, expanded, alwaysExpanded = false, hasErrors = false, children, onToggle }: CollapsibleSectionProps) {
  const headingId = `${id}-heading`;
  const panelId = `${id}-panel`;
  return (
    <section className="quote-intake-section" data-expanded={expanded} data-has-errors={hasErrors} aria-labelledby={headingId} role="region">
      <button
        type="button"
        className="quote-intake-section__toggle"
        aria-expanded={expanded}
        aria-controls={panelId}
        disabled={alwaysExpanded}
        onClick={onToggle}
      >
        <span aria-hidden="true" className="quote-intake-section__chevron">{expanded ? '▼' : '▶'}</span>
        <span>
          <strong id={headingId}>{title}</strong>
          <small>{summary}</small>
        </span>
        {hasErrors ? <em>Needs review</em> : null}
      </button>
      {expanded ? (
        <div id={panelId} className="quote-intake-section__panel">
          <div className="quote-intake-section__panel-inner">{children}</div>
        </div>
      ) : null}
    </section>
  );
}

export default CollapsibleSection;
