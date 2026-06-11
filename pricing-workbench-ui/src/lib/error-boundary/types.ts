import type { ErrorInfo, ReactNode } from 'react';

export type ErrorSeverity = 'info' | 'warning' | 'error' | 'fatal';

export type ErrorBoundaryLevel = 'screen' | 'module' | 'component';

export type ErrorContext = {
  route?: string;
  screenId?: string;
  moduleId?: string;
  componentId?: string;
  userId?: string;
  tenantId?: string;
  uiTraceId?: string;
  timestamp?: string;
  userAgent?: string;
  viewport?: { width: number; height: number };
  props?: Record<string, unknown>;
  [key: string]: unknown;
};

export type ErrorReport = {
  name: string;
  message: string;
  stack?: string;
  componentStack?: string;
  code: string;
  level: ErrorBoundaryLevel;
  severity: ErrorSeverity;
  context: ErrorContext;
};

export type ErrorFallbackProps = {
  error: Error;
  errorInfo?: ErrorInfo;
  errorCode: string;
  uiTraceId: string;
  reset: () => void;
  dismiss?: () => void;
  retryCount: number;
  level: ErrorBoundaryLevel;
  context?: ErrorContext;
};

export type ErrorFallbackRenderer = ReactNode | ((props: ErrorFallbackProps) => ReactNode);

export type ErrorBoundaryProps = {
  children: ReactNode;
  fallback?: ErrorFallbackRenderer;
  onError?: (error: Error, errorInfo: ErrorInfo, context: ErrorContext) => void;
  resetKeys?: unknown[];
  resetOnPropsChange?: boolean;
  context?: ErrorContext;
};
