package com.wcpe.pricingbff.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class LockManagementServiceClientTest {
  @Test
  void readsLiveLockSummaryAndDetailFromConfiguredLockService() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    UUID tenantScope = UUID.nameUUIDFromBytes("ui-tenant:tenant-live".getBytes(StandardCharsets.UTF_8));
    server.expect(requestTo("https://lock-service.test/api/v1/tenants/" + tenantScope + "/locks"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("""
            [{"lockId":"lock-live-1","quoteId":"quote-live-1","status":"REQUESTED","version":2,"createdAt":"2026-07-01T08:00:00Z","updatedAt":"2026-07-01T08:05:00Z","expiresAt":"2026-07-05T08:00:00Z"}]
            """, MediaType.APPLICATION_JSON));
    server.expect(requestTo("https://lock-service.test/api/v1/tenants/" + tenantScope + "/locks/lock-live-1"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("""
            {"lockId":"lock-live-1","status":"REQUESTED","version":2,"createdAt":"2026-07-01T08:00:00Z","expiresAt":"2026-07-05T08:00:00Z","expirationBusinessDays":3,"calendarConfigHash":"calendar-hash-1"}
            """, MediaType.APPLICATION_JSON));

    LockManagementServiceClient client = new LockManagementServiceClient(builder, "https://lock-service.test");
    PricingBffUiFallbackAdapter.LockManagementView view = client.lockManagement("tenant-live", "trace-live");
    var detailAction = client.requestLockManagementAction("tenant-live", "lock-live-1", "detail", "trace-live-detail");

    assertThat(view.dependencyStatus()).isEqualTo("LOCK_SERVICE_LIVE_READ_READY");
    assertThat(view.locks()).hasSize(1);
    assertThat(view.locks().get(0).availableActions()).containsExactly("read", "detail");
    assertThat(view.locks().get(0).actionBlockers()).containsEntry("extend", "LOCK_EXTENSION_REQUIRED_FIELDS_NOT_SUPPLIED");
    assertThat(view.pendingCount()).isEqualTo(1);
    assertThat(detailAction.getBody().status()).isEqualTo("ACCEPTED");
    server.verify();
  }

  @Test
  void disablesUnsupportedMutationActionsWithPreciseReasons() {
    LockManagementServiceClient client = LockManagementServiceClient.notConfigured();

    var extension = client.requestLockManagementAction("tenant-live", "lock-live-1", "extend", "trace-action");
    var deliver = client.requestLockManagementAction("tenant-live", "lock-live-1", "deliver", "trace-action");

    assertThat(extension.getStatusCode().value()).isEqualTo(422);
    assertThat(extension.getBody().blockers()).containsExactly("LOCK_EXTENSION_REQUIRED_FIELDS_NOT_SUPPLIED");
    assertThat(deliver.getBody().blockers()).containsExactly("LOCK_INVESTOR_DELIVERY_ROUTE_NOT_EXPOSED_BY_LOCK_SERVICE");
  }
}
