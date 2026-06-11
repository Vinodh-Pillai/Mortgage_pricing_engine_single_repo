import type { ErrorBoundaryProps } from './types';
import { ErrorBoundary } from './ErrorBoundary';
import { ComponentErrorFallback } from './fallbacks/ComponentErrorFallback';

export function ComponentErrorBoundary(props: ErrorBoundaryProps) {
  return <ErrorBoundary {...props} level="component" defaultFallback={(fallbackProps) => <ComponentErrorFallback {...fallbackProps} />} />;
}
