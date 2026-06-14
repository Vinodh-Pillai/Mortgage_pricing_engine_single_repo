package com.wcpe.lock.nonqm;

import static com.wcpe.lock.nonqm.NonQmLockModels.*;

import com.wcpe.lock.LockServiceException;
import java.util.UUID;

public final class NonQmLockApi {
  public static final String POST_LOCK_REQUEST_METHOD = "POST";
  public static final String POST_LOCK_REQUEST_PATH = "/api/v1/locks/non-qm/request";
  public static final String POST_EXTENSION_METHOD = "POST";
  public static final String POST_EXTENSION_PATH = "/api/v1/locks/non-qm/{lockId}/extensions";
  public static final String GET_LOCK_DETAIL_METHOD = "GET";
  public static final String GET_LOCK_DETAIL_PATH = "/api/v1/locks/non-qm/{lockId}";
  public static final String GET_DELIVERY_PROFILE_METHOD = "GET";
  public static final String GET_DELIVERY_PROFILE_PATH = "/api/v1/secondary/non-qm/delivery-profiles/{profileCode}";
  public static final String POST_DELIVERY_PROFILE_IMPORT_METHOD = "POST";
  public static final String POST_DELIVERY_PROFILE_IMPORT_PATH = "/api/v1/secondary/non-qm/delivery-profiles/import";
  public static final String POST_FLOAT_DOWN_METHOD = "POST";
  public static final String POST_FLOAT_DOWN_PATH = "/api/v1/locks/non-qm/{lockId}/float-down";
  public static final String GET_COMMITMENT_LETTER_METHOD = "GET";
  public static final String GET_COMMITMENT_LETTER_PATH = "/api/v1/locks/non-qm/{lockId}/commitment-letter";

  private final NonQmLockService service;

  public NonQmLockApi(NonQmLockService service) {
    if (service == null) throw new LockServiceException("VALIDATION_FAILED", "Non-QM lock service is required");
    this.service = service;
  }

  public NonQmLockDecision postLockRequest(NonQmLockRequest request) {
    return service.requestLock(request);
  }

  public NonQmExtensionDecision postExtension(NonQmExtensionRequest request) {
    return service.requestExtension(request);
  }

  public NonQmLockRecord getLockDetail(UUID tenantId, String lockId) {
    return service.getLock(tenantId, lockId);
  }

  public void importDeliveryProfile(SecondaryDeliveryProfile profile) {
    service.importDeliveryProfile(profile);
  }

  public SecondaryDeliveryPackage getSecondaryDeliveryPackage(UUID tenantId, String lockId) {
    return service.secondaryDeliveryPackage(tenantId, lockId);
  }

  public FloatDownDecision postFloatDown(FloatDownRequest request) {
    return service.requestFloatDown(request);
  }

  public LockCommitmentLetter getCommitmentLetter(UUID tenantId, String lockId) {
    return service.commitmentLetter(tenantId, lockId);
  }
}
