package com.wcpe.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wcpe.integration.SftpFeedAdapterService.ConfigureSftpAdapterCommand;
import com.wcpe.integration.SftpFeedAdapterService.FixtureSftpClient;
import com.wcpe.integration.SftpFeedAdapterService.PollSftpAdapterCommand;
import com.wcpe.integration.SftpFeedAdapterService.RemoteSftpFile;
import com.wcpe.integration.SftpFeedAdapterService.RemoteSftpFileContent;
import com.wcpe.integration.SftpFeedAdapterService.RunStatus;
import com.wcpe.integration.SftpFeedAdapterService.SftpAdapterResponse;
import com.wcpe.integration.SftpFeedAdapterService.SftpAdapterResult;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SftpFeedAdapterServiceTest {
  private static final String TENANT_ONE = "11111111-1111-1111-1111-111111111111";
  private static final String TENANT_TWO = "22222222-2222-2222-2222-222222222222";
  private static final String ADAPTER_ID = "sftp-adapter-alpha";
  private static final String PARTNER_ID = "partner-alpha";
  private static final String HOST = "sftp.partner.example.test";
  private static final int PORT = 22;
  private static final String FINGERPRINT = "SHA256:tenant-known-host-alpha";
  private static final String FEED_TYPE = "PRICE_SHEET";
  private static final String SCHEMA_VERSION = "PRICE-SHEET-V1";

  @Test
  void configuresAdapterWithoutPersistingRawSecrets() {
    SftpFeedAdapterService service = service(clientWithValidFile());

    SftpAdapterResult result = service.configureAdapter(configure("idem-config-1"));

    assertTrue(result.valid());
    SftpAdapterResponse response = result.value().orElseThrow();
    assertEquals(ADAPTER_ID, response.id());
    assertEquals("ACTIVE", response.status());
    assertEquals(1, service.adaptersForTenant(TENANT_ONE).size());
    assertEquals(0, service.adaptersForTenant(TENANT_TWO).size());
    assertEquals("credential-ref/sftp-alpha", service.adaptersForTenant(TENANT_ONE).get(0).credentialRef());
    assertEquals(SftpFeedAdapterService.AUDIT_ACTION, service.auditRecords().get(0).action());
  }

  @Test
  void rejectsRawCredentialsKnownHostMismatchUnsafePatternAndMissingValidator() {
    SftpFeedAdapterService service = service(clientWithValidFile());

    ConfigureSftpAdapterCommand rawSecret =
        new ConfigureSftpAdapterCommand(TENANT_ONE, ADAPTER_ID, PARTNER_ID, HOST, PORT, "/inbound", "*.csv", "Bearer token-value", FINGERPRINT, FEED_TYPE, SCHEMA_VERSION, "/archive", "manual", true, "idem-config-1", "integration-admin", "corr-PII-16-S06");
    assertEquals("VALIDATION_FAILED", service.configureAdapter(rawSecret).error().orElseThrow().reason());

    ConfigureSftpAdapterCommand mismatch =
        new ConfigureSftpAdapterCommand(TENANT_ONE, ADAPTER_ID, PARTNER_ID, HOST, PORT, "/inbound", "*.csv", "credential-ref/sftp-alpha", "SHA256:wrong", FEED_TYPE, SCHEMA_VERSION, "/archive", "manual", true, "idem-config-2", "integration-admin", "corr-PII-16-S06");
    assertEquals("POLICY_NOT_SATISFIED", service.configureAdapter(mismatch).error().orElseThrow().reason());

    ConfigureSftpAdapterCommand unsafe =
        new ConfigureSftpAdapterCommand(TENANT_ONE, ADAPTER_ID, PARTNER_ID, HOST, PORT, "/inbound", "../*.csv", "credential-ref/sftp-alpha", FINGERPRINT, FEED_TYPE, SCHEMA_VERSION, "/archive", "manual", true, "idem-config-3", "integration-admin", "corr-PII-16-S06");
    assertEquals("VALIDATION_FAILED", service.configureAdapter(unsafe).error().orElseThrow().reason());

    ConfigureSftpAdapterCommand missingValidator =
        new ConfigureSftpAdapterCommand(TENANT_ONE, ADAPTER_ID, PARTNER_ID, HOST, PORT, "/inbound", "*.csv", "credential-ref/sftp-alpha", FINGERPRINT, FEED_TYPE, "UNKNOWN", "/archive", "manual", true, "idem-config-4", "integration-admin", "corr-PII-16-S06");
    assertEquals("POLICY_NOT_SATISFIED", service.configureAdapter(missingValidator).error().orElseThrow().reason());
  }

  @Test
  void pollsFixtureSftpFeedNormalizesArchivesAndPublishesAuditableEvents() {
    FixtureSftpClient client = clientWithValidFile();
    SftpFeedAdapterService service = service(client);
    service.configureAdapter(configure("idem-config-1"));

    SftpAdapterResult result = service.pollAdapter(poll("idem-poll-1"));

    assertTrue(result.valid());
    SftpAdapterResponse response = result.value().orElseThrow();
    assertEquals("ARCHIVED", response.status());
    assertEquals("1", response.resultSummary().get("discoveredCount"));
    assertEquals("2", response.resultSummary().get("normalizedCount"));
    assertEquals("1", response.resultSummary().get("archivedCount"));
    assertEquals(RunStatus.ARCHIVED, service.runsForTenant(TENANT_ONE).get(0).status());
    assertEquals(2, service.stagingRecordsForRun(TENANT_ONE, service.runsForTenant(TENANT_ONE).get(0).runId()).size());
    assertFalse(service.stagingRecordsForRun(TENANT_ONE, service.runsForTenant(TENANT_ONE).get(0).runId()).get(0).normalizedJson().containsKey("rawPriceSheet"));
    assertEquals(1, client.archives().size());
    assertEquals(SftpFeedAdapterService.FILE_DISCOVERED_EVENT_TYPE, service.outboxEvents().get(0).eventType());
    assertEquals(SftpFeedAdapterService.RECORD_NORMALIZED_EVENT_TYPE, service.outboxEvents().get(1).eventType());
    assertEquals(SftpFeedAdapterService.FILE_NORMALIZED_EVENT_TYPE, service.outboxEvents().get(3).eventType());
    assertEquals(SftpFeedAdapterService.FILE_ARCHIVED_EVENT_TYPE, service.outboxEvents().get(4).eventType());
    assertFalse(service.outboxEvents().get(1).payload().toString().contains("rawPriceSheet"));
    assertFalse(service.outboxEvents().get(4).payload().toString().contains("/archive"));
  }

  @Test
  void failsChecksumMismatchAndFetchIsTenantIsolated() {
    FixtureSftpClient client = new FixtureSftpClient();
    client.addKnownHost(HOST, PORT, FINGERPRINT);
    byte[] csv = "externalRecordId,recordType,productRef,effectiveDate\nprice-1,PRICE_SHEET,product-a,2026-06-07\n".getBytes(StandardCharsets.UTF_8);
    client.addFile(new RemoteSftpFile("/inbound/price-sheet.csv", "price-sheet.csv", csv.length, false, false, Instant.parse("2026-06-07T03:05:00Z")), new RemoteSftpFileContent(csv, "checksum-that-does-not-match"));
    SftpFeedAdapterService service = service(client);
    service.configureAdapter(configure("idem-config-1"));

    SftpAdapterResponse response = service.pollAdapter(poll("idem-poll-1")).value().orElseThrow();

    assertEquals("FAILED", response.status());
    assertTrue(response.validationMessages().get(0).contains("CHECKSUM_MISMATCH"));
    String runId = service.runsForTenant(TENANT_ONE).get(0).runId();
    assertTrue(service.fetchRun(TENANT_ONE, ADAPTER_ID, runId, "corr-PII-16-S06").valid());
    assertFalse(service.fetchRun(TENANT_TWO, ADAPTER_ID, runId, "corr-PII-16-S06").valid());
    assertEquals(SftpFeedAdapterService.FILE_FAILED_EVENT_TYPE, service.outboxEvents().get(1).eventType());
  }

  @Test
  void replaysIdempotentPollAndRejectsConflictingReuse() {
    SftpFeedAdapterService service = service(clientWithValidFile());
    service.configureAdapter(configure("idem-config-1"));

    SftpAdapterResponse first = service.pollAdapter(poll("idem-poll-1")).value().orElseThrow();
    SftpAdapterResponse replay = service.pollAdapter(poll("idem-poll-1")).value().orElseThrow();
    SftpAdapterResult conflict = service.pollAdapter(new PollSftpAdapterCommand(TENANT_ONE, "other-adapter", "idem-poll-1", "integration-admin", "corr-PII-16-S06"));

    assertEquals(first, replay);
    assertFalse(conflict.valid());
    assertEquals("IDEMPOTENCY_CONFLICT", conflict.error().orElseThrow().reason());
  }

  @Test
  void migrationUsesTenantScopedTablesKeysAndDeduplicationConstraint() throws Exception {
    String migration = Files.readString(Path.of("src/main/resources/db/migration/V6__sftp_feed_adapter.sql"));

    assertTrue(migration.contains("create table if not exists integration_sftp_adapter"));
    assertTrue(migration.contains("create table if not exists integration_sftp_file"));
    assertTrue(migration.contains("create table if not exists integration_sftp_feed_record_staging"));
    assertTrue(migration.contains("constraint uq_integration_sftp_file_checksum unique (tenant_id, adapter_id, checksum)"));
    assertTrue(migration.contains("foreign key (tenant_id, adapter_id) references integration_sftp_adapter(tenant_id, adapter_id)"));
  }

  private SftpFeedAdapterService service(FixtureSftpClient client) {
    SftpFeedAdapterService service =
        new SftpFeedAdapterService(
            Clock.fixed(Instant.parse("2026-06-07T03:05:00Z"), ZoneOffset.UTC),
            client,
            (tenantId, credentialRef) -> TENANT_ONE.equals(tenantId) && "credential-ref/sftp-alpha".equals(credentialRef));
    service.registerSchemaValidator(FEED_TYPE, SCHEMA_VERSION, (file, rows) -> rows.stream().allMatch(row -> row.containsKey("externalRecordId") && row.containsKey("recordType")) ? List.of() : List.of("REQUIRED_COLUMNS_MISSING"));
    return service;
  }

  private FixtureSftpClient clientWithValidFile() {
    FixtureSftpClient client = new FixtureSftpClient();
    client.addKnownHost(HOST, PORT, FINGERPRINT);
    byte[] csv =
        ("externalRecordId,recordType,productRef,effectiveDate,rawPriceSheet\n"
                + "price-1,PRICE_SHEET,product-a,2026-06-07,restricted\n"
                + "price-2,PRICE_SHEET,product-b,2026-06-07,restricted\n")
            .getBytes(StandardCharsets.UTF_8);
    client.addFile(new RemoteSftpFile("/inbound/price-sheet.csv", "price-sheet.csv", csv.length, false, false, Instant.parse("2026-06-07T03:05:00Z")), new RemoteSftpFileContent(csv, sha256(csv)));
    client.addFile(new RemoteSftpFile("/inbound/.hidden.csv", ".hidden.csv", 1, false, true, Instant.parse("2026-06-07T03:05:00Z")), new RemoteSftpFileContent("x".getBytes(StandardCharsets.UTF_8), ""));
    return client;
  }

  private ConfigureSftpAdapterCommand configure(String idempotencyKey) {
    return new ConfigureSftpAdapterCommand(TENANT_ONE, ADAPTER_ID, PARTNER_ID, HOST, PORT, "/inbound", "*.csv", "credential-ref/sftp-alpha", FINGERPRINT, FEED_TYPE, SCHEMA_VERSION, "/archive", "manual", true, idempotencyKey, "integration-admin", "corr-PII-16-S06");
  }

  private PollSftpAdapterCommand poll(String idempotencyKey) {
    return new PollSftpAdapterCommand(TENANT_ONE, ADAPTER_ID, idempotencyKey, "integration-admin", "corr-PII-16-S06");
  }

  private String sha256(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }
}
