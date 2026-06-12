import type { DynamicRuleEvidenceSnapshot } from '../../lib/api/adminGovernance';

export function businessFacingText(value: string | null | undefined) {
  if (!value) return 'Not provided';
  return value
    .replace(/RBAC/gi, 'role access')
    .replace(/BFF/gi, 'workbench service')
    .replace(/upstream/gi, 'configured service')
    .replace(/downstream/gi, 'connected workflow')
    .replace(/dependency status/gi, 'setup status')
    .replace(/contract/gi, 'setup')
    .replace(/_/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

export function serviceReadinessText(value: string | null | undefined) {
  return value ? 'Configuration needed before live service use.' : 'Not provided';
}

export function ChipList({ label, values }: { label: string; values: string[] }) {
  const visible = values.filter(Boolean);
  if (visible.length === 0) return null;
  return (
    <ul className="chip-list" aria-label={label}>
      {visible.map((value) => <li key={value}>{value}</li>)}
    </ul>
  );
}

type RuleEvidenceRow = DynamicRuleEvidenceSnapshot['matchedRules'][number];

export function RuleEvidenceTable({ label, rows }: { label: string; rows: RuleEvidenceRow[] }) {
  return (
    <div className="quote-table" role="table" aria-label={label}>
      <div role="row" className="quote-table__row quote-table__row--head">
        <span role="columnheader">Rule</span>
        <span role="columnheader">Version</span>
        <span role="columnheader">Outcome</span>
        <span role="columnheader">Reason</span>
        <span role="columnheader">Facts</span>
      </div>
      {rows.map((row) => (
        <div key={`${row.ruleRef}-${row.reasonCode}`} role="row" className="quote-table__row">
          <span role="cell">{row.ruleRef}</span>
          <span role="cell">{row.versionRef}</span>
          <span role="cell">{row.outcome}</span>
          <span role="cell">{row.reasonCode}</span>
          <span role="cell"><ChipList label={`${row.ruleRef} fact references`} values={row.factRefs} /></span>
        </div>
      ))}
    </div>
  );
}

export function DisabledActionButton({ children, describedBy }: { children: string; describedBy?: string }) {
  return <button type="button" disabled aria-describedby={describedBy}>{children}</button>;
}
