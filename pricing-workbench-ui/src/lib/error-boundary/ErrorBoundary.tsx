import { Component, isValidElement, type ErrorInfo, type ReactNode } from 'react';
import { classifyError, levelDefaultCode } from './codes';
import { createErrorContext, reportError } from './reporting';
import type { ErrorBoundaryLevel, ErrorBoundaryProps, ErrorContext, ErrorFallbackProps } from './types';

type Props = ErrorBoundaryProps & {
  level: ErrorBoundaryLevel;
  defaultFallback: (props: ErrorFallbackProps) => ReactNode;
};

type State = {
  error?: Error;
  errorInfo?: ErrorInfo;
  retryCount: number;
  dismissed: boolean;
};

export class ErrorBoundary extends Component<Props, State> {
  state: State = { retryCount: 0, dismissed: false };

  static getDerivedStateFromError(error: Error): Partial<State> {
    return { error, dismissed: false };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    const context = createBoundaryContext(this.props.context);
    this.setState({ errorInfo });
    reportError(error, errorInfo, context, this.props.level);
    this.props.onError?.(error, errorInfo, context);
  }

  componentDidUpdate(prevProps: Props) {
    if (!this.state.error) return;
    if (this.props.resetOnPropsChange && prevProps.children !== this.props.children) this.reset();
    if (haveResetKeysChanged(prevProps.resetKeys, this.props.resetKeys)) this.reset();
  }

  reset = () => {
    this.setState((state) => ({ error: undefined, errorInfo: undefined, retryCount: state.retryCount + 1, dismissed: false }));
  };

  dismiss = () => {
    this.setState({ dismissed: true });
  };

  render() {
    const { error, errorInfo, dismissed, retryCount } = this.state;
    if (!error || dismissed) return this.props.children;

    const uiTraceId = String(this.props.context?.uiTraceId ?? createBoundaryContext(this.props.context).uiTraceId ?? 'unavailable');
    const fallbackProps: ErrorFallbackProps = {
      error,
      errorInfo,
      errorCode: classifyError(error, levelDefaultCode(this.props.level)),
      uiTraceId,
      reset: this.reset,
      dismiss: this.dismiss,
      retryCount,
      level: this.props.level,
      context: this.props.context,
    };

    try {
      if (typeof this.props.fallback === 'function') return this.props.fallback(fallbackProps);
      if (isValidElement(this.props.fallback)) return this.props.fallback;
      return this.props.fallback ?? this.props.defaultFallback(fallbackProps);
    } catch {
      return <div role="alert">Something went wrong. Reference {uiTraceId}.</div>;
    }
  }
}

export function createBoundaryContext(context: ErrorContext = {}): ErrorContext {
  return createErrorContext(context);
}

function haveResetKeysChanged(previous: unknown[] = [], next: unknown[] = []) {
  return previous.length !== next.length || previous.some((value, index) => !Object.is(value, next[index]));
}
