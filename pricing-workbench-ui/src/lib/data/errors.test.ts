import { describe, expect, it } from 'vitest';
import { ApiError, getErrorMessage, isRetryableError } from './errors';

describe('ErrorTest', () => {
  it('apiErrorHasStatusCodeMessage', () => {
    const error = new ApiError(503, 'UNAVAILABLE', 'Service failed at stack line');
    expect(error.status).toBe(503);
    expect(error.message).toBe('Service failed');
  });

  it('retryableErrorsRetried', () => {
    expect(isRetryableError(new ApiError(503, 'UNAVAILABLE', 'Temporary'))).toBe(true);
    expect(isRetryableError(new ApiError(400, 'BAD_REQUEST', 'Invalid'))).toBe(false);
  });

  it('errorMessagesAreUserFacing', () => {
    expect(getErrorMessage(new Error('Failed at stack line'))).toBe('Failed');
  });
});
