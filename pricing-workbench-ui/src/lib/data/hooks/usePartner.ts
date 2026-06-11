import { fetchPartnerChannelWorkbench, fetchPartnerWebhookHealth, requestPartnerWebhookEndpointTest, requestPartnerWebhookReplay, requestPartnerWebhookSafetyToggle } from '../../api/partnerTransport';
import { fetchPartnerQuoteDetail, fetchPartnerQuotes, requestPartnerReprice } from '../../api/partnerQuotes';
import { queryKeys } from '../queryKeys';
import { useTypedInfiniteQuery, useTypedMutation, useTypedQuery } from './common';

export function usePartnerQuotesQuery(partnerId: string, status = '') {
  return useTypedQuery(queryKeys.partner.quotes(partnerId, status), () => fetchPartnerQuotes(partnerId, status), { staleTime: 30_000 });
}

export function usePartnerQuoteDetailQuery(partnerId: string, quoteId: string) {
  return useTypedQuery(queryKeys.partner.quoteDetail(partnerId, quoteId), () => fetchPartnerQuoteDetail(partnerId, quoteId), { staleTime: 30_000 });
}

export function usePartnerChannelWorkbenchQuery(partnerId: string) {
  return useTypedQuery(queryKeys.partner.channelWorkbench(partnerId), () => fetchPartnerChannelWorkbench(partnerId), { staleTime: 30_000 });
}

export function usePartnerWebhookHealthQuery(partnerId: string) {
  return useTypedQuery(queryKeys.partner.webhookHealth(partnerId), () => fetchPartnerWebhookHealth(partnerId), { staleTime: 30_000 });
}

export function usePartnerMutation<TData, TVariables>(mutationFn: (variables: TVariables) => Promise<TData>) {
  return useTypedMutation(mutationFn);
}

export function usePartnerRepriceMutation(partnerId: string) {
  return useTypedMutation((variables: { quoteId: string }) => requestPartnerReprice(partnerId, variables.quoteId));
}

export function usePartnerWebhookReplayMutation(partnerId: string) {
  return useTypedMutation((variables: { webhookId: string; eventId: string; correlationId: string; idempotencyConfirmed: boolean }) =>
    requestPartnerWebhookReplay(partnerId, variables.webhookId, variables.eventId, variables.correlationId, variables.idempotencyConfirmed),
  );
}

export function usePartnerWebhookEndpointTestMutation(partnerId: string) {
  return useTypedMutation((variables: { webhookId: string }) => requestPartnerWebhookEndpointTest(partnerId, variables.webhookId));
}

export function usePartnerWebhookSafetyToggleMutation(partnerId: string) {
  return useTypedMutation((variables: { webhookId: string; route: string; paused: boolean; confirmed: boolean }) =>
    requestPartnerWebhookSafetyToggle(partnerId, variables.webhookId, variables.route, variables.paused, variables.confirmed),
  );
}

export function usePartnerInfiniteQuery<TPage, TPageParam>(key: readonly unknown[], queryFn: (pageParam: TPageParam) => Promise<TPage>, initialPageParam: TPageParam) {
  return useTypedInfiniteQuery(key, queryFn, initialPageParam, { getNextPageParam: () => undefined });
}
