import { useEffect, type ReactNode } from 'react';
import { usePageActions } from '../../layout/PageActionsContext';

export interface PageHeaderProps {
  eyebrow: string;
  title: string;
  breadcrumb: string;
  summary: string;
  meta?: ReactNode;
  actions?: ReactNode;
}

export function PageHeader({ eyebrow, title, breadcrumb, summary, meta, actions }: PageHeaderProps) {
  const { setPromotedActions } = usePageActions();

  useEffect(() => {
    setPromotedActions(actions ? { label: `${title} page actions`, actions } : null);
    return () => setPromotedActions(null);
  }, [setPromotedActions, title]);

  return (
    <header className="page-header" aria-labelledby={`${title.toLowerCase().replace(/[^a-z0-9]+/g, '-')}-heading`}>
      <div>
        <p className="page-header__crumb">{breadcrumb}</p>
        <p className="eyebrow">{eyebrow}</p>
        <h1 id={`${title.toLowerCase().replace(/[^a-z0-9]+/g, '-')}-heading`}>{title}</h1>
        <p className="page-header__summary">{summary}</p>
        {meta ? <div className="page-header__meta">{meta}</div> : null}
      </div>
      {actions ? <div className="page-header__actions">{actions}</div> : null}
    </header>
  );
}
