import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import type { ReactElement } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../data/errors';
import { ComponentErrorBoundary, ErrorCodes, ModuleErrorBoundary, ScreenErrorBoundary, classifyError, configureErrorReporting, flushErrorReports, resetErrorReportingForTests, sanitizeDiagnostic } from './index';

function Broken({ message = 'Boom' }: { message?: string }): ReactElement {
  throw new Error(message);
}

function Stable() {
  return <div>Recovered child</div>;
}

afterEach(() => {
  cleanup();
  resetErrorReportingForTests();
  vi.restoreAllMocks();
});

describe('ScreenErrorBoundaryTest', () => {
  it('catchesRenderError', () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    render(
      <ScreenErrorBoundary context={{ screenId: 'quote-offers', uiTraceId: 'trace-screen-1' }}>
        <Broken />
      </ScreenErrorBoundary>,
    );

    expect(screen.getByRole('alert')).toHaveTextContent('quote-offers is temporarily unavailable');
    expect(screen.getByText('trace-screen-1')).toBeInTheDocument();
  });

  it('rendersFallback', () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    render(
      <ScreenErrorBoundary context={{ screenId: 'pricing-waterfall', uiTraceId: 'trace-screen-2' }}>
        <Broken message="Pricing waterfall failed" />
      </ScreenErrorBoundary>,
    );

    expect(screen.getByText('SCREEN_LOAD_FAILED')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Try Again' })).toBeInTheDocument();
    expect(screen.getByText('Contact Support')).toHaveAttribute('href', expect.stringContaining('trace-screen-2'));
  });

  it('resetOnRetry', () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    let shouldThrow = true;
    function MaybeBroken() {
      if (shouldThrow) throw new Error('Transient screen error');
      return <Stable />;
    }

    render(
      <ScreenErrorBoundary context={{ screenId: 'quote-offers', uiTraceId: 'trace-screen-3' }}>
        <MaybeBroken />
      </ScreenErrorBoundary>,
    );

    shouldThrow = false;
    fireEvent.click(screen.getByRole('button', { name: 'Try Again' }));
    expect(screen.getByText('Recovered child')).toBeInTheDocument();
  });

  it('showsContactSupportAfterThreeRetries', () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    render(
      <ScreenErrorBoundary context={{ screenId: 'quote-offers', uiTraceId: 'trace-screen-retry-limit' }}>
        <Broken message="Persistent screen error" />
      </ScreenErrorBoundary>,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Try Again' }));
    fireEvent.click(screen.getByRole('button', { name: 'Try Again' }));
    fireEvent.click(screen.getByRole('button', { name: 'Try Again' }));

    expect(screen.queryByRole('button', { name: 'Try Again' })).not.toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('persisted after 3 retry attempts');
    expect(screen.getByText('Contact Support')).toHaveAttribute('href', expect.stringContaining('trace-screen-retry-limit'));
  });

  it('rendersSessionExpiredLoginAction', () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    render(
      <ScreenErrorBoundary context={{ screenId: 'quote-offers', uiTraceId: 'trace-session-expired' }}>
        <Broken message="Session expired" />
      </ScreenErrorBoundary>,
    );

    expect(screen.getByText('SESSION_EXPIRED')).toBeInTheDocument();
    expect(screen.getByText('Sign in again')).toHaveAttribute('href', '/login');
  });

  it('resetOnKeyChange', () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    function BoundaryHarness({ resetKey, shouldThrow }: { resetKey: string; shouldThrow: boolean }) {
      return (
        <ScreenErrorBoundary context={{ screenId: 'quote-offers', uiTraceId: 'trace-screen-4' }} resetKeys={[resetKey]}>
          {shouldThrow ? <Broken /> : <Stable />}
        </ScreenErrorBoundary>
      );
    }

    const { rerender } = render(<BoundaryHarness resetKey="tenant-a" shouldThrow />);
    expect(screen.getByRole('alert')).toBeInTheDocument();
    rerender(<BoundaryHarness resetKey="tenant-b" shouldThrow={false} />);
    expect(screen.getByText('Recovered child')).toBeInTheDocument();
  });
});

describe('ModuleErrorBoundaryTest', () => {
  it('isolatesModuleErrors', () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    render(
      <div>
        <ModuleErrorBoundary context={{ moduleId: 'waterfall', uiTraceId: 'trace-module-1' }}>
          <Broken message="Waterfall failed" />
        </ModuleErrorBoundary>
        <div>Sibling quote summary remains available</div>
      </div>,
    );

    expect(screen.getByRole('alert')).toHaveTextContent('waterfall could not load');
    expect(screen.getByText('Sibling quote summary remains available')).toBeInTheDocument();
  });
});

describe('ComponentErrorBoundaryTest', () => {
  it('showsMinimalFallback', () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    render(
      <ComponentErrorBoundary context={{ componentId: 'chart', uiTraceId: 'trace-component-1' }}>
        <Broken />
      </ComponentErrorBoundary>,
    );

    expect(screen.getByRole('alert')).toHaveTextContent('Failed to load');
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument();
  });
});

describe('ErrorReportingTest', () => {
  it('capturesContext', () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    render(
      <ComponentErrorBoundary context={{ screenId: 'offers', componentId: 'chart', uiTraceId: 'trace-report-1', tenantId: 'tenant-1' }}>
        <Broken />
      </ComponentErrorBoundary>,
    );

    expect(consoleError).toHaveBeenCalledWith('Pricing workbench UI error', expect.objectContaining({ context: expect.objectContaining({ screenId: 'offers', componentId: 'chart', uiTraceId: 'trace-report-1' }) }));
  });

  it('sanitizesPII', () => {
    const result = sanitizeDiagnostic({
      Authorization: 'Bearer abc123',
      ssn: '123-45-6789',
      stack: 'at C:\\Users\\person\\repo\\file.ts:10',
      nested: { email: 'person@example.com', safe: 'ok' },
    });

    expect(result.Authorization).toBe('[redacted]');
    expect(result.ssn).toBe('[redacted]');
    expect(result.stack).toContain('[redacted-path]');
    expect(result.nested).toEqual({ email: '[redacted]', safe: 'ok' });
  });

  it('batchesInProduction', async () => {
    const transport = vi.fn();
    configureErrorReporting({ env: 'production', transport, flushMs: 1 });
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    render(
      <ComponentErrorBoundary context={{ componentId: 'grid', uiTraceId: 'trace-prod-1' }}>
        <Broken />
      </ComponentErrorBoundary>,
    );

    await act(async () => {
      await flushErrorReports();
    });

    expect(transport).toHaveBeenCalledWith([expect.objectContaining({ context: expect.objectContaining({ uiTraceId: 'trace-prod-1' }) })]);
  });
});

describe('ErrorCodesTest', () => {
  it('classifiesNetworkErrors', () => {
    expect(classifyError(new ApiError(503, 'UNAVAILABLE', 'Temporarily unavailable'))).toBe(ErrorCodes.NETWORK_ERROR);
    expect(classifyError(new TypeError('Failed to fetch'))).toBe(ErrorCodes.NETWORK_ERROR);
  });

  it('classifiesPermissionErrors', () => {
    expect(classifyError(new ApiError(403, 'FORBIDDEN', 'Denied'))).toBe(ErrorCodes.PERMISSION_DENIED);
    expect(classifyError(new ApiError(401, 'UNAUTHORIZED', 'Session expired'))).toBe(ErrorCodes.SESSION_EXPIRED);
  });
});
