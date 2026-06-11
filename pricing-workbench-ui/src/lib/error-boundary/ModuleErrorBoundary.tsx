import type { ErrorBoundaryProps } from './types';
import { ErrorBoundary } from './ErrorBoundary';
import { ModuleErrorFallback } from './fallbacks/ModuleErrorFallback';

export function ModuleErrorBoundary(props: ErrorBoundaryProps) {
  return <ErrorBoundary {...props} level="module" defaultFallback={(fallbackProps) => <ModuleErrorFallback {...fallbackProps} />} />;
}
