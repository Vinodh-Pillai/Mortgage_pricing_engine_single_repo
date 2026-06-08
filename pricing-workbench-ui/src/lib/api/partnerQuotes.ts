export type PartnerQuoteSummary = {
  quoteId: string;
  borrowerLabel: string;
  status: string;
  slaState: string;
  lockState: string;
  errorFlags: string[];
};

export type PartnerQuoteAction = {
  visible: boolean;
  permitted: boolean;
  guidance: string;
  supportHandoffRoute: string;
};

export type PartnerQuoteDetail = PartnerQuoteSummary & {
  tenantContext: string;
  partnerId: string;
  lifecycleEvents: string[];
  actions: {
    reprice: PartnerQuoteAction;
  };
  uiTraceId: string;
};

export type PartnerQuoteListView = {
  partnerId: string;
  tenantContext: string;
  statusFilter: string;
  quotes: PartnerQuoteSummary[];
  uiTraceId: string;
  events: string[];
};

export type PartnerRepriceResult = {
  quoteId: string;
  status: 'BLOCKED' | 'ACCEPTED' | string;
  message: string;
  guidance: string;
  supportHandoffRoute: string;
  uiTraceId: string;
  events: string[];
};

const partnerTraceHeaders = {
  Accept: 'application/json',
  'X-Ui-Trace-Id': 'ch-s02-local-trace',
  'X-Tenant-Context': 'ui-preview-tenant',
};

export async function fetchPartnerQuotes(
  partnerId: string,
  status: string,
  fetchImpl: typeof fetch = fetch,
): Promise<PartnerQuoteListView> {
  const query = status ? `?status=${encodeURIComponent(status)}` : '';
  const response = await fetchImpl(`/api/v1/partners/${encodeURIComponent(partnerId)}/quotes${query}`, {
    headers: partnerTraceHeaders,
  });
  if (response.status >= 500) throw new Error('BFF partner quote list boundary is temporarily unavailable.');
  return (await response.json()) as PartnerQuoteListView;
}

export async function fetchPartnerQuoteDetail(
  partnerId: string,
  quoteId: string,
  fetchImpl: typeof fetch = fetch,
): Promise<PartnerQuoteDetail> {
  const response = await fetchImpl(`/api/v1/partners/${encodeURIComponent(partnerId)}/quotes/${encodeURIComponent(quoteId)}`, {
    headers: partnerTraceHeaders,
  });
  if (response.status >= 500) throw new Error('BFF partner quote detail boundary is temporarily unavailable.');
  return (await response.json()) as PartnerQuoteDetail;
}

export async function requestPartnerReprice(
  partnerId: string,
  quoteId: string,
  fetchImpl: typeof fetch = fetch,
): Promise<PartnerRepriceResult> {
  const response = await fetchImpl(`/api/v1/partners/${encodeURIComponent(partnerId)}/quotes/${encodeURIComponent(quoteId)}/reprice`, {
    method: 'POST',
    headers: {
      ...partnerTraceHeaders,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ requestedBy: 'partner-workbench' }),
  });
  return (await response.json()) as PartnerRepriceResult;
}
