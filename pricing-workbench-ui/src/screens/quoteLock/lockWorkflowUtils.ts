export function countdownWarning(expiresAt: string | null | undefined, now = new Date()) {
  if (!expiresAt) return { severity: 'warning', label: 'Expiration unavailable', text: 'Backend did not return lock expiration' };
  const msRemaining = new Date(expiresAt).getTime() - now.getTime();
  if (Number.isNaN(msRemaining)) return { severity: 'warning', label: 'Expiration unavailable', text: 'Backend returned an unreadable lock expiration' };
  if (msRemaining <= 0) return { severity: 'expired', label: 'Expired', text: 'Lock expired' };
  const hours = msRemaining / (60 * 60 * 1000);
  const label = hours >= 24 ? `${Math.ceil(hours / 24)}d remaining` : `${Math.ceil(hours)}h remaining`;
  if (hours <= 2) return { severity: 'critical', label, text: 'Critical: lock expires within 2 hours' };
  if (hours <= 24) return { severity: 'warning', label, text: 'Warning: lock expires within 24 hours' };
  if (hours <= 72) return { severity: 'info', label, text: 'Notice: lock expires within 72 hours' };
  return { severity: 'normal', label, text: 'Lock expiration tracked' };
}

export function valueText(value: string | number | null | undefined) {
  if (value === null || value === undefined || value === '') return 'N/A';
  return String(value);
}

export function dateTimeText(value: string | null | undefined) {
  if (!value) return 'Not returned by backend';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return 'Not returned by backend';
  return date.toLocaleString();
}
