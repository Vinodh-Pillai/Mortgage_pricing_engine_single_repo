export type RateSheetValidationIssue = {
  rowNumber: number | null;
  column: string;
  severity: 'BLOCKING' | 'WARNING' | string;
  message: string;
};

export type RateSheetParsedRow = {
  rowId: string;
  rowNumber: number | null;
  productRef: string;
  rateRef: string;
  status: string;
  validationIssues: RateSheetValidationIssue[];
};

export type RateSheetUploadResult = {
  uploadId: string;
  status: string;
  sourceHash: string;
  validationResultHash: string;
  versionHash: string;
  parsedRows: RateSheetParsedRow[];
  validationIssues: RateSheetValidationIssue[];
  publishReady: boolean;
  auditRefs: string[];
  uiTraceId: string;
  structuralAnalysis?: RateSheetStructuralAnalysis | null;
};

export type RateSheetStructuralAnalysis = {
  status: string;
  formatType: string;
  confidence: string;
  sourcePreview?: unknown;
  mappings?: unknown[];
  profileMatch?: unknown;
};

export type RateSheetUploadMetadata = {
  investorId: string;
  channelId: string;
  productCode: string;
  effectiveAt: string;
};

export type RateSheetPublishResult = {
  status: 'PUBLISHED' | 'BLOCKED' | string;
  message: string;
  auditRefs: string[];
};

export type RateSheetPublishOptions = {
  expectedValidationResultHash?: string;
  expectedVersionHash?: string;
};

const traceHeaders = {
  Accept: 'application/json',
  'X-Ui-Trace-Id': 'rate-sheet-live-intake-ui',
};

export async function uploadRateSheetForParsing(
  tenantId: string,
  file: File,
  sourceHash: string,
  metadata: RateSheetUploadMetadata,
  fetchImpl: typeof fetch = fetch,
): Promise<RateSheetUploadResult> {
  const form = new FormData();
  form.append('file', file);
  form.append('sourceHash', sourceHash);
  form.append('investorId', metadata.investorId);
  form.append('channelId', metadata.channelId);
  form.append('productCode', metadata.productCode);
  form.append('effectiveAt', metadata.effectiveAt);
  const response = await fetchImpl(`/api/v1/tenants/${encodeURIComponent(tenantId)}/rate-sheets/uploads`, {
    method: 'POST',
    headers: traceHeaders,
    body: form,
  });
  if (response.status === 404) throw new Error('Rate sheet upload/parse contract is not available from the backend.');
  if (response.status === 422 || response.status === 400) {
    const blocked = normalizeUploadResult(await response.json(), sourceHash);
    throw new Error(blocked.validationIssues[0]?.message || 'Rate sheet upload is blocked by backend validation.');
  }
  if (!response.ok) throw new Error('Rate sheet upload/parse is temporarily unavailable.');
  return normalizeUploadResult(await response.json(), sourceHash);
}

export async function validateRateSheetUpload(
  tenantId: string,
  uploadId: string,
  fetchImpl: typeof fetch = fetch,
): Promise<RateSheetUploadResult> {
  const response = await fetchImpl(`/api/v1/tenants/${encodeURIComponent(tenantId)}/rate-sheets/uploads/${encodeURIComponent(uploadId)}/validation`, {
    headers: traceHeaders,
  });
  if (response.status === 404) throw new Error('Rate sheet validation contract is not available from the backend.');
  if (!response.ok) throw new Error('Rate sheet validation is temporarily unavailable.');
  return normalizeUploadResult(await response.json(), '');
}

export async function publishRateSheetUpload(
  tenantId: string,
  uploadId: string,
  optionsOrFetch: RateSheetPublishOptions | typeof fetch = {},
  fetchOverride?: typeof fetch,
): Promise<RateSheetPublishResult> {
  const fetchImpl = typeof optionsOrFetch === 'function' ? optionsOrFetch : fetchOverride ?? fetch;
  const options = typeof optionsOrFetch === 'function' ? {} : optionsOrFetch;
  const response = await fetchImpl(`/api/v1/tenants/${encodeURIComponent(tenantId)}/rate-sheets/uploads/${encodeURIComponent(uploadId)}/publish`, {
    method: 'POST',
    headers: {
      ...traceHeaders,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      uploadId,
      expectedValidationResultHash: options.expectedValidationResultHash,
      expectedVersionHash: options.expectedVersionHash,
    }),
  });
  if (response.status === 404) return { status: 'BLOCKED', message: 'Rate sheet publish contract is not available from the backend.', auditRefs: [] };
  if (!response.ok) throw new Error('Rate sheet publish is temporarily unavailable.');
  const raw = objectRecord(await response.json());
  return {
    status: stringValue(raw.status) || 'BLOCKED',
    message: stringValue(raw.message) || 'Publish response did not include a message.',
    auditRefs: stringArray(raw.auditRefs ?? raw.evidenceRefs),
  };
}

function normalizeUploadResult(raw: unknown, sourceHash: string): RateSheetUploadResult {
  const record = objectRecord(raw);
  const rows = arrayValue(record.parsedRows ?? record.rows ?? record.validationRows) ?? [];
  const issues = arrayValue(record.validationIssues ?? record.issues) ?? [];
  const parsedRows = rows.map(normalizeParsedRow).filter((row): row is RateSheetParsedRow => Boolean(row));
  const validationIssues = issues.map(normalizeIssue);
  return {
    uploadId: stringValue(record.uploadId ?? record.batchId ?? record.id),
    status: stringValue(record.status) || (parsedRows.length > 0 ? 'PARSED' : 'BLOCKED'),
    sourceHash: stringValue(record.sourceHash) || sourceHash,
    validationResultHash: stringValue(record.validationResultHash ?? record.expectedValidationResultHash ?? objectRecord(record.validationResult).resultHash),
    versionHash: stringValue(record.versionHash ?? record.expectedVersionHash ?? record.resultHash),
    parsedRows,
    validationIssues,
    publishReady: Boolean(record.publishReady ?? record.readyToPublish) && parsedRows.length > 0 && validationIssues.every((issue) => issue.severity !== 'BLOCKING'),
    auditRefs: stringArray(record.auditRefs ?? record.evidenceRefs),
    uiTraceId: stringValue(record.uiTraceId) || 'rate-sheet-live-intake-ui',
    structuralAnalysis: normalizeStructuralAnalysis(record.structuralAnalysis),
  };
}

function normalizeStructuralAnalysis(raw: unknown): RateSheetStructuralAnalysis | null {
  const record = objectRecord(raw);
  if (Object.keys(record).length === 0) return null;
  return {
    status: stringValue(record.status) || 'STRUCTURAL_ANALYSIS_READY',
    formatType: stringValue(record.formatType),
    confidence: stringValue(record.confidence) || 'LOW',
    sourcePreview: record.sourcePreview,
    mappings: Array.isArray(record.mappings) ? record.mappings : [],
    profileMatch: record.profileMatch,
  };
}

function normalizeParsedRow(raw: unknown): RateSheetParsedRow | null {
  const record = objectRecord(raw);
  const rowId = stringValue(record.rowId ?? record.id ?? record.sourceRowRef);
  if (!rowId) return null;
  return {
    rowId,
    rowNumber: numberValue(record.rowNumber ?? record.sourceRowNumber),
    productRef: stringValue(record.productRef ?? record.productCode) || 'product-ref-unavailable',
    rateRef: stringValue(record.rateRef ?? record.rateId) || 'rate-ref-unavailable',
    status: stringValue(record.status) || 'UNKNOWN',
    validationIssues: (arrayValue(record.validationIssues ?? record.issues) ?? []).map(normalizeIssue),
  };
}

function normalizeIssue(raw: unknown): RateSheetValidationIssue {
  const record = objectRecord(raw);
  return {
    rowNumber: numberValue(record.rowNumber ?? record.sourceRowNumber),
    column: stringValue(record.column ?? record.field ?? record.fieldName) || 'row',
    severity: stringValue(record.severity) || 'BLOCKING',
    message: stringValue(record.message) || 'Backend validation issue did not include a message.',
  };
}

function objectRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function arrayValue(value: unknown): unknown[] | null {
  return Array.isArray(value) ? value : null;
}

function stringValue(value: unknown) {
  if (value === null || value === undefined) return '';
  return String(value);
}

function stringArray(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return value.map(stringValue).filter(Boolean);
}

function numberValue(value: unknown): number | null {
  const parsed = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}
