package com.wcpe.lock;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lock.persistence")
public final class LockPersistenceGate {
  private String mode = "fail-closed";
  private String readinessMessage = "Durable persistence is not enabled; lifecycle routes fail closed.";
  private boolean jdbcRepositoryAvailable;

  public String mode() {
    return mode;
  }

  public void setMode(String mode) {
    this.mode = mode;
  }

  public String readinessMessage() {
    return readinessMessage;
  }

  public void setReadinessMessage(String readinessMessage) {
    this.readinessMessage = readinessMessage;
  }

  public void markJdbcRepositoryAvailable(boolean jdbcRepositoryAvailable) {
    this.jdbcRepositoryAvailable = jdbcRepositoryAvailable;
  }

  public boolean lifecycleRoutesEnabled() {
    return "jdbc".equalsIgnoreCase(mode) && jdbcRepositoryAvailable;
  }

  public void requireLifecycleRoutePersistence(String route) {
    if (lifecycleRoutesEnabled()) {
      return;
    }
    throw new LockServiceException(
      "PERSISTENCE_NOT_DURABLE",
      "Lock lifecycle route " + route + " is fail-closed because durable JDBC persistence is not wired, even when lock.persistence.mode=jdbc is requested, and process-local storage is disabled. "
        + readinessMessage
    );
  }
}
