package com.wcpe.scenario.domain;

import java.time.LocalDate;
import java.util.*;

final class RepresentativeCreditScorePolicy {

  private RepresentativeCreditScorePolicy() {}

  /**
   * LOWEST_REPRESENTATIVE_SCORE: minimum available score across occupying borrowers.
   * Excludes borrowers with FROZEN, MISSING, or NO_SCORE status.
   */
  static RepresentativeCreditResult derive(List<BorrowerCredit> borrowers) {
    List<Map<String, Object>> included = new ArrayList<>();
    List<Map<String, Object>> excluded = new ArrayList<>();
    Integer lowestScore = null;

    for (BorrowerCredit b : borrowers) {
      String extId = b.borrowerExternalId() != null ? b.borrowerExternalId() : b.borrowerRole();
      if (BorrowerCreditValidator.isScoreExcluded(b.creditStatus())) {
        excluded.add(Map.of(
            "borrowerExternalId", extId,
            "reason", b.creditStatus() + "_EXCLUDED"));
      } else if (b.occupiesProperty() && b.creditScore() != null) {
        included.add(Map.of(
            "borrowerExternalId", extId,
            "score", b.creditScore()));
        if (lowestScore == null || b.creditScore() < lowestScore) {
          lowestScore = b.creditScore();
        }
      } else if (b.creditScore() != null) {
        excluded.add(Map.of(
            "borrowerExternalId", extId,
            "reason", "NON_OCCUPANT_EXCLUDED"));
      }
    }

    String qualityStatus;
    if (included.isEmpty()) {
      qualityStatus = lowestScore != null ? "INCOMPLETE" : "MISSING";
    } else {
      qualityStatus = "COMPLETE";
    }

    return new RepresentativeCreditResult(lowestScore,
        "LOWEST_REPRESENTATIVE_SCORE",
        Map.of("included", included, "excluded", excluded),
        qualityStatus);
  }

  record RepresentativeCreditResult(Integer score, String rule, Map<String, Object> trace, String qualityStatus) {}
}
