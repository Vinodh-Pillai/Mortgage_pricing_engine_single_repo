package com.wcpe.lock;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LockLifecycleControllerTest {
  @Test
  void lifecycleRoutesDefaultToFailClosedUntilDurablePersistenceIsWired() {
    LockPersistenceGate gate = new LockPersistenceGate();

    LockServiceException error = assertThrows(
      LockServiceException.class,
      () -> gate.requireLifecycleRoutePersistence("POST /api/v1/tenants/{tenantId}/locks/requests")
    );

    assertEquals("PERSISTENCE_NOT_DURABLE", error.code());
    assertTrue(error.getMessage().contains("fail-closed"));
  }

  @Test
  void inMemoryDevModeCannotEnableProductionLifecycleRoutes() {
    LockPersistenceGate gate = new LockPersistenceGate();
    gate.setMode("in-memory-dev");

    assertFalse(gate.lifecycleRoutesEnabled());
    LockServiceException error = assertThrows(
      LockServiceException.class,
      () -> gate.requireLifecycleRoutePersistence("POST /api/v1/tenants/{tenantId}/locks/requests")
    );

    assertEquals("PERSISTENCE_NOT_DURABLE", error.code());
    assertTrue(error.getMessage().contains("process-local storage is disabled"));
  }

  @Test
  void jdbcModeStillFailsClosedUntilJdbcRepositoryIsAvailable() {
    LockPersistenceGate gate = new LockPersistenceGate();
    gate.setMode("jdbc");

    assertFalse(gate.lifecycleRoutesEnabled());
    LockServiceException error = assertThrows(
      LockServiceException.class,
      () -> gate.requireLifecycleRoutePersistence("POST /api/v1/tenants/{tenantId}/locks/requests")
    );

    assertEquals("PERSISTENCE_NOT_DURABLE", error.code());
    assertTrue(error.getMessage().contains("lock.persistence.mode=jdbc"));
  }

  @Test
  void jdbcModeEnablesRepositoryBackedLifecycleRoutesOnlyAfterJdbcRepositoryIsWired() {
    LockPersistenceGate gate = new LockPersistenceGate();
    gate.setMode("jdbc");
    gate.markJdbcRepositoryAvailable(true);

    assertTrue(gate.lifecycleRoutesEnabled());
    assertDoesNotThrow(() -> gate.requireLifecycleRoutePersistence("POST /api/v1/tenants/{tenantId}/locks/requests"));
  }

  @Test
  void lifecycleRouteConstantsAreTenantScoped() {
    assertEquals("/api/v1/tenants/{tenantId}/locks/{lockId}", LockDetailApi.GET_LOCK_PATH);
    assertEquals("/api/v1/tenants/{tenantId}/locks/{lockId}/confirmations", LockConfirmationApi.POST_CONFIRMATION_PATH);
    assertEquals("/api/v1/tenants/{tenantId}/locks/{lockId}/extensions", LockExtensionApi.POST_EXTENSION_PATH);
  }
}
