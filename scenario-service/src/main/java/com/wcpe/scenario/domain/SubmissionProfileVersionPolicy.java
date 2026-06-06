package com.wcpe.scenario.domain;

import java.time.Instant;
import java.util.Objects;

final class SubmissionProfileVersionPolicy {
  private static final Instant OPEN_ENDED = Instant.parse("9999-12-31T23:59:59Z");

  private SubmissionProfileVersionPolicy() {}

  static boolean windowsOverlap(Instant existingFromUtc, Instant existingToUtc,
      Instant requestedFromUtc, Instant requestedToUtc) {
    Objects.requireNonNull(existingFromUtc, "existingFromUtc");
    Objects.requireNonNull(requestedFromUtc, "requestedFromUtc");
    Instant existingTo = existingToUtc != null ? existingToUtc : OPEN_ENDED;
    Instant requestedTo = requestedToUtc != null ? requestedToUtc : OPEN_ENDED;
    return existingFromUtc.isBefore(requestedTo) && existingTo.isAfter(requestedFromUtc);
  }

  static int nextPublishedVersionNumber(SubmissionProfileVersion sourceVersion) {
    Objects.requireNonNull(sourceVersion, "sourceVersion");
    return sourceVersion.versionNumber() + 1;
  }
}
