import type { ReactNode } from 'react';

export function businessFacingText(value: string | number | null | undefined) {
  if (value === null || value === undefined || value === '') return 'Not provided';
  return String(value)
    .replace(/BFF/gi, 'workbench service')
    .replace(/backend[- ]owned/gi, 'authoritative')
    .replace(/backend/gi, 'connected service')
    .replace(/dependency status/gi, 'setup status')
    .replace(/contract/gi, 'setup')
    .replace(/audit/gi, 'review')
    .replace(/replay hash|replay/gi, 'review reference')
    .replace(/[_:./-]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

export function serviceReadinessText(value: string | null | undefined) {
  return value ? 'Configuration needed before live service use.' : 'Not provided';
}

export function ChipList({ label, values }: { label: string; values: Array<string | number | null | undefined> }) {
  const visible = values.filter((value): value is string | number => value !== null && value !== undefined && value !== '');
  if (visible.length === 0) return null;
  return (
    <ul className="chip-list" aria-label={label}>
      {visible.map((value) => <li key={String(value)}>{String(value)}</li>)}
    </ul>
  );
}

export function StatusBadge({ children, tone = 'info' }: { children: ReactNode; tone?: 'info' | 'blocked' | 'success' }) {
  const className = tone === 'blocked' ? 'status-card status-card--offline' : 'status-card';
  return <span className={className}>{children}</span>;
}
