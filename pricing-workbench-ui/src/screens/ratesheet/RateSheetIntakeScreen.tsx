import { useState } from 'react';
import { MajorFunctionalityPage, type EvidenceCapture, type FunctionalityPageConfig } from '../shared/MajorFunctionalityPage';
import type { ScreenVisualState } from '../contract/ScreenProps';

type RateSheetRow = { id: string; investor: string; effectiveDate: string; status: string };

export const rateSheetIntakeEvidenceTarget = '.local-harness/evidence/PII-25-S04/rate-sheet-intake.json';

const rateSheetConfig: FunctionalityPageConfig<RateSheetRow> = {
  screenId: 'rate-sheet-intake',
  evidenceTarget: rateSheetIntakeEvidenceTarget,
  breadcrumb: 'Pricing / Rate sheets',
  eyebrow: 'Rate sheet service',
  title: 'Rate Sheet Intake',
  summary: 'Supports rate sheet upload, validation review, investor mapping, effective dates, and publish readiness.',
  dataBoundary: 'rate-sheet-service: GET/PATCH /api/v1/rate-sheets',
  sections: [
    { id: 'upload-import', eyebrow: 'Import', title: 'Upload/Import', summary: 'File intake and upload progress.', status: 'ready', items: ['Select file', 'Upload progress', 'File validation'] },
    { id: 'validation-results', eyebrow: 'Validation', title: 'Validation Results', summary: 'Row-level errors and remediation refs.', status: 'needs-attention', items: ['Schema result', 'Row errors', 'Remediation owner'] },
    { id: 'rate-grid', eyebrow: 'Grid', title: 'Rate Grid', summary: 'Read-only grid metadata from backend.', status: 'blocked', items: ['Grid ref', 'Cell lineage', 'Source version'] },
    { id: 'investor-mapping', eyebrow: 'Investor', title: 'Investor Mapping', summary: 'Investor mapping references.', status: 'needs-attention', items: ['Investor key', 'Mapping status', 'Review ref'] },
    { id: 'effective-dates', eyebrow: 'Timing', title: 'Effective Dates', summary: 'Effective date windows from service metadata.', status: 'ready', items: ['Start date ref', 'Cutoff ref', 'Timezone ref'] },
    { id: 'publish-workflow', eyebrow: 'Publish', title: 'Publish Workflow', summary: 'Publish approval and replay evidence.', status: 'empty', items: ['Approver', 'Publish event', 'Rollback ref'] },
  ],
  metrics: [
    { label: 'Upload', value: 'Ready', help: 'Drag/drop and browse supported' },
    { label: 'Validation', value: 'Row-level', help: 'Remediation table included' },
    { label: 'Publish', value: 'Guarded', help: 'Publishing stays disabled until required review is complete.' },
  ],
  rows: [
    { id: 'rs-001', investor: 'Investor mapping A', effectiveDate: 'service-owned', status: 'needs-attention' },
    { id: 'rs-002', investor: 'Investor mapping B', effectiveDate: 'service-owned', status: 'ready' },
    { id: 'rs-003', investor: 'Investor mapping C', effectiveDate: 'service-owned', status: 'blocked' },
  ],
  columns: [
    { key: 'investor', header: 'Investor mapping' },
    { key: 'effectiveDate', header: 'Effective date source' },
    { key: 'status', header: 'Status', render: (row) => <span className={`functionality-badge functionality-badge--${row.status}`}>{row.status}</span> },
  ],
  tableCaption: 'Rate sheet intake records',
  primaryActions: [{ id: 'upload-rate-sheet', label: 'Upload rate sheet', variant: 'primary' }],
  secondaryActions: [{ id: 'validate-rate-sheet', label: 'Validate rows' }, { id: 'publish-rate-sheet', label: 'Publish', disabled: true }],
  emptyMessage: 'No rate sheet uploads are available for this pricing workspace.',
  blockedMessage: 'Rate sheet intake is blocked until rate-sheet-service grid contracts are available.',
  attentionMessage: 'Validation and investor mapping rows need remediation.',
};

export function RateSheetIntakeScreen({ visualState, onEvidenceCapture }: { visualState?: ScreenVisualState; onEvidenceCapture?: EvidenceCapture }) {
  const [uploadLabel, setUploadLabel] = useState('No file selected');
  const config = {
    ...rateSheetConfig,
    renderSpotlight: (recordAction: (actionId: string) => void) => (
      <section className="section-card" aria-labelledby="rate-sheet-upload-heading">
        <h2 id="rate-sheet-upload-heading">Drag-drop file upload</h2>
        <label className="drop-zone">
          <strong>Drop a rate sheet or browse</strong>
          <span>{uploadLabel}</span>
          <input aria-label="Rate sheet file" type="file" onChange={(event) => { const file = event.target.files?.[0]; setUploadLabel(file ? `${file.name} uploaded 100%` : 'No file selected'); recordAction('rate-sheet-file-selected'); }} />
        </label>
      </section>
    ),
  } satisfies FunctionalityPageConfig<RateSheetRow>;
  return <MajorFunctionalityPage config={config} visualState={visualState} onEvidenceCapture={onEvidenceCapture} />;
}

export default RateSheetIntakeScreen;
