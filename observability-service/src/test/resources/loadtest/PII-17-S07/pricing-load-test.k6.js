import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';

const fixture = new SharedArray('pricing-load-test-fixture', () => [JSON.parse(open('./pricing-load-test-fixture.json'))])[0];
const baseUrl = __ENV.PRICING_BASE_URL;
const tenantOverride = __ENV.PRICING_TENANT_ID;

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
    'http_req_failed': ['rate<0.005']
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
  const payload = {
    requestId: `load-${scenarioType}-${__VU}-${__ITER}`,
    sourceSystem: 'observability-load-test',
    scenarioType,
    scenarioRefs: fixture.scenarioLabels,
    sourceRefs: fixture.sourceRefs,
    privacy: fixture.privacy
  };

  const response = http.post(
      `${baseUrl}/api/v1/tenants/${tenantId}/pricing/quotes`,
      JSON.stringify(payload),
      {
        headers: {
          'Content-Type': 'application/json',
          'X-Correlation-Id': correlationId,
          'Idempotency-Key': idempotencyKey
        },
        tags: { scenarioType }
      });

  check(response, {
    'status is 2xx, validation 4xx, rate limit 429, or dependency 503': r =>
        (r.status >= 200 && r.status < 300) || r.status === 400 || r.status === 422 || r.status === 429 || r.status === 503
  });
  sleep(Number(__ENV.PRICING_LOAD_SLEEP_SECONDS || 1));
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
