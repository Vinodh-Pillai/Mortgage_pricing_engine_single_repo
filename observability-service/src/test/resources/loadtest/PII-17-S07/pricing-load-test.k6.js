import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { Rate } from 'k6/metrics';

const fixture = new SharedArray('pricing-load-test-fixture', () => [JSON.parse(open('./pricing-load-test-fixture.json'))])[0];
const baseUrl = __ENV.PRICING_BASE_URL;
const tenantOverride = __ENV.PRICING_TENANT_ID;
const apiPrefix = __ENV.PRICING_API_PREFIX || fixture.quoteApi.defaultApiPrefix;
const quotePath = __ENV.PRICING_QUOTE_PATH || fixture.quoteApi.defaultQuotePath;
const expectedResponses = fixture.quoteApi.expectedResponseStatuses;
const unexpectedResponseRate = new Rate('quote_unexpected_response_rate');

export const options = {
  scenarios: {
    smoke: {
      executor: 'constant-vus',
      vus: Number(__ENV.PRICING_LOAD_VUS || 1),
      duration: __ENV.PRICING_LOAD_DURATION || '1m',
      tags: { profile: __ENV.PRICING_LOAD_PROFILE || 'smoke' }
    }
  },
  thresholds: {
    'http_req_duration{scenarioType:warm-cache-repeat-quotes}': ['p(95)<=500'],
    'http_req_duration{scenarioType:cold-cache-unique-quotes}': ['p(95)<=1500', 'p(99)<=2500'],
    'quote_unexpected_response_rate': ['rate<0.005']
  }
};

export default function () {
  if (!baseUrl) {
    throw new Error('PRICING_BASE_URL is required; do not hardcode local ports or credentials in the script');
  }
  const scenarioType = chooseScenarioType(__VU + __ITER);
  const tenantId = tenantOverride || fixture.tenants[(__VU + __ITER) % fixture.tenants.length];
  const correlationId = `corr-${scenarioType}-${__VU}-${__ITER}`;
  const idempotencyKey = `idem-${scenarioType}-${__VU}-${__ITER}`;
  const refs = fixture.seededQuoteRequestRefs;
  const payload = {
    scenarioId: refs.scenarioId,
    scenarioVersion: refs.scenarioVersion,
    requestedLockPeriods: refs.requestedLockPeriods,
    filters: {
      productTypes: fixture.scenarioLabels,
      investors: refs.investors,
      channels: refs.channels,
      propertyStates: refs.propertyStates
    },
    presentationCurrency: refs.presentationCurrency,
    clientContext: {
      requestId: `load-${scenarioType}-${__VU}-${__ITER}`,
      sourceSystem: refs.sourceSystem,
      scenarioType,
      sourceRefs: fixture.sourceRefs,
      privacy: fixture.privacy,
      syntheticFixtureMetadata: fixture.syntheticFixtureMetadata,
      referenceDataVersionRef: fixture.sourceRefs.referenceDataVersionRef
    },
    actorId: refs.actorId,
    idempotencyKey,
    correlationId,
    effectiveDate: refs.effectiveDate
  };

  const response = http.post(
      `${baseUrl}${apiPrefix}/tenants/${tenantId}${quotePath}`,
      JSON.stringify(payload),
      {
        headers: {
          'Content-Type': 'application/json',
          'X-Correlation-Id': correlationId,
          'Idempotency-Key': idempotencyKey
        },
        tags: { scenarioType, endpoint: 'quote-create' }
      });

  const expectedStatus = isExpectedResponseStatus(response.status);
  unexpectedResponseRate.add(!expectedStatus, { scenarioType, endpoint: 'quote-create' });
  check(response, {
    'status is successful, contract rejection, rate limit, or dependency unavailable': () => expectedStatus
  });
  sleep(Number(__ENV.PRICING_LOAD_SLEEP_SECONDS || 1));
}

function isExpectedResponseStatus(status) {
  return expectedResponses.successRanges.some(range => status >= range.min && status <= range.max)
      || expectedResponses.contractRejections.includes(status)
      || expectedResponses.boundedOperationalResponses.includes(status);
}

function chooseScenarioType(seed) {
  const weighted = Object.entries(fixture.scenarioMix);
  const slot = seed % 100;
  let cursor = 0;
  for (const [name, weight] of weighted) {
    cursor += weight;
    if (slot < cursor) {
      return name;
    }
  }
  return 'warm-cache-repeat-quotes';
}
