import { ApiError } from '../data/errors';

export const ErrorCodes = {
  SCREEN_LOAD_FAILED: 'SCREEN_LOAD_FAILED',
  MODULE_LOAD_FAILED: 'MODULE_LOAD_FAILED',
  COMPONENT_RENDER_ERROR: 'COMPONENT_RENDER_ERROR',
  DATA_FETCH_FAILED: 'DATA_FETCH_FAILED',
  MUTATION_FAILED: 'MUTATION_FAILED',
  NETWORK_ERROR: 'NETWORK_ERROR',
  PERMISSION_DENIED: 'PERMISSION_DENIED',
  SESSION_EXPIRED: 'SESSION_EXPIRED',
  UNKNOWN_ERROR: 'UNKNOWN_ERROR',
} as const;

export type ErrorCode = (typeof ErrorCodes)[keyof typeof ErrorCodes];

export function classifyError(error: unknown, fallback: ErrorCode = ErrorCodes.UNKNOWN_ERROR): ErrorCode {
  if (error instanceof ApiError) {
    if (error.status === 401) return ErrorCodes.SESSION_EXPIRED;
    if (error.status === 403) return ErrorCodes.PERMISSION_DENIED;
    if (error.status === 408 || error.status === 429 || error.status >= 500) return ErrorCodes.NETWORK_ERROR;
    if (/data|query|fetch/i.test(error.code)) return ErrorCodes.DATA_FETCH_FAILED;
    if (/mutation|update|write/i.test(error.code)) return ErrorCodes.MUTATION_FAILED;
    return fallback;
  }

  if (error instanceof TypeError || (error instanceof Error && /network|fetch|timeout/i.test(error.message))) {
    return ErrorCodes.NETWORK_ERROR;
  }

  if (error instanceof Error && /permission|forbidden|denied/i.test(error.message)) return ErrorCodes.PERMISSION_DENIED;
  if (error instanceof Error && /session|login|unauthorized|expired/i.test(error.message)) return ErrorCodes.SESSION_EXPIRED;
  return fallback;
}

export function levelDefaultCode(level: 'screen' | 'module' | 'component'): ErrorCode {
  if (level === 'screen') return ErrorCodes.SCREEN_LOAD_FAILED;
  if (level === 'module') return ErrorCodes.MODULE_LOAD_FAILED;
  return ErrorCodes.COMPONENT_RENDER_ERROR;
}
