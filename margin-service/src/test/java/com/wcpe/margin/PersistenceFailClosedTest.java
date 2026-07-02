package com.wcpe.margin;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wcpe.margin.overlay.OverlayInputs;
import com.wcpe.margin.overlay.OverlayRuleRepository;
import com.wcpe.margin.srp.SrpCalculationService;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PersistenceFailClosedTest {
  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

  @TempDir
  Path tempDir;

  @Test
  void servicesWithMigrationBackedContractsHaveAvailableDefaultStores() {
    assertDoesNotThrow(() -> new CompanyMarginPolicyService(CLOCK).outboxEvents());
    assertDoesNotThrow(() -> new MarginReplayService(CLOCK).outboxEvents());
    assertDoesNotThrow(() -> new MarginGovernanceService(CLOCK).outboxEvents());
    assertDoesNotThrow(() -> new ProfitabilityFloorService(CLOCK).outboxEvents());
    assertDoesNotThrow(() -> new MarginVersioningService(CLOCK).outboxEvents());
    assertDoesNotThrow(() -> new BrokerCompensationService(CLOCK).outboxEvents());
    assertDoesNotThrow(() -> new LoCompensationService(CLOCK).outboxEvents());
  }

  @Test
  void explicitFailClosedStoreStillFailsWhenSelected() {
    assertThrows(ProcessLocalStatePolicy.PersistenceNotConfiguredException.class,
        () -> new CompanyMarginPolicyService(CLOCK, new SrpCalculationService(), OverlayRuleRepository.empty(),
            CompanyMarginPolicyService.Store.failClosed("CompanyMarginPolicyService")).outboxEvents());
  }

  @Test
  void productionDefaultStoreFailsClosedWithoutDurableConfiguration() {
    String oldProfile = System.getProperty("spring.profiles.active");
    String oldProcessLocal = System.getProperty(ProcessLocalStatePolicy.PROCESS_LOCAL_STORE_PROPERTY);
    String oldDurablePath = System.getProperty(ProcessLocalStatePolicy.DURABLE_STORE_PATH_PROPERTY);
    String oldDatasource = System.getProperty("spring.datasource.url");
    try {
      System.setProperty("spring.profiles.active", "prod");
      System.clearProperty(ProcessLocalStatePolicy.PROCESS_LOCAL_STORE_PROPERTY);
      System.clearProperty(ProcessLocalStatePolicy.DURABLE_STORE_PATH_PROPERTY);
      System.clearProperty("spring.datasource.url");

      ProcessLocalStatePolicy.PersistenceNotConfiguredException error = assertThrows(
          ProcessLocalStatePolicy.PersistenceNotConfiguredException.class,
          () -> new MarginGovernanceService(CLOCK));

      assertTrue(error.getMessage().contains(ProcessLocalStatePolicy.PERSISTENT_STORE_REQUIRED));
    } finally {
      restoreProperty("spring.profiles.active", oldProfile);
      restoreProperty(ProcessLocalStatePolicy.PROCESS_LOCAL_STORE_PROPERTY, oldProcessLocal);
      restoreProperty(ProcessLocalStatePolicy.DURABLE_STORE_PATH_PROPERTY, oldDurablePath);
      restoreProperty("spring.datasource.url", oldDatasource);
    }
  }

  @Test
  void productionDefaultStoreUsesConfiguredDurableFileAdapter() {
    String oldProfile = System.getProperty("spring.profiles.active");
    String oldProcessLocal = System.getProperty(ProcessLocalStatePolicy.PROCESS_LOCAL_STORE_PROPERTY);
    String oldDurablePath = System.getProperty(ProcessLocalStatePolicy.DURABLE_STORE_PATH_PROPERTY);
    Path durableStore = tempDir.resolve("margin-store.json");
    try {
      System.setProperty("spring.profiles.active", "prod");
      System.clearProperty(ProcessLocalStatePolicy.PROCESS_LOCAL_STORE_PROPERTY);
      System.setProperty(ProcessLocalStatePolicy.DURABLE_STORE_PATH_PROPERTY, durableStore.toString());

      MarginGovernanceService service = new MarginGovernanceService(CLOCK);
      MarginGovernanceService.ApprovalRoute approvalRoute = new MarginGovernanceService.ApprovalRoute(
          java.util.List.of(new MarginGovernanceService.ApprovalStepPolicy("COMPLIANCE",
              MarginGovernanceService.APPROVE_PERMISSION)));
      MarginGovernanceService.GovernanceReceipt receipt = service.createChangeRequest(
          new MarginGovernanceService.ChangeRequestCommand("tenant-durable", "req-" + UUID.randomUUID(),
              "actor-a", "idem-" + UUID.randomUUID(), "corr-durable-store", "COMPANY", "policy-a",
              "version-a", 1, "config-hash", "diff-hash", "HIGH", approvalRoute,
              "request-hash-" + UUID.randomUUID()));

      assertTrue(Files.exists(durableStore));
      assertTrue(Files.exists(durableStore.resolveSibling(durableStore.getFileName() + ".lock")));
      assertTrue(new MarginGovernanceService(CLOCK).readChange("tenant-durable", receipt.changeId()).isPresent());
    } finally {
      restoreProperty("spring.profiles.active", oldProfile);
      restoreProperty(ProcessLocalStatePolicy.PROCESS_LOCAL_STORE_PROPERTY, oldProcessLocal);
      restoreProperty(ProcessLocalStatePolicy.DURABLE_STORE_PATH_PROPERTY, oldDurablePath);
    }
  }

  @Test
  void processLocalStoreRequiresExplicitLocalProfileOrProperty() {
    String oldProfile = System.getProperty("spring.profiles.active");
    String oldProcessLocal = System.getProperty(ProcessLocalStatePolicy.PROCESS_LOCAL_STORE_PROPERTY);
    try {
      System.setProperty("spring.profiles.active", "prod");
      System.setProperty(ProcessLocalStatePolicy.PROCESS_LOCAL_STORE_PROPERTY, "true");

      assertDoesNotThrow(() -> new MarginGovernanceService(CLOCK).outboxEvents());
    } finally {
      restoreProperty("spring.profiles.active", oldProfile);
      restoreProperty(ProcessLocalStatePolicy.PROCESS_LOCAL_STORE_PROPERTY, oldProcessLocal);
    }
  }

  @Test
  void defaultGovernanceStoreIsSharedAcrossServiceInstances() {
    MarginGovernanceService service = new MarginGovernanceService(CLOCK);
    MarginGovernanceService.ApprovalRoute approvalRoute = new MarginGovernanceService.ApprovalRoute(
        java.util.List.of(new MarginGovernanceService.ApprovalStepPolicy("COMPLIANCE",
            MarginGovernanceService.APPROVE_PERMISSION)));
    MarginGovernanceService.GovernanceReceipt receipt = service.createChangeRequest(
        new MarginGovernanceService.ChangeRequestCommand("tenant-a", "req-" + UUID.randomUUID(), "actor-a",
            "idem-" + UUID.randomUUID(), "corr-shared-store", "COMPANY", "policy-a", "version-a", 1,
            "config-hash", "diff-hash", "HIGH", approvalRoute, "request-hash-" + UUID.randomUUID()));

    assertTrue(new MarginGovernanceService(CLOCK).readChange("tenant-a", receipt.changeId()).isPresent());
  }

  @Test
  void defaultOverlayRepositoryFailsClosedWhenNoDurableStoreExists() {
    OverlayInputs inputs = new OverlayInputs(UUID.randomUUID(), "investor", "retail", "conforming",
        "purchase", "owner", "single-family", "CA", "001", new BigDecimal("500000"), false, null, null,
        false, true, 360, Instant.parse("2026-01-01T00:00:00Z"));

    assertThrows(IllegalStateException.class, () -> OverlayRuleRepository.empty().findApplicable(inputs));
  }

  private static void restoreProperty(String key, String value) {
    if (value == null) {
      System.clearProperty(key);
    } else {
      System.setProperty(key, value);
    }
  }
}
