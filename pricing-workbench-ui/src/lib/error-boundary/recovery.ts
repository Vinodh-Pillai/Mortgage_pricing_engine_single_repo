export type RecoveryWindow = Pick<Window, 'history' | 'location' | 'open'>;

export function retry(reset: () => void) {
  reset();
}

export function navigate(fallbackRoute?: string, win: RecoveryWindow = window) {
  if (fallbackRoute) {
    win.location.assign(fallbackRoute);
    return;
  }
  win.history.back();
}

export function refresh(win: Pick<Window, 'location'> = window) {
  win.location.reload();
}

export function contactSupport(error: Error, options: { uiTraceId?: string; email?: string; win?: Pick<Window, 'location'> } = {}) {
  const href = createSupportHref(error, options);
  (options.win ?? window).location.href = href;
  return href;
}

export function createSupportHref(error: Error, options: { uiTraceId?: string; email?: string } = {}) {
  const recipient = options.email ?? 'support@example.com';
  const subject = encodeURIComponent(`Pricing Workbench error ${options.uiTraceId ?? ''}`.trim());
  const body = encodeURIComponent(`Error: ${error.name}\nMessage: ${error.message}\nCorrelation ID: ${options.uiTraceId ?? 'unavailable'}`);
  return `mailto:${recipient}?subject=${subject}&body=${body}`;
}
