package com.wcpe.margin;

import java.util.Optional;

final class ProcessLocalStatePolicy {
  static final String PERSISTENT_STORE_REQUIRED = "PERSISTENT_STORE_REQUIRED";
  static final String PERSISTENT_STORE_NOT_WIRED = "PERSISTENT_STORE_NOT_WIRED";
  static final String PROCESS_LOCAL_STORE_PROPERTY = "wcpe.margin.process-local-store.enabled";
  static final String DURABLE_STORE_PATH_PROPERTY = "wcpe.margin.durable-store.path";
  static final String DURABLE_STORE_PATH_ENV = "WCPE_MARGIN_DURABLE_STORE_PATH";

  private ProcessLocalStatePolicy() {}

  static void requireDurableStoreOrExplicitTestHarness(boolean enabled, String component) {
    if (!enabled) {
      throw new PersistenceNotConfiguredException(component + ":" + PERSISTENT_STORE_REQUIRED);
    }
  }

  static void requireProcessLocalStoreProfileOrFailClosed(String component) {
    if (processLocalStoreAllowed()) {
      return;
    }
    if (durableBackingConfigured()) {
      throw new PersistenceNotConfiguredException(component + ":" + PERSISTENT_STORE_NOT_WIRED
          + ": durable store configuration is present, but this service has no repository adapter wired for it");
    }
    throw new PersistenceNotConfiguredException(component + ":" + PERSISTENT_STORE_REQUIRED
        + ": configure " + DURABLE_STORE_PATH_PROPERTY + "/" + DURABLE_STORE_PATH_ENV
        + " or run with local/dev/test profile or explicit " + PROCESS_LOCAL_STORE_PROPERTY + "=true");
  }

  static Optional<String> durableStorePath() {
    return firstText(System.getProperty(DURABLE_STORE_PATH_PROPERTY), System.getenv(DURABLE_STORE_PATH_ENV));
  }

  static boolean processLocalStoreAllowed() {
    if (Boolean.getBoolean(PROCESS_LOCAL_STORE_PROPERTY)) {
      return true;
    }
    return profileAllowsProcessLocal(System.getProperty("spring.profiles.active"))
        || profileAllowsProcessLocal(System.getenv("SPRING_PROFILES_ACTIVE"))
        || profileAllowsProcessLocal(System.getenv("APP_ENV"));
  }

  static boolean durableBackingConfigured() {
    return durableStorePath().isPresent()
        || hasText(System.getProperty("spring.datasource.url"))
        || hasText(System.getenv("SPRING_DATASOURCE_URL"));
  }

  private static Optional<String> firstText(String... values) {
    for (String value : values) {
      if (hasText(value)) {
        return Optional.of(value.trim());
      }
    }
    return Optional.empty();
  }

  private static boolean profileAllowsProcessLocal(String profileCsv) {
    if (!hasText(profileCsv)) {
      return false;
    }
    for (String profile : profileCsv.split(",")) {
      String normalized = profile.trim().toLowerCase(java.util.Locale.ROOT);
      if (normalized.equals("local") || normalized.equals("dev") || normalized.equals("test")) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  static final class PersistenceNotConfiguredException extends IllegalStateException {
    PersistenceNotConfiguredException(String message) {
      super(message);
    }
  }
}
