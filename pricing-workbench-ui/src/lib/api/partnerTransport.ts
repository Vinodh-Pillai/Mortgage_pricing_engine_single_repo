export type PartnerWebhookDeliveryAttempt = {
  webhookId: string;
  eventId: string;
  route: string;
  status: string;
  rootCauseCode: string;
  lastSuccessfulAt: string;
  failureReason: string;
  idempotencyKeyState: string;
  maskingIndicator: string;
  consentIndicator: string;
};

export type PartnerChannelWorkbenchItem = {
  itemId: string;
  label: string;
  state: string;
  retryState: string;
  dlqReason: string;
  payloadRedactionState: string;
  auditRefs: string[];
};

export type PartnerChannelWorkbenchTab = {
  tabId: string;
  label: string;
  route: string;
  status: string;
  recoveryOwner: string;
  items: PartnerChannelWorkbenchItem[];
};

export type PartnerServiceAccountBlockedState = {
  blocked: boolean;
  missingCapability: string;
  recoveryOwner: string;
  credentialExposure: string;
};

export type PartnerChannelWorkbenchView = {
  partnerId: string;
  tenantContext: string;
  dependencyStatus: string;
  tabs: PartnerChannelWorkbenchTab[];
  serviceAccount: PartnerServiceAccountBlockedState;
  fallbackReason: string;
  uiTraceId: string;
  events: string[];
};

export type PartnerSafetyToggle = {
  webhookId: string;
  route: string;
  paused: boolean;
  visibleState: string;
};

export type PartnerWebhookAction = {
  available: boolean;
  disabledReason: string;
  confirmationRequirement: string;
  supportHandoffRoute: string;
};

export type PartnerWebhookHealthView = {
  partnerId: string;
  tenantContext: string;
  retryHealthSummary: string;
  eventWindow: string;
  dlqSizeStatus: string;
  retryWindowStatus: string;
  deliveryAttempts: PartnerWebhookDeliveryAttempt[];
  safetyToggles: PartnerSafetyToggle[];
  replayAction: PartnerWebhookAction;
  endpointTestAction: PartnerWebhookAction;
  uiTraceId: string;
  events: string[];
};

export type PartnerWebhookActionResult = {
  webhookId: string;
  eventId: string | null;
  status: 'BLOCKED' | 'ACCEPTED' | string;
  message: string;
  guidance: string;
  downstreamExecuted: boolean;
  uiTraceId: string;
  events: string[];
};

export type PartnerSafetyToggleResult = {
  webhookId: string;
  route: string | null;
  paused: boolean;
  status: 'BLOCKED' | 'VISIBLE' | string;
  message: string;
  uiTraceId: string;
  events: string[];
};

const partnerTransportHeaders = {
  Accept: 'application/json',
  'X-Ui-Trace-Id': 'ch-s05-local-trace',
  'X-Tenant-Context': 'ui-preview-tenant',
};

export async function fetchPartnerWebhookHealth(
  partnerId: string,
  fetchImpl: typeof fetch = fetch,
): Promise<PartnerWebhookHealthView> {
  const response = await fetchImpl(`/api/v1/partners/${encodeURIComponent(partnerId)}/integrations/webhooks`, {
    headers: partnerTransportHeaders,
  });
  if (response.status >= 500) throw new Error('BFF partner transport boundary is temporarily unavailable.');
  return (await response.json()) as PartnerWebhookHealthView;
}

export async function fetchPartnerChannelWorkbench(
  partnerId: string,
  fetchImpl: typeof fetch = fetch,
): Promise<PartnerChannelWorkbenchView> {
  const response = await fetchImpl(`/api/v1/partners/${encodeURIComponent(partnerId)}/integrations/workbench`, {
    headers: partnerTransportHeaders,
  });
  if (response.status >= 500) throw new Error('BFF partner integration workbench boundary is temporarily unavailable.');
  return (await response.json()) as PartnerChannelWorkbenchView;
}

export async function requestPartnerWebhookReplay(
  partnerId: string,
  webhookId: string,
  eventId: string,
  correlationId: string,
  idempotencyConfirmed: boolean,
  fetchImpl: typeof fetch = fetch,
): Promise<PartnerWebhookActionResult> {
  const response = await fetchImpl(`/api/v1/partners/${encodeURIComponent(partnerId)}/integrations/webhooks/${encodeURIComponent(webhookId)}/replay`, {
    method: 'POST',
    headers: {
      ...partnerTransportHeaders,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ eventId, correlationId, idempotencyConfirmed }),
  });
  return (await response.json()) as PartnerWebhookActionResult;
}

export async function requestPartnerWebhookEndpointTest(
  partnerId: string,
  webhookId: string,
  fetchImpl: typeof fetch = fetch,
): Promise<PartnerWebhookActionResult> {
  const response = await fetchImpl(`/api/v1/partners/${encodeURIComponent(partnerId)}/integrations/webhooks/${encodeURIComponent(webhookId)}/test`, {
    method: 'POST',
    headers: partnerTransportHeaders,
  });
  return (await response.json()) as PartnerWebhookActionResult;
}

export async function requestPartnerWebhookSafetyToggle(
  partnerId: string,
  webhookId: string,
  route: string,
  paused: boolean,
  confirmed: boolean,
  fetchImpl: typeof fetch = fetch,
): Promise<PartnerSafetyToggleResult> {
  const response = await fetchImpl(`/api/v1/partners/${encodeURIComponent(partnerId)}/integrations/webhooks/${encodeURIComponent(webhookId)}/safety`, {
    method: 'POST',
    headers: {
      ...partnerTransportHeaders,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ route, paused, confirmed }),
  });
  return (await response.json()) as PartnerSafetyToggleResult;
}
