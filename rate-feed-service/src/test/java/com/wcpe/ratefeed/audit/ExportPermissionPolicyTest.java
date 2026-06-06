package com.wcpe.ratefeed.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.ratefeed.domain.RateFeedModels;
import com.wcpe.ratefeed.domain.RateFeedModels.RateFeedException;
import com.wcpe.ratefeed.domain.RequestContext;
import com.wcpe.ratefeed.role.RateFeedRoles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExportPermissionPolicyTest {

  @AfterEach
  void clearRequestContext() throws Exception {
    Method clear = RequestContext.class.getDeclaredMethod("clear");
    clear.setAccessible(true);
    clear.invoke(null);
  }

  @Test
  void export_standard_requires_audit_view() throws Exception {
    AuditReportService service = serviceWithNoBatchEvents();
    setRoles(RateFeedRoles.RATE_FEED_AUDIT_VIEW);

    RateFeedException ex = assertThrows(RateFeedException.class,
        () -> service.createExport(UUID.randomUUID(), UUID.randomUUID(),
            new RateFeedModels.AuditExportRequest(RateFeedModels.AuditReportFormat.JSON, false, "AUDIT_REPORT"),
            "actor-1", UUID.randomUUID().toString()));

    assertEquals("BATCH_NOT_FOUND", ex.code(), "RATE_FEED_AUDIT_VIEW should pass authorization and reach data lookup");
  }

  @Test
  void export_raw_requires_audit_export() throws Exception {
    AuditReportService service = serviceWithNoBatchEvents();
    setRoles(RateFeedRoles.RATE_FEED_AUDIT_VIEW);

    RateFeedException ex = assertThrows(RateFeedException.class,
        () -> service.createExport(UUID.randomUUID(), UUID.randomUUID(),
            new RateFeedModels.AuditExportRequest(RateFeedModels.AuditReportFormat.JSON, true, "RAW_AUDIT_REPORT"),
            "actor-1", UUID.randomUUID().toString()));

    assertEquals("ELEVATED_EXPORT_PERMISSION_REQUIRED", ex.code());
  }

  @Test
  void export_requires_governed_reason_code() throws Exception {
    AuditReportService service = serviceWithNoBatchEvents();
    setRoles(RateFeedRoles.RATE_FEED_AUDIT_VIEW);

    RateFeedException ex = assertThrows(RateFeedException.class,
        () -> service.createExport(UUID.randomUUID(), UUID.randomUUID(),
            new RateFeedModels.AuditExportRequest(RateFeedModels.AuditReportFormat.JSON, false, ""),
            "actor-1", UUID.randomUUID().toString()));

    assertEquals("POLICY_NOT_SATISFIED", ex.code());
  }

  @Test
  void export_denies_without_role() {
    AuditReportService service = serviceWithNoBatchEvents();

    RateFeedException ex = assertThrows(RateFeedException.class,
        () -> service.createExport(UUID.randomUUID(), UUID.randomUUID(),
            new RateFeedModels.AuditExportRequest(RateFeedModels.AuditReportFormat.JSON, false, "AUDIT_REPORT"),
            "actor-1", UUID.randomUUID().toString()));

    assertEquals("AUDIT_ACCESS_DENIED", ex.code());
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private AuditReportService serviceWithNoBatchEvents() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.query(startsWith("select e.audit_event_id"), ArgumentMatchers.<RowMapper<Object>>any(), any(), any(), any()))
        .thenReturn(List.of());
    return new AuditReportService(jdbc, new ObjectMapper(), 2555);
  }

  private void setRoles(String roles) throws Exception {
    Method method = RequestContext.class.getDeclaredMethod("roles", String.class);
    method.setAccessible(true);
    method.invoke(null, roles);
  }
}
