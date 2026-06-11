import { createContext, useContext, type ReactNode } from 'react';
import { reportError } from './reporting';
import type { ErrorContext, ErrorSeverity } from './types';

type ErrorReporterApi = {
  captureError: (error: Error, context?: ErrorContext) => void;
  captureMessage: (message: string, level?: ErrorSeverity, context?: ErrorContext) => void;
  context: ErrorContext;
};

const WorkbenchErrorContext = createContext<ErrorReporterApi | undefined>(undefined);

export function ErrorContextProvider({ children, context = {} }: { children: ReactNode; context?: ErrorContext }) {
  const api: ErrorReporterApi = {
    context,
    captureError(error, nextContext = {}) {
      reportError(error, undefined, { ...context, ...nextContext }, 'component');
    },
    captureMessage(message, level = 'info', nextContext = {}) {
      reportError(new Error(message), undefined, { ...context, ...nextContext, severity: level }, 'component');
    },
  };

  return <WorkbenchErrorContext.Provider value={api}>{children}</WorkbenchErrorContext.Provider>;
}

export function useErrorReporter() {
  const api = useContext(WorkbenchErrorContext);
  if (!api) {
    return {
      context: {},
      captureError(error: Error, context?: ErrorContext) {
        reportError(error, undefined, context, 'component');
      },
      captureMessage(message: string, level: ErrorSeverity = 'info', context?: ErrorContext) {
        reportError(new Error(message), undefined, { ...context, severity: level }, 'component');
      },
    } satisfies ErrorReporterApi;
  }
  return api;
}
