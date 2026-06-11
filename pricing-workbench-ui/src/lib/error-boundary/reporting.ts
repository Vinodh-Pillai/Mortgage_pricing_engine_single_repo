import type { ErrorInfo } from 'react';
import { classifyError, levelDefaultCode } from './codes';
import type { ErrorBoundaryLevel, ErrorContext, ErrorReport, ErrorSeverity } from './types';

export type ErrorTransport = (reports: ErrorReport[]) => Promise<void> | void;

export type ErrorReporterOptions = {
  endpoint?: string;
  transport?: ErrorTransport;
  flushMs?: number;
  env?: 'development' | 'production' | 'test';
};

const SECRET_KEY_PATTERN = /authorization|password|secret|token|ssn|social|email|phone|requestBody|body/i;
const INTERNAL_PATH_PATTERN = /([A-Z]:\\[^\s)]+|\/Users\/[^\s)]+|\/home\/[^\s)]+|\/workspace\/[^\s)]+)/g;

let queue: ErrorReport[] = [];
let flushTimer: ReturnType<typeof setTimeout> | undefined;
let options: ErrorReporterOptions = {};

export function configureErrorReporting(nextOptions: ErrorReporterOptions) {
  options = { ...options, ...nextOptions };
}

export function sanitizeDiagnostic<T>(value: T): T {
  if (typeof value === 'string') {
    return value.replace(/Bearer\s+[A-Za-z0-9._-]+/g, 'Bearer [redacted]').replace(INTERNAL_PATH_PATTERN, '[redacted-path]') as T;
  }

  if (Array.isArray(value)) return value.map((item) => sanitizeDiagnostic(item)) as T;

  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value).map(([key, nested]) => [key, SECRET_KEY_PATTERN.test(key) ? '[redacted]' : sanitizeDiagnostic(nested)]),
    ) as T;
  }

  return value;
}

export function createErrorContext(context: ErrorContext = {}): ErrorContext {
  const viewport = typeof window === 'undefined' ? undefined : { width: window.innerWidth, height: window.innerHeight };
  return sanitizeDiagnostic({
    route: typeof window === 'undefined' ? undefined : window.location.pathname,
    timestamp: new Date().toISOString(),
    userAgent: typeof navigator === 'undefined' ? undefined : navigator.userAgent,
    viewport,
    uiTraceId: context.uiTraceId ?? createUiTraceId(),
    ...context,
  });
}

export function createErrorReport(
  error: Error,
  errorInfo: ErrorInfo | undefined,
  context: ErrorContext = {},
  level: ErrorBoundaryLevel = 'component',
  severity: ErrorSeverity = level === 'screen' ? 'fatal' : 'error',
): ErrorReport {
  const sanitizedContext = createErrorContext(context);
  return sanitizeDiagnostic({
    name: error.name,
    message: error.message,
    stack: error.stack,
    componentStack: errorInfo?.componentStack ?? undefined,
    code: classifyError(error, levelDefaultCode(level)),
    level,
    severity,
    context: sanitizedContext,
  });
}

export function reportError(error: Error, errorInfo: ErrorInfo | undefined, context: ErrorContext = {}, level: ErrorBoundaryLevel = 'component') {
  const report = createErrorReport(error, errorInfo, context, level);
  const env = options.env ?? (import.meta.env.PROD ? 'production' : 'development');

  if (env !== 'production') {
    console.error('Pricing workbench UI error', report);
    return report;
  }

  queue.push(report);
  scheduleFlush();
  return report;
}

export async function flushErrorReports() {
  const reports = queue.splice(0, queue.length);
  if (reports.length === 0) return;

  try {
    if (options.transport) {
      await options.transport(reports);
      return;
    }

    const endpoint = options.endpoint ?? import.meta.env.VITE_ERROR_MONITORING_URL;
    if (!endpoint) return;

    await fetch(endpoint, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ reports }),
      keepalive: true,
    });
  } catch (error) {
    console.error('Error reporting failed', sanitizeDiagnostic(error));
  }
}

export function resetErrorReportingForTests() {
  queue = [];
  options = {};
  if (flushTimer) clearTimeout(flushTimer);
  flushTimer = undefined;
}

export function createUiTraceId() {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID();
  return `ui-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

function scheduleFlush() {
  if (flushTimer) return;
  flushTimer = setTimeout(() => {
    flushTimer = undefined;
    void flushErrorReports();
  }, options.flushMs ?? 5_000);
}
