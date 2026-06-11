import { ComponentErrorFallback } from './ComponentErrorFallback';
import { ModuleErrorFallback } from './ModuleErrorFallback';
import { ScreenErrorFallback } from './ScreenErrorFallback';
import type { ErrorFallbackProps } from '../types';

const baseProps: ErrorFallbackProps = {
  error: new Error('Pricing details failed to render'),
  errorCode: 'SCREEN_LOAD_FAILED',
  uiTraceId: 'ui-trace-storybook',
  reset: () => undefined,
  retryCount: 0,
  level: 'screen',
  context: { screenId: 'quote-offers', moduleId: 'waterfall', componentId: 'rate-chart' },
};

export default {
  title: 'Error Boundary/Fallbacks',
};

export function ScreenDefault() {
  return <ScreenErrorFallback {...baseProps} />;
}

export function ModuleInline() {
  return <ModuleErrorFallback {...baseProps} level="module" moduleId="waterfall" />;
}

export function ComponentMinimal() {
  return <ComponentErrorFallback {...baseProps} level="component" />;
}
