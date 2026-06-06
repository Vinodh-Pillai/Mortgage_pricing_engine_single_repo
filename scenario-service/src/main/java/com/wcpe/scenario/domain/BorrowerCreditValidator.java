package com.wcpe.scenario.domain;

import java.time.LocalDate;
import java.util.List;

final class BorrowerCreditValidator {

  private BorrowerCreditValidator() {}

  static void validateScoreRange(Integer score) {
    if (score != null && (score < 300 || score > 850)) {
      throw new ScenarioException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
          "CREDIT_SCORE_OUT_OF_RANGE", "Credit score must be between 300 and 850.", List.of(
          new ValidationIssue("CREDIT_SCORE_OUT_OF_RANGE", "creditScore", Severity.BLOCKING, "Credit score must be between 300 and 850.")));
    }
  }

  static void validateScoreDate(LocalDate date) {
    if (date != null && date.isAfter(LocalDate.now())) {
      throw new ScenarioException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
          "FUTURE_CREDIT_SCORE_DATE", "Credit score date cannot be in the future.", List.of());
    }
  }

  static void validateExactlyOnePrimary(List<BorrowerCredit> borrowers) {
    long primaryCount = borrowers.stream().filter(b -> "PRIMARY".equals(b.borrowerRole())).count();
    if (primaryCount > 1) {
      throw new ScenarioException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
          "DUPLICATE_PRIMARY_BORROWER", "Exactly one primary borrower is required.", List.of(
          new ValidationIssue("DUPLICATE_PRIMARY_BORROWER", "borrowers.borrowerRole", Severity.BLOCKING, "Exactly one primary borrower is required.")));
    }
    if (primaryCount == 0 && !borrowers.isEmpty()) {
      throw new ScenarioException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
          "MISSING_PRIMARY_BORROWER", "At least one primary borrower is required.", List.of());
    }
  }

  static void validateCreditStatusAndScore(String creditStatus, Integer creditScore) {
    if ("AVAILABLE".equals(creditStatus) && creditScore == null) {
      throw new ScenarioException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
          "MISSING_CREDIT_SCORE", "Available credit status requires a credit score.", List.of());
    }
  }

  static boolean isScoreAvailable(String creditStatus) {
    return "AVAILABLE".equals(creditStatus);
  }

  static boolean isScoreExcluded(String creditStatus) {
    return "FROZEN".equals(creditStatus) || "MISSING".equals(creditStatus) || "NO_SCORE".equals(creditStatus);
  }
}
