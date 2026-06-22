package com.wcpe.lock;

import java.time.Instant;
import java.util.UUID;

public final class LockDetailApi {
  public static final String GET_LOCK_METHOD = "GET";
  public static final String GET_LOCK_PATH = "/api/v1/tenants/{tenantId}/locks/{lockId}";

  private final LockService service;

  public LockDetailApi(LockService service) {
    if (service == null) {
      throw new LockServiceException("VALIDATION_FAILED", "lock service is required");
    }
    this.service = service;
  }

  public LockDetailResponse getLock(UUID tenantId, String lockId) {
    LockModels.LockDetailResponse response = service.getLockDetail(tenantId, lockId);
    return new LockDetailResponse(
      response.lockId(), response.status().name(), response.version(), response.createdAt(), response.expiresAt(),
      response.expirationBusinessDays(), response.calendarConfigHash(), response.expirationBreakdown()
    );
  }

  public record LockDetailResponse(
    String lockId,
    String status,
    int version,
    Instant createdAt,
    Instant expiresAt,
    int expirationBusinessDays,
    String calendarConfigHash,
    BusinessDayCalculator.ExpirationBreakdown expirationBreakdown
  ) {}
}
