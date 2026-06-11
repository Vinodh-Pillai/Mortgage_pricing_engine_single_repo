export function businessFacingText(value: string | null | undefined): string {
  if (!value) return 'Not provided';
  return value
    .replace(/load-state|loading/gi, 'loading')
    .replace(/blocked|unavailable-contract|blocked-evidence/gi, 'needs attention')
    .replace(/audit-evidence|replay-evidence|export-evidence|recommendation-evidence|floor-evidence|evidence-panels/gi, 'review records')
    .replace(/backend-recalculation/gi, 'connected recalculation')
    .replace(/dlq/gi, 'exception queue')
    .replace(/feed-adapters?/gi, 'investor delivery connections')
    .replace(/sftp-adapters?/gi, 'partner file delivery')
    .replace(/[._/-]+/g, ' ');
}