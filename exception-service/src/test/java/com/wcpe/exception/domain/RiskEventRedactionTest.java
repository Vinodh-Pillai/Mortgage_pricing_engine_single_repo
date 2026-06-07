package com.wcpe.exception.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class RiskEventRedactionTest {

  @Test
  void rejectsPiiPayload() {
    ExceptionService service = new ExceptionService(new ExceptionRepository());

    ExceptionServiceException error = assertThrows(
      ExceptionServiceException.class,
      () -> service.publishRiskMonitoringEvent(
        RiskEventMapperTest.sourceEvent(
          "RISK-IDEMP-PII-001",
          Map.of("borrowerEmail", "borrower@example.com", "amountBucket", "configured-small")
        ),
        RiskEventMapperTest.mappingVersion(),
        false
      )
    );

    assertEquals("RAW_PII_NOT_ALLOWED", error.code());
  }
}
