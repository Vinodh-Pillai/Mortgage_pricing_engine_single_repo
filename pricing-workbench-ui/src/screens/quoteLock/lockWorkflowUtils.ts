export function countdownWarning(expiresAt: string, now = new Date()) {
  const msRemaining = new Date(expiresAt).getTime() - now.getTime();
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
