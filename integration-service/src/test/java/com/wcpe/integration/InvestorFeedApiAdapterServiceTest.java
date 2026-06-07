package com.wcpe.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wcpe.integration.InvestorFeedApiAdapterService.ConfigureAdapterCommand;
import com.wcpe.integration.InvestorFeedApiAdapterService.FixtureInvestorApiClient;
import com.wcpe.integration.InvestorFeedApiAdapterService.InvestorAdapterResponse;
import com.wcpe.integration.InvestorFeedApiAdapterService.InvestorAdapterResult;
import com.wcpe.integration.InvestorFeedApiAdapterService.InvestorCallbackCommand;
import com.wcpe.integration.InvestorFeedApiAdapterService.InvestorFeedImportRun;
import com.wcpe.integration.InvestorFeedApiAdapterService.InvestorFeedPayload;
import com.wcpe.integration.InvestorFeedApiAdapterService.InvestorFeedRecord;
import com.wcpe.integration.InvestorFeedApiAdapterService.RunStatus;
import com.wcpe.integration.InvestorFeedApiAdapterService.SyncInvestorFeedCommand;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InvestorFeedApiAdapterServiceTest {
  private static final String TENANT_ONE = "11111111-1111-1111-1111-111111111111";
  private static final String TENANT_TWO = "22222222-2222-2222-2222-222222222222";
  private static final String ADAPTER_ID = "adapter-alpha";
  private static final String INVESTOR_ID = "investor-alpha";
  private static final String SCHEMA_VERSION = "INVESTOR-FEED-V1";

  @Test
  void configuresAdapterWithoutPersistingRawUrlOrCredentials() {
    InvestorFeedApiAdapterService service = service(client());

    InvestorAdapterResult result = service.configureAdapter(configure("idem-config-1"));

    assertTrue(result.valid());
    InvestorAdapterResponse response = result.value().orElseThrow();
    assertEquals(ADAPTER_ID, response.id());
    assertEquals("ACTIVE", response.status());
    assertEquals(1, service.adaptersForTenant(TENANT_ONE).size());
    assertEquals(0, service.adaptersForTenant(TENANT_TWO).size());
    assertNotEquals("https://investor.example.test/api", service.adaptersForTenant(TENANT_ONE).get(0).baseUrlHash());
    assertEquals("credential-ref/investor-alpha", service.adaptersForTenant(TENANT_ONE).get(0).credentialRef());
    assertEquals(InvestorFeedApiAdapterService.AUDIT_ACTION, service.auditRecords().get(0).action());
  }

  @Test
  void rejectsRawCredentialsDisallowedEgressAndMissingSchemaStrategy() {
    InvestorFeedApiAdapterService service = service(client());

    ConfigureAdapterCommand rawSecret =
        new ConfigureAdapterCommand(
            TENANT_ONE,
            ADAPTER_ID,
            INVESTOR_ID,
            "https://investor.example.test/api",
            "Bearer token-value",
            "manual",
            List.of("PRICE_SHEET"),
            SCHEMA_VERSION,
            10,
            true,
            LocalDate.parse("2026-06-07"),
            "idem-config-1",
            "integration-admin",
            "corr-PII-16-S05");
    assertEquals("VALIDATION_FAILED", service.configureAdapter(rawSecret).error().orElseThrow().reason());

    ConfigureAdapterCommand disallowedHost =
        new ConfigureAdapterCommand(
            TENANT_ONE,
            ADAPTER_ID,
            INVESTOR_ID,
            "https://unapproved.example.test/api",
            "credential-ref/investor-alpha",
            "manual",
            List.of("PRICE_SHEET"),
            SCHEMA_VERSION,
            10,
            true,
            LocalDate.parse("2026-06-07"),
            "idem-config-2",
            "integration-admin",
            "corr-PII-16-S05");
    assertEquals("POLICY_NOT_SATISFIED", service.configureAdapter(disallowedHost).error().orElseThrow().reason());

    ConfigureAdapterCommand missingStrategy =
        new ConfigureAdapterCommand(
            TENANT_ONE,
            ADAPTER_ID,
            "investor-without-strategy",
            "https://investor.example.test/api",
            "credential-ref/investor-alpha",
            "manual",
            List.of("PRICE_SHEET"),
            SCHEMA_VERSION,
            10,
            true,
            LocalDate.parse("2026-06-07"),
            "idem-config-3",
            "integration-admin",
            "corr-PII-16-S05");
    assertEquals("POLICY_NOT_SATISFIED", service.configureAdapter(missingStrategy).error().orElseThrow().reason());
  }

  @Test
  void syncsFixtureInvestorFeedAndPublishesRunAndRecordEvents() {
    FixtureInvestorApiClient client = client();
    InvestorFeedApiAdapterService service = service(client);
    service.configureAdapter(configure("idem-config-1"));

    InvestorAdapterResult result = service.triggerSync(sync("idem-sync-1"));

    assertTrue(result.valid());
    InvestorAdapterResponse response = result.value().orElseThrow();
    assertEquals("PUBLISHED", response.status());
    assertEquals("2", response.resultSummary().get("recordCount"));
    InvestorFeedImportRun run = service.runsForTenant(TENANT_ONE).get(0);
    assertEquals(RunStatus.PUBLISHED, run.status());
    assertEquals(2, service.stagingRecordsForRun(TENANT_ONE, run.runId()).size());
    assertEquals(1, client.acknowledgements().size());
    assertEquals(InvestorFeedApiAdapterService.RUN_STARTED_EVENT_TYPE, service.outboxEvents().get(0).eventType());
    assertEquals(InvestorFeedApiAdapterService.RUN_NORMALIZED_EVENT_TYPE, service.outboxEvents().get(1).eventType());
    assertEquals(InvestorFeedApiAdapterService.RECORD_NORMALIZED_EVENT_TYPE, service.outboxEvents().get(2).eventType());
    assertEquals(TENANT_ONE + ":" + INVESTOR_ID + ":PRICE_SHEET", service.outboxEvents().get(2).eventKey());
    assertFalse(service.outboxEvents().get(2).payload().toString().contains("rawPriceSheet"));
  }

  @Test
  void replaysIdempotentSyncAndRejectsConflictingReuse() {
    InvestorFeedApiAdapterService service = service(client());
    service.configureAdapter(configure("idem-config-1"));

    InvestorAdapterResponse first = service.triggerSync(sync("idem-sync-1")).value().orElseThrow();
    InvestorAdapterResponse replay = service.triggerSync(sync("idem-sync-1")).value().orElseThrow();
    InvestorAdapterResult conflict = service.triggerSync(new SyncInvestorFeedCommand(TENANT_ONE, ADAPTER_ID, "ELIGIBILITY", "cursor-a", "idem-sync-1", "integration-admin", "corr-PII-16-S05"));

    assertEquals(first, replay);
    assertFalse(conflict.valid());
    assertEquals("IDEMPOTENCY_CONFLICT", conflict.error().orElseThrow().reason());
    assertEquals(4, service.outboxEvents().size());
  }

  @Test
  void failsRunForSchemaMismatchAndFetchIsTenantIsolated() {
    FixtureInvestorApiClient client = new FixtureInvestorApiClient();
    client.addFixture(
        "PRICE_SHEET",
        new InvestorFeedPayload("UNKNOWN", "PRICE_SHEET", List.of(Map.of("externalRecordId", "price-1", "recordType", "PRICE_SHEET")), "cursor-b", "fixture"));
    InvestorFeedApiAdapterService service = service(client);
    service.configureAdapter(configure("idem-config-1"));

    InvestorAdapterResponse response = service.triggerSync(sync("idem-sync-1")).value().orElseThrow();

    assertEquals("FAILED", response.status());
    assertEquals("SCHEMA_VERSION_MISMATCH", response.validationMessages().get(0));
    InvestorFeedImportRun run = service.runsForTenant(TENANT_ONE).get(0);
    assertTrue(service.fetchRun(TENANT_ONE, ADAPTER_ID, run.runId(), "corr-PII-16-S05").valid());
    assertFalse(service.fetchRun(TENANT_TWO, ADAPTER_ID, run.runId(), "corr-PII-16-S05").valid());
    assertEquals(InvestorFeedApiAdapterService.RUN_FAILED_EVENT_TYPE, service.outboxEvents().get(1).eventType());
  }

  @Test
  void callbackRequiresServiceAccountPermission() {
    InvestorFeedApiAdapterService service = service(client());
    service.configureAdapter(configure("idem-config-1"));

    InvestorAdapterResult denied =
        service.receiveCallback(new InvestorCallbackCommand(TENANT_ONE, ADAPTER_ID, "PRICE_SHEET", "cursor-a", "idem-callback-1", "investor-service", "wrong.permission", "corr-PII-16-S05"));
    InvestorAdapterResult accepted =
        service.receiveCallback(
            new InvestorCallbackCommand(
                TENANT_ONE,
                ADAPTER_ID,
                "PRICE_SHEET",
                "cursor-a",
                "idem-callback-2",
                "investor-service",
                InvestorFeedApiAdapterService.SERVICE_ACCOUNT_PERMISSION,
                "corr-PII-16-S05"));

    assertFalse(denied.valid());
    assertEquals("TENANT_ACCESS_DENIED", denied.error().orElseThrow().reason());
    assertTrue(accepted.valid());
  }

  @Test
  void migrationUsesTenantScopedKeysAndForeignKeys() throws Exception {
    String migration = Files.readString(Path.of("src/main/resources/db/migration/V5__investor_feed_api_adapter.sql"));

    assertTrue(migration.contains("primary key (tenant_id, adapter_id)"));
    assertTrue(migration.contains("primary key (tenant_id, run_id)"));
    assertTrue(migration.contains("primary key (tenant_id, run_id, record_id)"));
    assertTrue(migration.contains("foreign key (tenant_id, adapter_id) references integration_investor_api_adapter(tenant_id, adapter_id)"));
    assertTrue(migration.contains("foreign key (tenant_id, run_id) references integration_investor_feed_run(tenant_id, run_id)"));
  }

  private InvestorFeedApiAdapterService service(FixtureInvestorApiClient client) {
    InvestorFeedApiAdapterService service =
        new InvestorFeedApiAdapterService(
            Clock.fixed(Instant.parse("2026-06-07T03:05:00Z"), ZoneOffset.UTC),
            client,
            Set.of("investor.example.test"));
    service.registerNormalizer(INVESTOR_ID, SCHEMA_VERSION, this::normalize);
    return service;
  }

  private FixtureInvestorApiClient client() {
    FixtureInvestorApiClient client = new FixtureInvestorApiClient();
    client.addFixture(
        "PRICE_SHEET",
        new InvestorFeedPayload(
            SCHEMA_VERSION,
            "PRICE_SHEET",
            List.of(
                Map.of("externalRecordId", "price-1", "recordType", "PRICE_SHEET", "productRef", "product-a", "effectiveDate", "2026-06-07", "rawPriceSheet", "restricted"),
                Map.of("externalRecordId", "price-2", "recordType", "PRICE_SHEET", "productRef", "product-b", "effectiveDate", "2026-06-07", "rawPriceSheet", "restricted")),
            "cursor-b",
            "fixture-price-sheet-v1"));
    client.addFixture(
        "ELIGIBILITY",
        new InvestorFeedPayload(
            SCHEMA_VERSION,
            "ELIGIBILITY",
            List.of(Map.of("externalRecordId", "elig-1", "recordType", "ELIGIBILITY", "productRef", "product-a", "effectiveDate", "2026-06-07")),
            "cursor-c",
            "fixture-eligibility-v1"));
    return client;
  }

  private List<InvestorFeedRecord> normalize(InvestorFeedImportRun run, List<Map<String, String>> records) {
    return records.stream()
        .map(
            record ->
                new InvestorFeedRecord(
                    run.tenantId(),
                    run.runId(),
                    run.runId() + ":" + record.get("externalRecordId"),
                    record.get("externalRecordId"),
                    record.get("recordType"),
                    Map.of(
                        "externalRecordId", record.get("externalRecordId"),
                        "recordType", record.get("recordType"),
                        "productRef", record.get("productRef"),
                        "effectiveDate", record.get("effectiveDate")),
                    "VALID",
                    List.of()))
        .toList();
  }

  private ConfigureAdapterCommand configure(String idempotencyKey) {
    return new ConfigureAdapterCommand(
        TENANT_ONE,
        ADAPTER_ID,
        INVESTOR_ID,
        "https://investor.example.test/api",
        "credential-ref/investor-alpha",
        "manual",
        List.of("PRICE_SHEET", "ELIGIBILITY"),
        SCHEMA_VERSION,
        10,
        true,
        LocalDate.parse("2026-06-07"),
        idempotencyKey,
        "integration-admin",
        "corr-PII-16-S05");
  }

  private SyncInvestorFeedCommand sync(String idempotencyKey) {
    return new SyncInvestorFeedCommand(TENANT_ONE, ADAPTER_ID, "PRICE_SHEET", "cursor-a", idempotencyKey, "integration-admin", "corr-PII-16-S05");
  }
}
