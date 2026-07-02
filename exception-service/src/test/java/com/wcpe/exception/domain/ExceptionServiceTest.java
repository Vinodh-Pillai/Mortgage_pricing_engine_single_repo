package com.wcpe.exception.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExceptionServiceTest {

  @TempDir
  Path tempDir;

  @Test
  void createPersistsAndReloadsLifecycleStateFromDurableStore() {
    Path store = tempDir.resolve("exception-repository.json");
    ExceptionService service = new ExceptionService(new ExceptionRepository(store));

    ExceptionModels.ExceptionRequestStatus created = service.create(
      new ExceptionModels.ExceptionRequestCreate("QUOTE-123", ExceptionModels.ExceptionType.CONCESSION)
    );

    assertEquals(ExceptionModels.ExceptionState.DRAFT, created.state());
    ExceptionService reloaded = new ExceptionService(new ExceptionRepository(store));
    assertEquals(created.exceptionRequestId(), reloaded.status(created.exceptionRequestId()).exceptionRequestId());
  }

  @Test
  void repositoryWritesUseSidecarFileLockAndAtomicJsonStore() {
    Path store = tempDir.resolve("locked-exception-repository.json");
    ExceptionRepository repository = new ExceptionRepository(store);

    repository.create(new ExceptionModels.ExceptionRequestCreate("QUOTE-LOCKED", ExceptionModels.ExceptionType.CONCESSION));

    assertTrue(Files.exists(store));
    assertTrue(Files.exists(store.resolveSibling(store.getFileName() + ".lock")));
    assertFalse(Files.exists(store.resolveSibling(store.getFileName() + ".tmp")));
  }

  @Test
  void defaultRepositoryFailsClosedWhenProductionProfileDoesNotConfigureStorePath() {
    String oldProfile = System.getProperty("spring.profiles.active");
    String oldStorePath = System.getProperty(ExceptionRepository.REPOSITORY_PATH_PROPERTY);
    String oldLocalFlag = System.getProperty(ExceptionRepository.LOCAL_JSON_STORE_PROPERTY);
    try {
      System.setProperty("spring.profiles.active", "prod");
      System.clearProperty(ExceptionRepository.REPOSITORY_PATH_PROPERTY);
      System.clearProperty(ExceptionRepository.LOCAL_JSON_STORE_PROPERTY);

      ExceptionServiceException error = assertThrows(ExceptionServiceException.class, ExceptionRepository::new);

      assertEquals("PERSISTENCE_STORE_REQUIRED", error.code());
    } finally {
      restoreProperty("spring.profiles.active", oldProfile);
      restoreProperty(ExceptionRepository.REPOSITORY_PATH_PROPERTY, oldStorePath);
      restoreProperty(ExceptionRepository.LOCAL_JSON_STORE_PROPERTY, oldLocalFlag);
    }
  }

  @Test
  void defaultRepositoryUsesExplicitConfiguredDurablePathInProductionProfile() {
    String oldProfile = System.getProperty("spring.profiles.active");
    String oldStorePath = System.getProperty(ExceptionRepository.REPOSITORY_PATH_PROPERTY);
    try {
      System.setProperty("spring.profiles.active", "prod");
      System.setProperty(ExceptionRepository.REPOSITORY_PATH_PROPERTY, tempDir.resolve("configured-store.json").toString());

      ExceptionService service = new ExceptionService(new ExceptionRepository());
      ExceptionModels.ExceptionRequestStatus created = service.create(
        new ExceptionModels.ExceptionRequestCreate("QUOTE-CONFIGURED", ExceptionModels.ExceptionType.CONCESSION)
      );

      assertEquals(created.exceptionRequestId(), new ExceptionService(new ExceptionRepository()).status(created.exceptionRequestId()).exceptionRequestId());
    } finally {
      restoreProperty("spring.profiles.active", oldProfile);
      restoreProperty(ExceptionRepository.REPOSITORY_PATH_PROPERTY, oldStorePath);
    }
  }

  @Test
  void transitionUsesRepositoryStateInsteadOfFailClosedUnavailableBoundary() {
    ExceptionService service = new ExceptionService(new ExceptionRepository(tempDir.resolve("transition-store.json")));
    ExceptionModels.ExceptionRequestStatus created = service.create(
      new ExceptionModels.ExceptionRequestCreate("QUOTE-456", ExceptionModels.ExceptionType.EXCEPTION)
    );

    ExceptionModels.ExceptionTransitionResponse transitioned = service.transition(
      created.exceptionRequestId(),
      new ExceptionModels.ExceptionTransitionRequest(ExceptionModels.ExceptionState.SUBMITTED)
    );

    assertEquals(ExceptionModels.ExceptionState.DRAFT, transitioned.previousState());
    assertEquals(ExceptionModels.ExceptionState.SUBMITTED, transitioned.newState());
  }

  @Test
  void repositoryListReadsReturnDurableRecords() {
    ExceptionRepository repository = new ExceptionRepository(tempDir.resolve("list-store.json"));
    repository.create(new ExceptionModels.ExceptionRequestCreate("QUOTE-789", ExceptionModels.ExceptionType.CONCESSION));

    assertEquals(1, repository.exceptionRequests().size());
    assertTrue(repository.concessionRequests().isEmpty());
  }

  @Test
  void historyReplayAndExportAreSavedForWorkbenchSurfaces() {
    UUID tenantId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    ExceptionRepository repository = new ExceptionRepository(tempDir.resolve("history-store.json"));
    ExceptionService service = new ExceptionService(repository);
    service.create(new ExceptionModels.ExceptionRequestCreate("QUOTE-HIST-1", ExceptionModels.ExceptionType.CONCESSION));
    ExceptionModels.ExceptionHistorySearchRequest request = new ExceptionModels.ExceptionHistorySearchRequest(
      tenantId,
      ExceptionModels.ExceptionHistorySubjectType.QUOTE,
      "QUOTE-HIST-1",
      "history-actor",
      Set.of("exception_history.view", "exception_history.replay", "exception_history.export"),
      false,
      "corr-history"
    );

    ExceptionModels.ExceptionHistoryTimeline timeline = service.reconstructExceptionHistory(request);
    ExceptionModels.ExceptionHistoryReplayResult replay = service.replayExceptionHistory(request, timeline.projectionHash(), java.util.List.of());
    ExceptionModels.ExceptionHistoryExportPacket export = service.exportExceptionHistory(request, false, null);

    assertEquals(1, timeline.events().size());
    assertEquals(ExceptionModels.ExceptionHistoryReplayStatus.MATCH, replay.status());
    assertTrue(repository.findHistoryReplay(tenantId, replay.replayId()).isPresent());
    assertTrue(repository.findHistoryExport(tenantId, export.manifest().exportId()).isPresent());
    assertFalse(service.exceptionConcessionWorkbench(tenantId, "CASE-1", "QUOTE-HIST-1").sections().isEmpty());
  }

  private static void restoreProperty(String key, String value) {
    if (value == null) {
      System.clearProperty(key);
    } else {
      System.setProperty(key, value);
    }
  }
}
