export type ApiErrorRefs = {
  requestId?: string;
  traceId?: string;
  evidenceRefs?: string[];
};

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly refs: ApiErrorRefs;

  constructor(status: number, code: string, message: string, refs: ApiErrorRefs = {}) {
    super(sanitizeErrorMessage(message));
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.refs = refs;
  }
}

export function sanitizeErrorMessage(message: string): string {
  return message.replace(/\s+at\s+.+/g, '').replace(/https?:\/\/\S+/g, '[redacted-url]').trim();
}

export function isRetryableError(error: unknown): boolean {
  if (error instanceof ApiError) {
    return error.status === 408 || error.status === 429 || error.status >= 500;
  }

  if (error instanceof TypeError) return true;
  if (error instanceof Error) return /temporarily unavailable|network|timeout/i.test(error.message);
  return false;
}

export function getErrorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error) return sanitizeErrorMessage(error.message || 'The request could not be completed.');
  return 'The request could not be completed.';
}
