package com.wcpe.tenantcontext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class TenantCalendarConfigServiceTest {
    private final TenantCalendarConfigService service = new TenantCalendarConfigService();

    @Test
    void exposesCurrentTenantCalendarForLockService() {
        TenantCalendarConfig config = service.currentCalendar("tenant-alpha");

        assertThat(TenantCalendarController.GET_CURRENT_TENANT_PATH).isEqualTo("/api/tenant/current");
        assertThat(config.tenantId()).isEqualTo("tenant-alpha");
        assertThat(config.timezone()).isEqualTo("America/Los_Angeles");
        assertThat(config.workingHours().schedule().get(DayOfWeek.MONDAY).open()).isEqualTo(LocalTime.of(9, 0));
        assertThat(config.workingHours().schedule().get(DayOfWeek.SATURDAY).closed()).isTrue();
        assertThat(config.holidays()).contains(LocalDate.parse("2026-07-04"));
    }

    @Test
    void rejectsMissingTenantWithoutDefaultFallback() {
        assertThatThrownBy(() -> service.currentCalendar(" "))
            .isInstanceOf(TenantContextValidationException.class)
            .extracting(error -> ((TenantContextValidationException) error).code())
            .isEqualTo("TENANT_CONTEXT_MISSING");
    }
}
