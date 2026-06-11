package com.wcpe.exception.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Minimal in-process REST contract adapter for PII-11-S09 exception history endpoints.
 *
 * <p>The service module has no web framework dependency, so this controller records the
 * tenant-scoped endpoint paths and delegates request handling to the domain service. A
 * Spring/JAX-RS adapter can bind the same methods without changing the domain contract.</p>
 */
public final class ExceptionHistoryController {

  public static final String GET_EXCEPTION_HISTORY = "GET /api/v1/tenants/{tenantId}/exception-history";
  public static final String POST_EXCEPTION_HISTORY_REPLAY = "POST /api/v1/tenants/{tenantId}/exception-history/replay";
  public static final String POST_EXCEPTION_HISTORY_EXPORT = "POST /api/v1/tenants/{tenantId}/exception-history/export";
  public static final String GET_EXCEPTION_CONCESSION_WORKBENCH = "GET /api/v1/tenants/{tenantId}/exceptions/concessions/{caseId}/workbench";

  private final ExceptionService service;

  public ExceptionHistoryController(ExceptionService service) {
    this.service = Objects.requireNonNull(service, "service");
  }

  public ExceptionModels.ExceptionHistoryTimeline getExceptionHistory(
    ExceptionModels.ExceptionHistorySearchRequest request
  ) {
    return service.reconstructExceptionHistory(request);
  }

  public ExceptionModels.ExceptionHistoryReplayResult replayExceptionHistory(
    ExceptionModels.ExceptionHistorySearchRequest request,
    String expectedProjectionHash,
    List<String> historicalConfigVersionIds
  ) {
    return service.replayExceptionHistory(request, expectedProjectionHash, historicalConfigVersionIds);
  }

  public ExceptionModels.ExceptionHistoryExportPacket exportExceptionHistory(
    ExceptionModels.ExceptionHistorySearchRequest request,
    boolean includeEvidenceRefs,
    Instant expiresAt
  ) {
    return service.exportExceptionHistory(request, includeEvidenceRefs, expiresAt);
  }

  public ExceptionModels.ExceptionWorkbenchCase getExceptionConcessionWorkbench(
    java.util.UUID tenantId,
    String caseId,
    String quoteId
  ) {
    return service.exceptionConcessionWorkbench(tenantId, caseId, quoteId);
  }
}
