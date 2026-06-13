package com.wcpe.lock;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface TenantCalendarClient {
  TenantCalendarConfig getCalendarConfig(UUID tenantId);

  static TenantCalendarClient configuredLocalDefault() {
    return tenantId -> TenantCalendarConfig.configuredDefault(tenantId);
  }
}

record TenantCalendarConfig(
  UUID tenantId,
  String timezone,
  WorkingHours workingHours,
  List<LocalDate> holidays,
  LocalDate effectiveFrom,
  LocalDate effectiveUntil
) {
  TenantCalendarConfig {
    if (tenantId == null) throw new LockServiceException("VALIDATION_FAILED", "tenantId is required");
    if (timezone == null || timezone.isBlank()) throw new LockServiceException("VALIDATION_FAILED", "timezone is required");
    if (workingHours == null) throw new LockServiceException("VALIDATION_FAILED", "workingHours is required");
    holidays = holidays == null ? List.of() : List.copyOf(holidays);
  }

  static TenantCalendarConfig configuredDefault(UUID tenantId) {
    return new TenantCalendarConfig(
      tenantId,
      "America/Los_Angeles",
      WorkingHours.weekdays(LocalTime.of(9, 0), LocalTime.of(17, 0)),
      List.of(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-07-04")),
      LocalDate.parse("2026-01-01"),
      LocalDate.parse("2026-12-31")
    );
  }
}

record WorkingHours(Map<DayOfWeek, DayHours> schedule) {
  WorkingHours {
    if (schedule == null || schedule.isEmpty()) throw new LockServiceException("VALIDATION_FAILED", "workingHours.schedule is required");
    EnumMap<DayOfWeek, DayHours> copy = new EnumMap<>(DayOfWeek.class);
    copy.putAll(schedule);
    for (DayOfWeek day : DayOfWeek.values()) {
      copy.putIfAbsent(day, DayHours.closedDay());
    }
    schedule = Map.copyOf(copy);
  }

  static WorkingHours weekdays(LocalTime open, LocalTime close) {
    EnumMap<DayOfWeek, DayHours> schedule = new EnumMap<>(DayOfWeek.class);
    for (DayOfWeek day : DayOfWeek.values()) {
      schedule.put(day, day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY ? DayHours.closedDay() : new DayHours(open, close, false));
    }
    return new WorkingHours(schedule);
  }
}

record DayHours(LocalTime open, LocalTime close, boolean closed) {
  DayHours {
    if (!closed && (open == null || close == null || !close.isAfter(open))) {
      throw new LockServiceException("VALIDATION_FAILED", "open and close are required for working days");
    }
  }

  static DayHours closedDay() {
    return new DayHours(null, null, true);
  }
}
