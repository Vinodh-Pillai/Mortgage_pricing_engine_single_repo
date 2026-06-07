package com.wcpe.observability.cache;

public enum ReferenceDataChangeType {
  PUBLISHED("ReferenceDataPublished.v1"),
  SUSPENDED("ReferenceDataSuspended.v1"),
  ROLLED_BACK("ReferenceDataRolledBack.v1");

  private final String eventType;

  ReferenceDataChangeType(String eventType) {
    this.eventType = eventType;
  }

  public String eventType() {
    return eventType;
  }
}
