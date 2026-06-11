import { useEffect, type ReactNode } from 'react';
import { Badge, Button, Card, Heading, Skeleton, Text } from '../../design-system';
import { ScreenErrorBoundary } from '../../lib/error-boundary';
import type { ScreenEvidence, ScreenVisualState } from './ScreenProps';

type ScreenWrapperProps = {
  screenId: string;
  storyId?: string;
  title: string;
  state: ScreenVisualState;
  dataRefs?: string[];
  blockers?: string[];
  uiTraceId?: string;
  routeParams?: Record<string, string>;
  theme?: 'dark' | 'light';
  screenshotRef?: string;
  dependencyStatus?: string;
  remediation?: string[];
  guidance?: string[];
  actions?: ReactNode;
  onEvidenceCapture?: (evidence: ScreenEvidence) => void;
  children?: ReactNode;
};

export function ScreenWrapper({ screenId, storyId = 'PII-24-S05', title, state, dataRefs = [], blockers = [], uiTraceId, routeParams = {}, theme, screenshotRef = '', dependencyStatus, remediation = [], guidance = [], actions, onEvidenceCapture, children }: ScreenWrapperProps) {
  useEffect(() => {
    const evidence = {
      screenId,
      storyId,
      timestamp: new Date().toISOString(),
      state,
      dataRefs,
      screenshotRef,
      blockers,
      uiTraceId: uiTraceId ?? `ui-${screenId}-${storyId}`,
      viewport: currentViewport(),
      theme: theme ?? currentTheme(),
      routeParams,
    };
    onEvidenceCapture?.(evidence);
  }, [blockers, dataRefs, onEvidenceCapture, routeParams, screenId, screenshotRef, state, storyId, theme, uiTraceId]);

  return (
    <section className={`screen-wrapper screen-wrapper--${state}`} aria-labelledby={`${screenId}-heading`} data-screen-id={screenId} data-screen-state={state}>
      <Card>
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Screen module</p>
            <Heading id={`${screenId}-heading`} level={2}>{title}</Heading>
          </div>
          <Badge>{stateLabel(state)}</Badge>
        </div>
        <ScreenErrorBoundary context={{ screenId, uiTraceId, routeParams, props: { storyId, title, state } }} resetKeys={[screenId, storyId, state]}>
          {renderState({ state, title, blockers, dependencyStatus, remediation, guidance, actions, children })}
        </ScreenErrorBoundary>
      </Card>
    </section>
  );
}

export function ScreenSkeleton({ label = 'Loading screen module' }: { label?: string }) {
  return (
    <div className="screen-state screen-state--loading" role="status" aria-label={label}>
      <Skeleton style={{ height: 24, marginBottom: 12 }} />
      <Skeleton style={{ height: 96, marginBottom: 12 }} />
      <Skeleton style={{ height: 40 }} />
    </div>
  );
}

export function EmptyState({ title, guidance = [], actions }: { title: string; guidance?: string[]; actions?: ReactNode }) {
  return <StateBlock tone="info" title={title} body="No records are available for this screen yet." items={guidance} actions={actions} />;
}

export function BlockedState({ dependencyStatus, blockers = [], remediation = [], actions }: { dependencyStatus?: string; blockers?: string[]; remediation?: string[]; actions?: ReactNode }) {
  return <StateBlock tone="blocked" title="Screen setup needs attention" body={dependencyStatus ?? 'A configured dependency is required before this screen can be marked ready.'} items={[...blockers, ...remediation]} actions={actions} />;
}

export function NeedsAttentionState({ guidance = [], actions }: { guidance?: string[]; actions?: ReactNode }) {
  return <StateBlock tone="attention" title="Review setup guidance" body="This screen is visible, but setup or evidence items need review." items={guidance} actions={actions} />;
}

export function ReadyState({ children }: { children?: ReactNode }) {
  return <div className="screen-state screen-state--ready">{children}</div>;
}

function renderState(props: Required<Pick<ScreenWrapperProps, 'state' | 'title' | 'blockers' | 'remediation' | 'guidance'>> & Pick<ScreenWrapperProps, 'dependencyStatus' | 'actions' | 'children'>) {
  if (props.state === 'loading') return <ScreenSkeleton />;
  if (props.state === 'empty') return <EmptyState title={props.title} guidance={props.guidance} actions={props.actions} />;
  if (props.state === 'blocked') return <BlockedState dependencyStatus={props.dependencyStatus} blockers={props.blockers} remediation={props.remediation} actions={props.actions} />;
  if (props.state === 'needs-attention') return <NeedsAttentionState guidance={props.guidance} actions={props.actions} />;
  return <ReadyState>{props.children}</ReadyState>;
}

function StateBlock({ tone, title, body, items, actions }: { tone: 'info' | 'blocked' | 'attention'; title: string; body: string; items: string[]; actions?: ReactNode }) {
  return (
    <div className={`screen-state screen-state--${tone}`} role={tone === 'blocked' ? 'alert' : 'status'}>
      <Heading level={3}>{title}</Heading>
      <Text>{body}</Text>
      {items.length > 0 ? <ul>{items.map((item) => <li key={item}>{item}</li>)}</ul> : null}
      {actions ?? (tone === 'blocked' ? <Button type="button" variant="secondary">Review setup</Button> : null)}
    </div>
  );
}

function stateLabel(state: ScreenVisualState) {
  return state.replace('-', ' ');
}

function currentViewport() {
  const width = typeof window === 'undefined' ? 0 : window.innerWidth;
  const height = typeof window === 'undefined' ? 0 : window.innerHeight;
  const breakpoint = width < 640 ? 'mobile' : width < 1024 ? 'tablet' : 'desktop';
  return { width, height, breakpoint };
}

function currentTheme(): 'dark' | 'light' {
  if (typeof document === 'undefined') return 'light';
  return document.documentElement.classList.contains('dark') ? 'dark' : 'light';
}
