import { useEffect, useMemo, useState, type ReactNode } from 'react';
import type { ScreenEvidence, ScreenVisualState } from '../contract/ScreenProps';
import { ActionToolbar, DataTable, PageHeader, PageStateWrapper, SectionCard, type DataTableColumn, type ToolbarAction } from './index';

export type EvidenceCapture = (evidence: ScreenEvidence & { evidenceTarget: string; refs: string[]; action?: string }) => void;

export interface FunctionalitySection {
  id: string;
  eyebrow: string;
  title: string;
  summary: string;
  status: ScreenVisualState;
  items: string[];
}

export interface FunctionalityPageConfig<T extends object> {
  screenId: string;
  evidenceTarget: string;
  breadcrumb: string;
  eyebrow: string;
  title: string;
  summary: string;
  dataBoundary: string;
  sections: FunctionalitySection[];
  metrics: Array<{ label: string; value: string; help: string }>;
  tableCaption: string;
  rows: T[];
  columns: DataTableColumn<T>[];
  primaryActions: ToolbarAction[];
  secondaryActions: ToolbarAction[];
  emptyMessage: string;
  blockedMessage: string;
  attentionMessage: string;
  renderSpotlight?: (onEvidence: (actionId: string) => void) => ReactNode;
}

export interface MajorFunctionalityPageProps<T extends object> {
  config: FunctionalityPageConfig<T>;
  visualState?: ScreenVisualState;
  onEvidenceCapture?: EvidenceCapture;
}

export function MajorFunctionalityPage<T extends object>({ config, visualState = 'ready', onEvidenceCapture }: MajorFunctionalityPageProps<T>) {
  const [state, setState] = useState<ScreenVisualState>(visualState);
  const refs = useMemo(() => [config.evidenceTarget, config.dataBoundary, ...config.sections.map((section) => section.id)], [config]);

  useEffect(() => setState(visualState), [visualState]);
  useEffect(() => {
    onEvidenceCapture?.({ screenId: config.screenId, timestamp: new Date().toISOString(), state, dataRefs: refs, blockers: state === 'blocked' ? [config.blockedMessage] : [], evidenceTarget: config.evidenceTarget, refs });
  }, [config.evidenceTarget, config.screenId, onEvidenceCapture, refs, state]);

  function recordAction(action: string) {
    onEvidenceCapture?.({ screenId: config.screenId, timestamp: new Date().toISOString(), state, dataRefs: refs, blockers: state === 'blocked' ? [config.blockedMessage] : [], evidenceTarget: config.evidenceTarget, refs, action });
    if (action.includes('review') || action.includes('validate')) setState('needs-attention');
  }

  return (
    <main className="functionality-page" data-screen-id={config.screenId}>
      <PageHeader
        eyebrow={config.eyebrow}
        title={config.title}
        breadcrumb={config.breadcrumb}
        summary={config.summary}
        meta={<span>Screen data is scoped to this workspace.</span>}
        actions={<ActionToolbar label={`${config.title} actions`} primaryActions={config.primaryActions} secondaryActions={config.secondaryActions} onAction={recordAction} />}
      />
      <PageStateWrapper state={state} title={config.title} emptyMessage={config.emptyMessage} blockedMessage={config.blockedMessage} attentionMessage={config.attentionMessage}>
        <section className="page-metrics" aria-label={`${config.title} readiness metrics`}>
          {config.metrics.map((metric) => <div className="page-metric" key={metric.label}><span>{metric.label}</span><strong>{metric.value}</strong><small>{metric.help}</small></div>)}
        </section>
        {config.renderSpotlight?.(recordAction)}
        <section className="section-grid" aria-label={`${config.title} progressive sections`}>
          {config.sections.map((section, index) => (
            <SectionCard key={section.id} {...section} defaultExpanded={index < 2} onToggle={(expanded) => recordAction(`${section.id}-${expanded ? 'expanded' : 'collapsed'}`)}>
              <ul className="progress-list">{section.items.map((item) => <li key={item}>{item}</li>)}</ul>
            </SectionCard>
          ))}
        </section>
        <SectionCard id={`${config.screenId}-records`} eyebrow="Workspace records" title={`${config.title} records`} summary="Sortable and filterable local view backed by service-owned data contracts." status="ready">
          <DataTable caption={config.tableCaption} rows={config.rows} columns={config.columns} />
        </SectionCard>
      </PageStateWrapper>
    </main>
  );
}

export const allVisualStates: ScreenVisualState[] = ['loading', 'empty', 'blocked', 'needs-attention', 'ready'];
