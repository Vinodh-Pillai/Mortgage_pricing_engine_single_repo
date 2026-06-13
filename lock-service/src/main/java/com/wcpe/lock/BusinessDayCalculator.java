package com.wcpe.lock;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

public final class BusinessDayCalculator {
  private final TenantCalendarClient tenantClient;

  public BusinessDayCalculator(TenantCalendarClient tenantClient) {
    if (tenantClient == null) {
      throw new LockServiceException("VALIDATION_FAILED", "tenant calendar client is required");
    }
    this.tenantClient = tenantClient;
  }

  public ExpirationCalculation calculateExpiration(UUID tenantId, Instant lockCreatedAt, int lockPeriodBusinessDays) {
    if (lockPeriodBusinessDays < 0) {
      throw new LockServiceException("VALIDATION_FAILED", "lockPeriodBusinessDays cannot be negative");
    }
    TenantCalendarConfig config = tenantClient.getCalendarConfig(tenantId);
    ZoneId zone = ZoneId.of(config.timezone());
    ZonedDateTime created = lockCreatedAt.atZone(zone);
    ZonedDateTime adjustedStart = adjustToBusinessStart(created, config);
    ZonedDateTime expiryDate = addBusinessDays(adjustedStart, lockPeriodBusinessDays, config);
    DayHours expiryHours = hoursFor(expiryDate, config);
    Instant expiresAt = expiryDate.with(expiryHours.close()).toInstant();
    ExpirationBreakdown breakdown = breakdown(adjustedStart, expiryDate, lockPeriodBusinessDays, config);
    return new ExpirationCalculation(
      expiresAt,
      adjustedStart.toInstant(),
      configHash(config),
      config.timezone(),
      workingHoursSummary(config),
      config.holidays().size(),
      breakdown,
      !adjustedStart.toInstant().equals(created.toInstant())
    );
  }

  ZonedDateTime adjustToBusinessStart(ZonedDateTime value, TenantCalendarConfig config) {
    ZonedDateTime current = value;
    while (true) {
      DayHours hours = hoursFor(current, config);
      if (isHoliday(current.toLocalDate(), config) || hours.closed() || !current.toLocalTime().isBefore(hours.close())) {
        current = current.plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        continue;
      }
      if (current.toLocalTime().isBefore(hours.open())) {
        return current.with(hours.open());
      }
      return current;
    }
  }

  ZonedDateTime addBusinessDays(ZonedDateTime start, int days, TenantCalendarConfig config) {
    if (days == 0) {
      return start;
    }
    ZonedDateTime current = start;
    int remaining = days;
    while (remaining > 0) {
      current = current.plusDays(1);
      if (isBusinessDay(current.toLocalDate(), config)) {
        remaining--;
      }
    }
    return current;
  }

  private ExpirationBreakdown breakdown(ZonedDateTime start, ZonedDateTime expiryDate, int businessDays, TenantCalendarConfig config) {
    int calendarDays = Math.max(0, (int) ChronoUnit.DAYS.between(start.toLocalDate(), expiryDate.toLocalDate()));
    int weekends = 0;
    List<LocalDate> holidays = new ArrayList<>();
    LocalDate current = start.toLocalDate().plusDays(1);
    while (!current.isAfter(expiryDate.toLocalDate())) {
      if (isHoliday(current, config) && !isWeekendClosed(current, config)) {
        holidays.add(current);
      } else if (isWeekendClosed(current, config)) {
        weekends++;
      }
      current = current.plusDays(1);
    }
    return new ExpirationBreakdown(businessDays, calendarDays, List.copyOf(holidays), weekends);
  }

  private boolean isBusinessDay(LocalDate date, TenantCalendarConfig config) {
    return !isHoliday(date, config) && !hoursFor(date, config).closed();
  }

  private boolean isHoliday(LocalDate date, TenantCalendarConfig config) {
    return config.holidays().contains(date);
  }

  private boolean isWeekendClosed(LocalDate date, TenantCalendarConfig config) {
    DayOfWeek day = date.getDayOfWeek();
    return (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) && hoursFor(date, config).closed();
  }

  private DayHours hoursFor(ZonedDateTime value, TenantCalendarConfig config) {
    return hoursFor(value.toLocalDate(), config);
  }

  private DayHours hoursFor(LocalDate value, TenantCalendarConfig config) {
    DayHours hours = config.workingHours().schedule().get(value.getDayOfWeek());
    if (hours == null || hours.closed()) {
      return DayHours.closedDay();
    }
    return hours;
  }

  private static String workingHoursSummary(TenantCalendarConfig config) {
    DayHours monday = config.workingHours().schedule().get(DayOfWeek.MONDAY);
    return monday == null || monday.closed() ? "configured schedule" : "Mon-Fri " + monday.open() + "-" + monday.close();
  }

  public static String configHash(TenantCalendarConfig config) {
    StringBuilder schedule = new StringBuilder();
    for (DayOfWeek day : DayOfWeek.values()) {
      DayHours hours = config.workingHours().schedule().get(day);
      schedule.append(day).append('=').append(hours == null ? "closed" : hours).append(';');
    }
    String canonical = config.tenantId() + "|" + config.timezone() + "|" + schedule + "|" + config.holidays()
      + "|" + config.effectiveFrom() + "|" + config.effectiveUntil();
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  public record ExpirationCalculation(
    Instant expiresAt,
    Instant adjustedStartAt,
    String calendarConfigHash,
    String timezone,
    String workingHoursSummary,
    int holidaysCount,
    ExpirationBreakdown breakdown,
    boolean startAdjusted
  ) {}

  public record ExpirationBreakdown(
    int businessDaysAdded,
    int calendarDaysElapsed,
    List<LocalDate> holidaysExcluded,
    int weekendsExcluded
  ) {}
}
