import { useMemo, useState } from 'react';
import { MajorFunctionalityPage, type EvidenceCapture, type FunctionalityPageConfig } from '../shared/MajorFunctionalityPage';
import type { ScreenVisualState } from '../contract/ScreenProps';

type RateSheetRow = { id: string; investor: string; effectiveDate: string; status: string };

type FileInspection = {
  name: string;
  extension: string;
  sizeBytes: number;
  lastModified: string;
  hash: string;
  hashStatus: 'pending' | 'available' | 'unavailable';
};

type ValidationOutcome = {
  status: 'idle' | 'blocked' | 'parser-unimplemented' | 'unsupported-type';
  message: string;
  parsedRows: number;
};

const supportedInspectionExtensions = new Set(['pdf', 'xlsm', 'xlsx']);

function extensionFor(fileName: string) {
  const suffix = fileName.split('.').pop()?.trim().toLowerCase();
  return suffix && suffix !== fileName.toLowerCase() ? suffix : 'unknown';
}

function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

async function hashFile(file: File) {
  const bytes = new Uint8Array(await file.arrayBuffer());
  let hash = 0x811c9dc5;
  for (const byte of bytes) {
    hash ^= byte;
    hash = Math.imul(hash, 0x01000193) >>> 0;
  }
  return `fnv1a-32:${hash.toString(16).padStart(8, '0')}`;
}

function messageForValidation(inspection: FileInspection | null): ValidationOutcome {
  if (!inspection) {
    return { status: 'blocked', parsedRows: 0, message: 'Select a PDF, XLSM, or XLSX file before validation. No rows were parsed.' };
  }

  if (!supportedInspectionExtensions.has(inspection.extension)) {
    return {
      status: 'unsupported-type',
      parsedRows: 0,
      message: `${inspection.extension.toUpperCase()} is not an accepted rate sheet intake format for this workflow. Supported inspection types are PDF, XLSM, and XLSX.`,
    };
  }

  const typeName = inspection.extension.toUpperCase();
  return {
    status: 'parser-unimplemented',
    parsedRows: 0,
    message: `${typeName} file inspection succeeded for ${inspection.name}, but the ${typeName} row parser is not implemented in this UI. No rate rows were invented or staged; publish stays disabled until a tenant-scoped service parser returns parsed rows that pass validation.`,
  };
}

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
    { id: 'upload-import', eyebrow: 'Import', title: 'File Inspection', summary: 'Reads selected file metadata and source fingerprint before any parser work.', status: 'ready', items: ['Name, extension, and size', 'Source hash where available', 'No local rate invention'] },
    { id: 'validation-results', eyebrow: 'Validation', title: 'Validation Results', summary: 'Parser readiness and row-validation result.', status: 'needs-attention', items: ['PDF parser status', 'XLSM/XLSX parser status', 'Parsed-row gate'] },
    { id: 'rate-grid', eyebrow: 'Grid', title: 'Rate Grid', summary: 'Read-only grid metadata from backend.', status: 'blocked', items: ['Grid ref', 'Cell lineage', 'Source version'] },
    { id: 'investor-mapping', eyebrow: 'Investor', title: 'Investor Mapping', summary: 'Investor mapping references.', status: 'needs-attention', items: ['Investor key', 'Mapping status', 'Review ref'] },
    { id: 'effective-dates', eyebrow: 'Timing', title: 'Effective Dates', summary: 'Effective date windows from service metadata.', status: 'ready', items: ['Start date ref', 'Cutoff ref', 'Timezone ref'] },
    { id: 'publish-workflow', eyebrow: 'Publish', title: 'Publish Workflow', summary: 'Publish is blocked until tenant-scoped parsed rows pass validation.', status: 'empty', items: ['Tenant selection required', 'Validated parsed rows required', 'Publish writes governed rates through rate-sheet-service'] },
  ],
  metrics: [
    { label: 'File intake', value: 'Inspect only', help: 'The browser reads selected file metadata and a source hash where available.' },
    { label: 'Validation', value: 'Parser-gated', help: 'PDF/XLSM/XLSX parser availability is reported by file type; parsed rows are not fabricated.' },
    { label: 'Publish', value: 'Disabled', help: 'Publishing stays disabled until a tenant is selected and parsed rows pass validation.' },
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
  primaryActions: [],
  secondaryActions: [{ id: 'publish-rate-sheet', label: 'Publish', disabled: true }],
  emptyMessage: 'No rate sheet uploads are available for this pricing workspace.',
  blockedMessage: 'Rate sheet intake is blocked until rate-sheet-service grid contracts are available.',
  attentionMessage: 'Validation and investor mapping rows need remediation.',
};

export function RateSheetIntakeScreen({ visualState, onEvidenceCapture }: { visualState?: ScreenVisualState; onEvidenceCapture?: EvidenceCapture }) {
  const [inspection, setInspection] = useState<FileInspection | null>(null);
  const [validationOutcome, setValidationOutcome] = useState<ValidationOutcome>({ status: 'idle', parsedRows: 0, message: 'Validation has not run. Publish remains disabled.' });
  const selectedFileSummary = useMemo(() => {
    if (!inspection) return 'No file selected';
    return `${inspection.name} · ${inspection.extension.toUpperCase()} · ${formatBytes(inspection.sizeBytes)}`;
  }, [inspection]);

  async function inspectFile(file: File | undefined, recordAction: (actionId: string) => void) {
    if (!file) {
      setInspection(null);
      setValidationOutcome({ status: 'idle', parsedRows: 0, message: 'Validation has not run. Publish remains disabled.' });
      return;
    }

    const nextInspection: FileInspection = {
      name: file.name,
      extension: extensionFor(file.name),
      sizeBytes: file.size,
      lastModified: Number.isFinite(file.lastModified) ? new Date(file.lastModified).toISOString() : 'unknown',
      hash: 'hash pending',
      hashStatus: 'pending',
    };
    setInspection(nextInspection);
    setValidationOutcome({ status: 'idle', parsedRows: 0, message: 'Validation has not run. Publish remains disabled.' });
    recordAction('rate-sheet-file-inspected');

    try {
      const hash = await hashFile(file);
      setInspection({ ...nextInspection, hash, hashStatus: 'available' });
    } catch {
      setInspection({ ...nextInspection, hash: 'hash unavailable in this browser session', hashStatus: 'unavailable' });
    }
  }

  function validateSelectedFile(recordAction: (actionId: string) => void) {
    setValidationOutcome(messageForValidation(inspection));
    recordAction('rate-sheet-validate-rows');
  }

  const config = {
    ...rateSheetConfig,
    renderSpotlight: (recordAction: (actionId: string) => void) => (
      <section className="section-card" aria-labelledby="rate-sheet-upload-heading">
        <h2 id="rate-sheet-upload-heading">File inspection and publish gate</h2>
        <label className="drop-zone">
          <strong>Select PDF, XLSM, or XLSX rate sheet source</strong>
          <span>{selectedFileSummary}</span>
          <input aria-label="Rate sheet source file" type="file" accept=".pdf,.xlsm,.xlsx" onChange={(event) => void inspectFile(event.target.files?.[0], recordAction)} />
        </label>
        <dl aria-label="Selected file inspection">
          <dt>File name</dt><dd>{inspection?.name ?? 'Not selected'}</dd>
          <dt>Extension</dt><dd>{inspection?.extension.toUpperCase() ?? 'Not selected'}</dd>
          <dt>Size</dt><dd>{inspection ? formatBytes(inspection.sizeBytes) : 'Not selected'}</dd>
          <dt>Last modified</dt><dd>{inspection?.lastModified ?? 'Not selected'}</dd>
          <dt>Source hash</dt><dd>{inspection?.hash ?? 'Not selected'}</dd>
          <dt>Tenant gate</dt><dd>Tenant must be selected by the service workflow before parsed rates can publish.</dd>
          <dt>Parser status</dt><dd>{validationOutcome.status}</dd>
        </dl>
        <div className="action-toolbar" aria-label="Rate sheet validation controls">
          <button type="button" className="ds-control ds-button ds-size-sm ds-variant-secondary" onClick={() => validateSelectedFile(recordAction)}>Validate rows</button>
        </div>
        <p role="status">{validationOutcome.message}</p>
        <p>Parsed rows ready for publish: {validationOutcome.parsedRows}. Publish remains disabled until tenant-scoped parsed rows pass validation.</p>
        <p>When enabled, Publish sends validated parsed rows, the source hash, and tenant/effective-date metadata to rate-sheet-service for governed rate activation and audit replay. This UI does not publish unparsed local files.</p>
      </section>
    ),
  } satisfies FunctionalityPageConfig<RateSheetRow>;
  return <MajorFunctionalityPage config={config} visualState={visualState} onEvidenceCapture={onEvidenceCapture} />;
}

export default RateSheetIntakeScreen;
