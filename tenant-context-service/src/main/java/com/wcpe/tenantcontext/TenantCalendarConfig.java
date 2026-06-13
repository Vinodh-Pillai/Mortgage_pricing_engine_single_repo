package com.wcpe.tenantcontext;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record TenantCalendarConfig(
    String tenantId,
    String timezone,
    WorkingHours workingHours,
    List<LocalDate> holidays,
    LocalDate effectiveFrom,
    LocalDate effectiveUntil
) {
    public TenantCalendarConfig {
        if (tenantId == null || tenantId.isBlank()) {
            throw new TenantContextValidationException("TENANT_CONTEXT_MISSING", "tenantId is required");
        }
        if (timezone == null || timezone.isBlank()) {
            throw new TenantContextValidationException("TENANT_CONTEXT_MISSING", "timezone is required");
        }
        if (workingHours == null) {
            throw new TenantContextValidationException("TENANT_CONTEXT_MISSING", "workingHours is required");
        }
        holidays = holidays == null ? List.of() : List.copyOf(holidays);
    }

    public static TenantCalendarConfig configuredDefault(String tenantId) {
        return new TenantCalendarConfig(
            tenantId,
            "America/Los_Angeles",
            WorkingHours.weekdays(LocalTime.of(9, 0), LocalTime.of(17, 0)),
            List.of(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-07-04")),
            LocalDate.parse("2026-01-01"),
            LocalDate.parse("2026-12-31")
        );
    }

    public record WorkingHours(Map<DayOfWeek, DayHours> schedule) {
        public WorkingHours {
            if (schedule == null || schedule.isEmpty()) {
                throw new TenantContextValidationException("TENANT_CONTEXT_MISSING", "workingHours.schedule is required");
            }
            EnumMap<DayOfWeek, DayHours> copy = new EnumMap<>(DayOfWeek.class);
            copy.putAll(schedule);
            for (DayOfWeek day : DayOfWeek.values()) {
                copy.putIfAbsent(day, DayHours.closedDay());
            }
            schedule = Map.copyOf(copy);
        }

        public static WorkingHours weekdays(LocalTime open, LocalTime close) {
            EnumMap<DayOfWeek, DayHours> schedule = new EnumMap<>(DayOfWeek.class);
            for (DayOfWeek day : DayOfWeek.values()) {
                schedule.put(day, day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY ? DayHours.closedDay() : new DayHours(open, close, false));
            }
            return new WorkingHours(schedule);
        }
    }

    public record DayHours(LocalTime open, LocalTime close, boolean closed) {
        public DayHours {
            if (!closed && (open == null || close == null || !close.isAfter(open))) {
                throw new TenantContextValidationException("TENANT_CONTEXT_MALFORMED", "open and close are required for working days");
            }
        }

        public static DayHours closedDay() {
            return new DayHours(null, null, true);
        }
    }
}
