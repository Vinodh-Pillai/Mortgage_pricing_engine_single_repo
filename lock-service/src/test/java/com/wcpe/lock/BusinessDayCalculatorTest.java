package com.wcpe.lock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BusinessDayCalculatorTest {
  private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  private final TenantCalendarConfig config = new TenantCalendarConfig(
    TENANT_ID,
    "America/Los_Angeles",
    WorkingHours.weekdays(LocalTime.of(9, 0), LocalTime.of(17, 0)),
    List.of(LocalDate.parse("2026-07-03")),
    LocalDate.parse("2026-01-01"),
    LocalDate.parse("2026-12-31")
  );
  private final BusinessDayCalculator calculator = new BusinessDayCalculator(tenantId -> config);

  @Test
  void fridayAfterHoursStartsMonday() {
    BusinessDayCalculator.ExpirationCalculation result = calculator.calculateExpiration(TENANT_ID, Instant.parse("2026-06-13T01:00:00Z"), 0);

    assertEquals(Instant.parse("2026-06-15T16:00:00Z"), result.adjustedStartAt());
    assertEquals(Instant.parse("2026-06-16T00:00:00Z"), result.expiresAt());
    assertEquals(0, result.breakdown().businessDaysAdded());
  }

  @Test
  void holidayExcluded() {
    BusinessDayCalculator.ExpirationCalculation result = calculator.calculateExpiration(TENANT_ID, Instant.parse("2026-06-30T23:00:00Z"), 3);

    assertEquals(Instant.parse("2026-07-07T00:00:00Z"), result.expiresAt());
    assertEquals(List.of(LocalDate.parse("2026-07-03")), result.breakdown().holidaysExcluded());
    assertEquals(2, result.breakdown().weekendsExcluded());
  }

  @Test
  void weekendExcluded() {
    BusinessDayCalculator.ExpirationCalculation result = calculator.calculateExpiration(TENANT_ID, Instant.parse("2026-06-12T23:00:00Z"), 1);

    assertEquals(Instant.parse("2026-06-16T00:00:00Z"), result.expiresAt());
    assertEquals(2, result.breakdown().weekendsExcluded());
  }

  @Test
  void timezoneConversion() {
    BusinessDayCalculator.ExpirationCalculation result = calculator.calculateExpiration(TENANT_ID, Instant.parse("2026-06-13T00:30:00Z"), 0);

    assertEquals(Instant.parse("2026-06-15T16:00:00Z"), result.adjustedStartAt());
    assertEquals(Instant.parse("2026-06-16T00:00:00Z"), result.expiresAt());
  }

  @Test
  void lockCreatedOnHoliday() {
    BusinessDayCalculator.ExpirationCalculation result = calculator.calculateExpiration(TENANT_ID, Instant.parse("2026-07-03T17:00:00Z"), 0);

    assertEquals(Instant.parse("2026-07-06T16:00:00Z"), result.adjustedStartAt());
    assertEquals(Instant.parse("2026-07-07T00:00:00Z"), result.expiresAt());
  }

  @Test
  void calendarConfigHashIsStableForAudit() {
    String first = BusinessDayCalculator.configHash(config);
    String second = BusinessDayCalculator.configHash(config);

    assertFalse(first.isBlank());
    assertEquals(64, first.length());
    assertEquals(first, second);
  }
}
