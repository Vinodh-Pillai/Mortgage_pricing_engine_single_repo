import { useMemo, useState } from 'react';
import { publishRateSheetUpload, uploadRateSheetForParsing, validateRateSheetUpload, type RateSheetPublishResult, type RateSheetUploadMetadata, type RateSheetUploadResult } from '../../lib/api/rateSheetIntake';
import { useOptionalTenantId } from '../../lib/data/tenant';
import type { EvidenceCapture } from '../shared/MajorFunctionalityPage';

type FileInspection = {
  name: string;
  extension: string;
  sizeBytes: number;
  lastModified: string;
  hash: string;
  hashStatus: 'pending' | 'available' | 'unavailable';
};

type BackendState =
  | { kind: 'idle'; message: string }
  | { kind: 'uploading'; message: string }
  | { kind: 'loaded'; result: RateSheetUploadResult }
  | { kind: 'blocked'; message: string };

const supportedInspectionExtensions = new Set(['csv', 'pdf', 'xlsm', 'xlsx']);
const pdfOcrBlockedMessage = 'PDF/OCR rate sheet intake requires an approved external document extractor/OCR handoff. This repository currently exposes rate-feed OCR review contracts but no PDF table extraction adapter, so PDF upload is blocked before parser execution.';

export const rateSheetIntakeEvidenceTarget = '.local-harness/evidence/PII-25-S04/rate-sheet-intake.json';

export function RateSheetIntakeScreen({ onEvidenceCapture }: { onEvidenceCapture?: EvidenceCapture }) {
  const tenantId = useOptionalTenantId();
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [inspection, setInspection] = useState<FileInspection | null>(null);
  const [metadata, setMetadata] = useState<RateSheetUploadMetadata>({ investorId: '', channelId: '', productCode: '', effectiveAt: '' });
  const [backendState, setBackendState] = useState<BackendState>({ kind: 'idle', message: 'Select a file, then upload it to the backend parser.' });
  const [publishResult, setPublishResult] = useState<RateSheetPublishResult | null>(null);
  const selectedFileSummary = useMemo(() => {
    if (!inspection) return 'No file selected';
    return `${inspection.name} · ${inspection.extension.toUpperCase()} · ${formatBytes(inspection.sizeBytes)}`;
  }, [inspection]);

  async function inspectFile(file: File | undefined) {
    setPublishResult(null);
    setBackendState({ kind: 'idle', message: 'Upload to backend parser before validation or publish.' });
    setSelectedFile(file ?? null);
    if (!file) {
      setInspection(null);
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
    try {
      const hash = await hashFile(file);
      setInspection({ ...nextInspection, hash, hashStatus: 'available' });
    } catch {
      setInspection({ ...nextInspection, hash: 'hash unavailable in this browser session', hashStatus: 'unavailable' });
    }
  }

  async function uploadAndParse() {
    if (!selectedFile || !inspection) {
      setBackendState({ kind: 'blocked', message: 'Select a CSV, XLSM, XLSX, or PDF file before backend upload.' });
      return;
    }
    if (!tenantId) {
      setBackendState({ kind: 'blocked', message: 'Select a tenant context before uploading a rate sheet.' });
      return;
    }
    if (inspection.hashStatus !== 'available') {
      setBackendState({ kind: 'blocked', message: inspection.hashStatus === 'pending' ? 'Wait for source hash calculation before backend upload.' : 'Source hash is unavailable; select the file again or use a browser session with file hashing support.' });
      return;
    }
    if (!supportedInspectionExtensions.has(inspection.extension)) {
      setBackendState({ kind: 'blocked', message: `${inspection.extension.toUpperCase()} is not an accepted rate sheet intake format. Supported repo-backed formats are CSV parser upload and XLSX/XLSM structural/profile analysis; PDF/OCR remains externally blocked.` });
      return;
    }
    if (inspection.extension === 'pdf') {
      setBackendState({ kind: 'blocked', message: pdfOcrBlockedMessage });
      return;
    }
    if (inspection.extension === 'csv' && (!metadata.investorId.trim() || !metadata.channelId.trim() || !metadata.productCode.trim() || !metadata.effectiveAt.trim())) {
      setBackendState({ kind: 'blocked', message: 'CSV parser upload requires investorId, channelId, productCode, and effectiveAt supplied by the user. The UI will not invent rate-feed metadata.' });
      return;
    }
    setBackendState({ kind: 'uploading', message: 'Uploading source file to backend parser...' });
    try {
      const result = await uploadRateSheetForParsing(tenantId, selectedFile, inspection.hash, metadata);
      setBackendState({ kind: 'loaded', result });
      capture(result, []);
    } catch (error: unknown) {
      setBackendState({ kind: 'blocked', message: error instanceof Error ? error.message : 'Backend upload/parse is unavailable.' });
    }
  }

  async function refreshValidation() {
    if (backendState.kind !== 'loaded' || !backendState.result.uploadId) return;
    if (!tenantId) {
      setBackendState({ kind: 'blocked', message: 'Select a tenant context before refreshing validation.' });
      return;
    }
    setBackendState({ kind: 'uploading', message: 'Loading backend row validation...' });
    try {
      const result = await validateRateSheetUpload(tenantId, backendState.result.uploadId);
      setBackendState({ kind: 'loaded', result });
      capture(result, []);
    } catch (error: unknown) {
      setBackendState({ kind: 'blocked', message: error instanceof Error ? error.message : 'Backend row validation is unavailable.' });
    }
  }

  async function publish() {
    if (backendState.kind !== 'loaded' || !backendState.result.uploadId || !backendState.result.publishReady) return;
    if (!tenantId) {
      setPublishResult({ status: 'BLOCKED', message: 'Select a tenant context before publishing a rate sheet.', auditRefs: [] });
      return;
    }
    try {
      const result = await publishRateSheetUpload(tenantId, backendState.result.uploadId, {
        expectedValidationResultHash: backendState.result.validationResultHash,
        expectedVersionHash: backendState.result.versionHash,
      });
      setPublishResult(result);
      capture(backendState.result, result.auditRefs);
    } catch (error: unknown) {
      setPublishResult({ status: 'BLOCKED', message: error instanceof Error ? error.message : 'Backend publish is unavailable.', auditRefs: [] });
    }
  }

  function capture(result: RateSheetUploadResult, publishRefs: string[]) {
    const refs = [rateSheetIntakeEvidenceTarget, result.uploadId, result.sourceHash, result.uiTraceId, ...result.auditRefs, ...publishRefs].filter(Boolean);
    onEvidenceCapture?.({
      screenId: 'rate-sheet-intake',
      timestamp: new Date().toISOString(),
      state: result.publishReady ? 'ready' : result.parsedRows.length > 0 ? 'needs-attention' : 'blocked',
      dataRefs: refs,
      blockers: result.validationIssues.filter((issue) => issue.severity === 'BLOCKING').map((issue) => issue.message),
      evidenceTarget: rateSheetIntakeEvidenceTarget,
      refs,
    });
  }

  const parsedRows = backendState.kind === 'loaded' ? backendState.result.parsedRows : [];
  const validationIssues = backendState.kind === 'loaded' ? backendState.result.validationIssues : [];
  const publishReady = backendState.kind === 'loaded' && backendState.result.publishReady;
  const hashReady = inspection?.hashStatus === 'available';
  const uploadDisabled = !selectedFile || !hashReady || backendState.kind === 'uploading' || !tenantId;

  return (
    <main className="functionality-page" data-screen-id="rate-sheet-intake" aria-labelledby="rate-sheet-title">
      <section className="hero" aria-labelledby="rate-sheet-title">
        <p className="eyebrow">Rate sheet service</p>
        <h1 id="rate-sheet-title">Rate Sheet Intake</h1>
        <p>Upload a rate sheet to backend parser/validation APIs. The browser inspects metadata and source hash only; it does not invent parser rows or publish unparsed files.</p>
      </section>

      <section className="panel" aria-labelledby="rate-sheet-upload-heading">
        <h2 id="rate-sheet-upload-heading">Backend upload and parse</h2>
        <label className="drop-zone">
          <strong>Select CSV, XLSM, XLSX, or PDF rate sheet source</strong>
          <span>{selectedFileSummary}</span>
          <input aria-label="Rate sheet source file" type="file" accept=".csv,.pdf,.xlsm,.xlsx" onChange={(event) => void inspectFile(event.target.files?.[0])} />
        </label>
        <div className="form-grid" aria-label="CSV parser metadata">
          <label>Investor ID<input aria-label="Investor ID" value={metadata.investorId} onChange={(event) => setMetadata({ ...metadata, investorId: event.target.value })} placeholder="UUID from rate-feed configuration" /></label>
          <label>Channel ID<input aria-label="Channel ID" value={metadata.channelId} onChange={(event) => setMetadata({ ...metadata, channelId: event.target.value })} placeholder="UUID from rate-feed configuration" /></label>
          <label>Product code<input aria-label="Product code" value={metadata.productCode} onChange={(event) => setMetadata({ ...metadata, productCode: event.target.value })} placeholder="Configured product code" /></label>
          <label>Effective at<input aria-label="Effective at" value={metadata.effectiveAt} onChange={(event) => setMetadata({ ...metadata, effectiveAt: event.target.value })} placeholder="ISO-8601 instant" /></label>
        </div>
        <dl aria-label="Selected file inspection">
          <dt>File name</dt><dd>{inspection?.name ?? 'Not selected'}</dd>
          <dt>Extension</dt><dd>{inspection?.extension.toUpperCase() ?? 'Not selected'}</dd>
          <dt>Size</dt><dd>{inspection ? formatBytes(inspection.sizeBytes) : 'Not selected'}</dd>
          <dt>Last modified</dt><dd>{inspection?.lastModified ?? 'Not selected'}</dd>
          <dt>Source hash</dt><dd>{inspection?.hash ?? 'Not selected'}</dd>
        </dl>
        {!tenantId ? <div className="banner banner--blocked" role="alert"><strong>Tenant context required</strong><span>Select a tenant before uploading a rate sheet.</span></div> : null}
        {inspection?.hashStatus === 'pending' ? <p role="status">Calculating source hash before upload is enabled...</p> : null}
        {inspection?.hashStatus === 'unavailable' ? <div className="banner banner--blocked" role="alert"><strong>Source hash unavailable</strong><span>Upload is disabled because the backend parser requires a concrete source hash.</span></div> : null}
        <div className="action-toolbar" aria-label="Rate sheet backend controls">
          <button type="button" className="ds-control ds-button ds-size-sm ds-variant-secondary" onClick={() => void uploadAndParse()} disabled={uploadDisabled}>{backendState.kind === 'uploading' ? 'Uploading...' : 'Upload and parse'}</button>
          <button type="button" className="ds-control ds-button ds-size-sm ds-variant-secondary" onClick={() => void refreshValidation()} disabled={backendState.kind !== 'loaded' || !backendState.result.uploadId}>Refresh validation</button>
          <button type="button" className="ds-control ds-button ds-size-sm ds-variant-primary" onClick={() => void publish()} disabled={!publishReady}>Publish</button>
        </div>
        {backendState.kind === 'blocked' ? <div className="banner banner--blocked" role="alert"><strong>Live backend blocked</strong><span>{backendState.message}</span></div> : <p role="status">{backendState.kind === 'loaded' ? `${parsedRows.length} parser-backed rows loaded.` : backendState.message}</p>}
        <p>Publish remains disabled until backend parser rows exist and blocking validation issues are resolved.</p>
        {backendState.kind === 'loaded' && backendState.result.structuralAnalysis ? <StructuralAnalysis analysis={backendState.result.structuralAnalysis} /> : null}
      </section>

      <section className="panel" aria-labelledby="rate-sheet-validation-heading">
        <h2 id="rate-sheet-validation-heading">Parser rows and validation</h2>
        {parsedRows.length === 0 ? <p role="status">No parser-backed rows are loaded. The UI will not fabricate rate rows.</p> : (
          <div className="quote-table" role="table" aria-label="Rate sheet parser rows">
            <div role="row" className="quote-table__row quote-table__row--head"><span role="columnheader">Row</span><span role="columnheader">Product</span><span role="columnheader">Rate</span><span role="columnheader">Status</span><span role="columnheader">Issues</span></div>
            {parsedRows.map((row) => <div key={row.rowId} role="row" className="quote-table__row"><span role="cell">{row.rowNumber ?? row.rowId}</span><span role="cell">{row.productRef}</span><span role="cell">{row.rateRef}</span><span role="cell">{row.status}</span><span role="cell"><IssueList issues={row.validationIssues} /></span></div>)}
          </div>
        )}
        <IssueList issues={validationIssues} />
      </section>

      <section className="panel" aria-labelledby="rate-sheet-publish-heading">
        <h2 id="rate-sheet-publish-heading">Publish activation and audit replay</h2>
        {publishResult ? <div className={publishResult.status === 'PUBLISHED' ? 'banner banner--success' : 'banner banner--blocked'} role={publishResult.status === 'PUBLISHED' ? 'status' : 'alert'}><strong>{publishResult.status}</strong><span>{publishResult.message}</span><RefList values={publishResult.auditRefs} /></div> : <p role="status">Publish has not run for this upload.</p>}
        {backendState.kind === 'loaded' ? <RefList values={backendState.result.auditRefs} /> : null}
      </section>
    </main>
  );
}

function IssueList({ issues }: { issues: Array<{ rowNumber: number | null; column: string; severity: string; message: string }> }) {
  if (issues.length === 0) return <p>No validation issues supplied.</p>;
  return <ul>{issues.map((issue, index) => <li key={`${issue.rowNumber ?? 'row'}-${issue.column}-${index}`}><strong>{issue.severity}</strong> row {issue.rowNumber ?? 'n/a'} {issue.column}: {issue.message}</li>)}</ul>;
}

function RefList({ values }: { values: string[] }) {
  if (values.length === 0) return null;
  return <ul>{values.map((value) => <li key={value}><code>{value}</code></li>)}</ul>;
}

function StructuralAnalysis({ analysis }: { analysis: NonNullable<RateSheetUploadResult['structuralAnalysis']> }) {
  const mappingCount = Array.isArray(analysis.mappings) ? analysis.mappings.length : 0;
  return <div className="banner banner--info" role="status"><strong>XLSX/XLSM structural/profile analysis</strong><span>{analysis.formatType || 'format'} · {analysis.confidence} confidence · {mappingCount} proposed mappings. Publish remains disabled until a CSV parser upload or approved downstream parser handoff supplies parser rows.</span></div>;
}

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

export default RateSheetIntakeScreen;
