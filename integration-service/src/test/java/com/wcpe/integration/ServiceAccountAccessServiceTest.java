package com.wcpe.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wcpe.integration.ServiceAccountAccessService.CredentialResponse;
import com.wcpe.integration.ServiceAccountAccessService.CredentialStatus;
import com.wcpe.integration.ServiceAccountAccessService.CredentialType;
import com.wcpe.integration.ServiceAccountAccessService.IntegrationResult;
import com.wcpe.integration.ServiceAccountAccessService.LocalDevSecretProvider;
import com.wcpe.integration.ServiceAccountAccessService.RegisterCredentialCommand;
import com.wcpe.integration.ServiceAccountAccessService.ResolveCredentialCommand;
import com.wcpe.integration.ServiceAccountAccessService.RevokeCredentialCommand;
import com.wcpe.integration.ServiceAccountAccessService.RotateCredentialCommand;
import com.wcpe.integration.ServiceAccountAccessService.ServiceAccountResponse;
import com.wcpe.integration.ServiceAccountAccessService.ServiceAccountStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ServiceAccountAccessServiceTest {
  private static final String TENANT_ONE = "11111111-1111-1111-1111-111111111111";
  private static final String TENANT_TWO = "22222222-2222-2222-2222-222222222222";

  @Test
  void createsServiceAccountCredentialAndDisclosesGeneratedSecretOnceWithoutPersistingIt() {
    ServiceAccountAccessService service = service();
    ServiceAccountResponse account = createAccount(service, "idem-account-1");

    IntegrationResult<CredentialResponse> created =
        service.registerCredential(
            new RegisterCredentialCommand(
                TENANT_ONE,
                account.id(),
                "idem-credential-1",
                "integration-admin",
                "los-webhook-hmac",
                CredentialType.WEBHOOK_HMAC,
                true,
                "",
                "2027-01-01T00:00:00Z",
                Map.of("secret", "should-not-persist", "adapter", "webhook"),
                "corr-PII-16-S07"));

    assertTrue(created.valid());
    CredentialResponse response = created.value().orElseThrow();
    assertTrue(response.oneTimeSecret().orElseThrow().startsWith("local-dev-"));
    assertEquals(CredentialStatus.ACTIVE, response.status());
    assertTrue(response.secretRef().startsWith("local-dev://"));

    CredentialResponse metadata = service.fetchCredentialMetadata(TENANT_ONE, response.id(), "corr-PII-16-S07").value().orElseThrow();
    assertTrue(metadata.oneTimeSecret().isEmpty());
    assertFalse(metadata.toString().contains(response.oneTimeSecret().orElseThrow()));
    assertFalse(service.outboxEvents().toString().contains(response.oneTimeSecret().orElseThrow()));
    assertFalse(service.auditRecords().toString().contains(response.oneTimeSecret().orElseThrow()));
    assertTrue(metadata.resultSummary().get("metadata").contains("<redacted>"));
    assertEquals(ServiceAccountAccessService.CREDENTIAL_CREATED_EVENT_TYPE, service.outboxEvents().get(1).eventType());
    assertEquals(1L, service.metrics().get("integration_credentials_total"));
  }

  @Test
  void enforcesScopePolicyIdempotencyAndTenantIsolation() {
    ServiceAccountAccessService noPolicy = new ServiceAccountAccessService(fixedClock(), new LocalDevSecretProvider());
    assertEquals("POLICY_NOT_SATISFIED", createAccountResult(noPolicy, "idem-account-1").error().orElseThrow().reason());

    ServiceAccountAccessService service = service();
    ServiceAccountResponse first = createAccount(service, "idem-account-1");
    ServiceAccountResponse replay = createAccount(service, "idem-account-1");

    assertEquals(first, replay);
    assertEquals(1, service.serviceAccountsForTenant(TENANT_ONE).size());
    assertEquals(0, service.serviceAccountsForTenant(TENANT_TWO).size());

    IntegrationResult<ServiceAccountResponse> changed =
        service.createServiceAccount(
            new ServiceAccountAccessService.CreateServiceAccountCommand(
                TENANT_ONE,
                "idem-account-1",
                "integration-admin",
                "LOS API changed",
                "SYSTEM",
                List.of("integrations.credentials.write"),
                List.of("LOS"),
                ServiceAccountStatus.ACTIVE,
                "2027-01-01T00:00:00Z",
                "corr-PII-16-S07"));
    assertEquals("IDEMPOTENCY_CONFLICT", changed.error().orElseThrow().reason());
  }

  @Test
  void rotatesWithSeparationOfDutiesAndNeverReturnsRawSecretFromMetadata() {
    ServiceAccountAccessService service = service();
    ServiceAccountResponse account = createAccount(service, "idem-account-1");
    CredentialResponse created = createGeneratedCredential(service, account.id());

    IntegrationResult<CredentialResponse> sameActor =
        service.rotateCredential(new RotateCredentialCommand(TENANT_ONE, created.id(), "idem-rotate-1", "integration-admin", 1, true, "", "integration-admin", "corr-PII-16-S07"));
    assertEquals("POLICY_NOT_SATISFIED", sameActor.error().orElseThrow().reason());

    CredentialResponse rotated =
        service
            .rotateCredential(new RotateCredentialCommand(TENANT_ONE, created.id(), "idem-rotate-2", "integration-admin", 1, true, "", "security-approver", "corr-PII-16-S07"))
            .value()
            .orElseThrow();

    assertEquals(2, rotated.version());
    assertNotEquals(created.secretRef(), rotated.secretRef());
    assertTrue(rotated.oneTimeSecret().isPresent());
    assertTrue(service.fetchCredentialMetadata(TENANT_ONE, rotated.id(), "corr-PII-16-S07").value().orElseThrow().oneTimeSecret().isEmpty());
    assertEquals(ServiceAccountAccessService.CREDENTIAL_ROTATED_EVENT_TYPE, service.outboxEvents().get(2).eventType());
    assertEquals(1L, service.metrics().get("integration_credential_rotations_total"));
  }

  @Test
  void adapterLookupUsesServiceInterfaceAndRevocationBlocksUsage() {
    ServiceAccountAccessService service = service();
    ServiceAccountResponse account = createAccount(service, "idem-account-1");
    CredentialResponse created = createGeneratedCredential(service, account.id());

    assertTrue(service.resolveForAdapter(new ResolveCredentialCommand(TENANT_ONE, created.id(), "webhook-signing", "webhook-delivery", "corr-PII-16-S07")).valid());

    CredentialResponse revoked =
        service
            .revokeCredential(new RevokeCredentialCommand(TENANT_ONE, created.id(), "idem-revoke-1", "integration-admin", 1, "security-approver", "rotation complete", "corr-PII-16-S07"))
            .value()
            .orElseThrow();
    assertEquals(CredentialStatus.REVOKED, revoked.status());
    assertEquals("POLICY_NOT_SATISFIED", service.resolveForAdapter(new ResolveCredentialCommand(TENANT_ONE, created.id(), "webhook-signing", "webhook-delivery", "corr-PII-16-S07")).error().orElseThrow().reason());
    assertEquals(2, service.usageAudits().size());
    assertEquals(ServiceAccountAccessService.CREDENTIAL_REVOKED_EVENT_TYPE, service.outboxEvents().get(2).eventType());
    assertEquals(1L, service.metrics().get("integration_credential_usage_failures_total"));
  }

  private CredentialResponse createGeneratedCredential(ServiceAccountAccessService service, String accountId) {
    return service
        .registerCredential(
            new RegisterCredentialCommand(
                TENANT_ONE,
                accountId,
                "idem-credential-1",
                "integration-admin",
                "los-webhook-hmac",
                CredentialType.WEBHOOK_HMAC,
                true,
                "",
                "2027-01-01T00:00:00Z",
                Map.of("adapter", "webhook"),
                "corr-PII-16-S07"))
        .value()
        .orElseThrow();
  }

  private ServiceAccountResponse createAccount(ServiceAccountAccessService service, String idempotencyKey) {
    return createAccountResult(service, idempotencyKey).value().orElseThrow();
  }

  private IntegrationResult<ServiceAccountResponse> createAccountResult(ServiceAccountAccessService service, String idempotencyKey) {
    return service.createServiceAccount(
        new ServiceAccountAccessService.CreateServiceAccountCommand(
            TENANT_ONE,
            idempotencyKey,
            "integration-admin",
            "LOS API",
            "SYSTEM",
            List.of("integrations.credentials.write", "integrations.webhook.write"),
            List.of("LOS", "WEBHOOK"),
            ServiceAccountStatus.ACTIVE,
            "2027-01-01T00:00:00Z",
            "corr-PII-16-S07"));
  }

  private ServiceAccountAccessService service() {
    ServiceAccountAccessService service = new ServiceAccountAccessService(fixedClock(), new LocalDevSecretProvider());
    service.configureScopePolicy(Set.of("integrations.credentials.write", "integrations.webhook.write", "integrations.sftp-adapter.write"), Set.of("LOS", "WEBHOOK", "SFTP"));
    return service;
  }

  private Clock fixedClock() {
    return Clock.fixed(Instant.parse("2026-06-07T00:00:00Z"), ZoneOffset.UTC);
  }
}
