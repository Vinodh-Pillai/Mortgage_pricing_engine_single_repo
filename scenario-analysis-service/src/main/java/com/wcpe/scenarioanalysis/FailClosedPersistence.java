package com.wcpe.scenarioanalysis;

final class FailClosedPersistence {
  private FailClosedPersistence() {}

  static IllegalStateException notConfigured(String storeName) {
    return new IllegalStateException(storeName
        + " requires a durable repository bean; production volatile store-of-record is disabled.");
  }
}
