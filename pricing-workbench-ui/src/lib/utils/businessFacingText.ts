export function businessFacingText(value: string | null | undefined): string {
  if (!value) return 'Not provided';
  return value
    .replace(/no published tenant application form version is (?:configured|available)\.?/gi, 'Setup support is needed.')
    .replace(/published tenant application form version (?:did not include field groups|is temporarily unavailable)\.?/gi, 'Setup support is needed.')
    .replace(/catalog dropdown configuration returned no selectable options\.?/gi, 'Some product choices are unavailable.')
    .replace(/tenant product dropdown configuration returned no selectable options\.?/gi, 'Some product choices are unavailable.')
    .replace(/field library|field-library|application form configuration|tenant application form configuration/gi, 'application fields')
    .replace(/configured\/?api|configured api|configured\/API/gi, 'connected')
    .replace(/configured eligibility-service|eligibility-service/gi, 'eligibility service')
    .replace(/configured pricing|pricing-service/gi, 'pricing service')
    .replace(/backend|bff/gi, 'connected service')
    .replace(/admin|administrator/gi, 'support')
    .replace(/builder|form builder|field editor/gi, 'application setup')
    .replace(/configuration blocker/gi, 'setup needed')
    .replace(/configuration|configured/gi, 'available')
    .replace(/load-state|loading/gi, 'loading')
    .replace(/blocked|unavailable-contract|blocked-evidence/gi, 'needs attention')
    .replace(/audit-evidence|replay-evidence|export-evidence|recommendation-evidence|floor-evidence|evidence-panels/gi, 'review records')
    .replace(/backend-recalculation/gi, 'connected recalculation')
    .replace(/dlq/gi, 'exception queue')
    .replace(/feed-adapters?/gi, 'investor delivery connections')
    .replace(/sftp-adapters?/gi, 'partner file delivery')
    .replace(/[._/-]+/g, ' ');
}
