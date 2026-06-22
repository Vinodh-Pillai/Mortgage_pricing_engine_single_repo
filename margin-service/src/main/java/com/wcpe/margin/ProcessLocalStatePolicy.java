package com.wcpe.margin;

final class ProcessLocalStatePolicy {
  static final String PERSISTENT_STORE_REQUIRED = "PERSISTENT_STORE_REQUIRED";

  private ProcessLocalStatePolicy() {}

  static void requireDurableStoreOrExplicitTestHarness(boolean enabled, String component) {
    if (!enabled) {
      throw new PersistenceNotConfiguredException(component + ":" + PERSISTENT_STORE_REQUIRED);
    }
  }

  static final class PersistenceNotConfiguredException extends IllegalStateException {
    PersistenceNotConfiguredException(String message) {
      super(message);
    }
  }
}
