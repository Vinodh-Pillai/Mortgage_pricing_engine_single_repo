import type { ErrorBoundaryProps } from './types';
import { ErrorBoundary } from './ErrorBoundary';
import { ScreenErrorFallback } from './fallbacks/ScreenErrorFallback';

export function ScreenErrorBoundary(props: ErrorBoundaryProps) {
  return <ErrorBoundary {...props} level="screen" defaultFallback={(fallbackProps) => <ScreenErrorFallback {...fallbackProps} />} />;
}
