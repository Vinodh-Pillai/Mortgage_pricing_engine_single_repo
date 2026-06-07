package com.wcpe.observability.scenariohash;

public record HashSchemaVersion(int value) {
  public HashSchemaVersion {
    if (value < 1) {
      throw new IllegalArgumentException("hash schema version must be positive");
    }
  }
}
