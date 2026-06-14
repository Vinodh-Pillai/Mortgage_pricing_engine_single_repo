package com.wcpe.lock.nonqm;

import com.wcpe.lock.BusinessDayCalculator;
import com.wcpe.lock.LockServiceException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class NonQmLockModels {
  private NonQmLockModels() {}

  public enum NonQmProductType {
    DSCR, BANK_STATEMENT, FIX_FLIP, CONSTRUCTION, ASSET_DEPLETION, FOREIGN_NATIONAL, BUSINESS_PURPOSE, REVERSE
  }

  public enum ExtensionType {
    STANDARD, CONSTRUCTION_DRAW, FIX_FLIP_TERM_EXTENSION
  }

  public enum DeliveryType {
    BEST_EFFORTS, MANDATORY, BULK, FLOW, GNMA_HECM, PRIVATE_POOL
  }

  public enum LockStatus {
    ACTIVE, PENDING_SECONDARY_REVIEW, EXPIRED, CANCELLED
  }

  public enum ExtensionStatus {
    APPROVED, PENDING_EVIDENCE, REJECTED
  }

  public enum FloatDownStatus {
    AVAILABLE, INELIGIBLE, APPROVED, REJECTED
  }

  public record LockTermOption(
    String termCode,
    int termDays,
    BigDecimal lockPriceAdjustmentBps,
    boolean floatDownEligible,
    String floatDownRuleRef,
    BigDecimal minimumImprovementBps,
    List<String> investorConditionRefs
  ) {
    public LockTermOption {
      requireText(termCode, "termCode");
      if (termDays <= 0) throw new LockServiceException("VALIDATION_FAILED", "termDays must be positive");
      lockPriceAdjustmentBps = lockPriceAdjustmentBps == null ? BigDecimal.ZERO : lockPriceAdjustmentBps;
      minimumImprovementBps = minimumImprovementBps == null ? BigDecimal.ZERO : minimumImprovementBps;
      investorConditionRefs = List.copyOf(investorConditionRefs == null ? List.of() : investorConditionRefs);
    }
  }

  public record ExtensionPolicy(
    String extensionCode,
    ExtensionType extensionType,
    int maxExtensions,
    int maxExtensionBusinessDays,
    String feeRuleRef,
    String approvalRuleRef,
    BigDecimal priceImpactBps,
    List<String> requiredEvidenceTypes
  ) {
    public ExtensionPolicy {
      requireText(extensionCode, "extensionCode");
      if (extensionType == null) throw new LockServiceException("VALIDATION_FAILED", "extensionType is required");
      if (maxExtensions < 0) throw new LockServiceException("VALIDATION_FAILED", "maxExtensions cannot be negative");
      if (maxExtensionBusinessDays <= 0) throw new LockServiceException("VALIDATION_FAILED", "maxExtensionBusinessDays must be positive");
      requireText(feeRuleRef, "feeRuleRef");
      requireText(approvalRuleRef, "approvalRuleRef");
      priceImpactBps = priceImpactBps == null ? BigDecimal.ZERO : priceImpactBps;
      requiredEvidenceTypes = List.copyOf(requiredEvidenceTypes == null ? List.of() : requiredEvidenceTypes);
    }
  }

  public record SecondaryDeliveryProfile(
    String profileCode,
    String investorCode,
    DeliveryType deliveryType,
    List<String> guidelineRefs,
    List<String> poolingAttributes,
    List<String> requiredPreDeliveryConditions,
    Map<String, String> investorCustomFields,
    String status
  ) {
    public SecondaryDeliveryProfile {
      requireText(profileCode, "profileCode");
      requireText(investorCode, "investorCode");
      if (deliveryType == null) throw new LockServiceException("VALIDATION_FAILED", "deliveryType is required");
      guidelineRefs = List.copyOf(guidelineRefs == null ? List.of() : guidelineRefs);
      poolingAttributes = List.copyOf(poolingAttributes == null ? List.of() : poolingAttributes);
      requiredPreDeliveryConditions = List.copyOf(requiredPreDeliveryConditions == null ? List.of() : requiredPreDeliveryConditions);
      investorCustomFields = Map.copyOf(investorCustomFields == null ? Map.of() : investorCustomFields);
      status = normalizedStatus(status);
    }
  }

  public record NonQmLockPolicy(
    String policyCode,
    String investorCode,
    String channelCode,
    NonQmProductType productType,
    List<LockTermOption> termOptions,
    List<ExtensionPolicy> extensionPolicies,
    SecondaryDeliveryProfile deliveryProfile,
    int version,
    String status,
    Instant effectiveStart,
    Instant effectiveEnd
  ) {
    public NonQmLockPolicy {
      requireText(policyCode, "policyCode");
      requireText(investorCode, "investorCode");
      requireText(channelCode, "channelCode");
      if (productType == null) throw new LockServiceException("VALIDATION_FAILED", "productType is required");
      termOptions = List.copyOf(termOptions == null ? List.of() : termOptions);
      extensionPolicies = List.copyOf(extensionPolicies == null ? List.of() : extensionPolicies);
      if (deliveryProfile == null) throw new LockServiceException("VALIDATION_FAILED", "deliveryProfile is required");
      if (version <= 0) throw new LockServiceException("VALIDATION_FAILED", "version must be positive");
      status = normalizedStatus(status);
      if (effectiveStart == null) throw new LockServiceException("VALIDATION_FAILED", "effectiveStart is required");
      if (effectiveEnd != null && !effectiveEnd.isAfter(effectiveStart)) {
        throw new LockServiceException("VALIDATION_FAILED", "effectiveEnd must be after effectiveStart");
      }
    }
  }

  public record PricingWaterfallSnapshot(
    String quoteId,
    String investorCode,
    String channelCode,
    NonQmProductType productType,
    String waterfallRef,
    String auditHash,
    Instant pricedAt,
    Map<String, String> pricingEvidenceRefs
  ) {
    public PricingWaterfallSnapshot {
      requireText(quoteId, "quoteId");
      requireText(investorCode, "investorCode");
      requireText(channelCode, "channelCode");
      if (productType == null) throw new LockServiceException("VALIDATION_FAILED", "productType is required");
      requireText(waterfallRef, "waterfallRef");
      requireText(auditHash, "auditHash");
      if (pricedAt == null) throw new LockServiceException("VALIDATION_FAILED", "pricedAt is required");
      pricingEvidenceRefs = Map.copyOf(pricingEvidenceRefs == null ? Map.of() : pricingEvidenceRefs);
    }
  }

  public record NonQmLockRequest(
    UUID tenantId,
    String requestId,
    String actorId,
    PricingWaterfallSnapshot pricingWaterfall,
    int requestedTermDays,
    Instant requestedAt,
    String quoteWaterfallHashAtRequest,
    String lockDeskOverrideArtifactRef,
    Map<String, String> projectContext,
    String idempotencyKey,
    String correlationId
  ) {
    public NonQmLockRequest {
      if (tenantId == null) throw new LockServiceException("VALIDATION_FAILED", "tenantId is required");
      requireText(requestId, "requestId");
      requireText(actorId, "actorId");
      if (pricingWaterfall == null) throw new LockServiceException("VALIDATION_FAILED", "pricingWaterfall is required");
      if (requestedTermDays <= 0) throw new LockServiceException("VALIDATION_FAILED", "requestedTermDays must be positive");
      if (requestedAt == null) throw new LockServiceException("VALIDATION_FAILED", "requestedAt is required");
      requireText(quoteWaterfallHashAtRequest, "quoteWaterfallHashAtRequest");
      projectContext = Map.copyOf(projectContext == null ? Map.of() : projectContext);
      requireText(idempotencyKey, "idempotencyKey");
      requireText(correlationId, "correlationId");
    }
  }

  public record NonQmLockRecord(
    UUID tenantId,
    String lockId,
    String requestId,
    String quoteId,
    NonQmProductType productType,
    String investorCode,
    String channelCode,
    String policyCode,
    int policyVersion,
    String termCode,
    int lockPeriodBusinessDays,
    Instant lockedAt,
    Instant expiresAt,
    BusinessDayCalculator.ExpirationBreakdown expirationBreakdown,
    LockStatus status,
    int extensionCount,
    SecondaryDeliveryProfile deliveryProfile,
    String pricingWaterfallRef,
    String pricingAuditHash,
    BigDecimal lockPriceAdjustmentBps,
    Map<String, String> projectContext,
    String auditRef,
    String outboxEventType
  ) {}

  public record NonQmLockDecision(
    String lockId,
    String policyCode,
    int termDays,
    Instant expiresAt,
    DeliveryType deliveryType,
    LockStatus status,
    List<String> validationMessages,
    String auditRef,
    String outboxEventType
  ) {}

  public record NonQmExtensionRequest(
    UUID tenantId,
    String lockId,
    String requestId,
    String actorId,
    ExtensionType extensionType,
    int requestedExtensionBusinessDays,
    Map<String, String> projectContext,
    List<String> evidenceRefs,
    Instant requestedAt,
    String idempotencyKey,
    String correlationId
  ) {
    public NonQmExtensionRequest {
      if (tenantId == null) throw new LockServiceException("VALIDATION_FAILED", "tenantId is required");
      requireText(lockId, "lockId");
      requireText(requestId, "requestId");
      requireText(actorId, "actorId");
      if (extensionType == null) throw new LockServiceException("VALIDATION_FAILED", "extensionType is required");
      if (requestedExtensionBusinessDays <= 0) throw new LockServiceException("VALIDATION_FAILED", "requestedExtensionBusinessDays must be positive");
      projectContext = Map.copyOf(projectContext == null ? Map.of() : projectContext);
      evidenceRefs = List.copyOf(evidenceRefs == null ? List.of() : evidenceRefs);
      if (requestedAt == null) throw new LockServiceException("VALIDATION_FAILED", "requestedAt is required");
      requireText(idempotencyKey, "idempotencyKey");
      requireText(correlationId, "correlationId");
    }
  }

  public record ExtensionFeeResult(
    String feeRuleRef,
    BigDecimal priceImpactBps,
    String pricingEvidenceRef,
    String calculationSummary
  ) {}

  public record NonQmExtensionDecision(
    String extensionId,
    String lockId,
    ExtensionStatus status,
    ExtensionType extensionType,
    int requestedExtensionBusinessDays,
    Instant previousExpiresAt,
    Instant requestedExpiresAt,
    ExtensionFeeResult feeResult,
    List<String> missingEvidenceTypes,
    List<String> reasonCodes,
    String auditRef,
    String outboxEventType
  ) {}

  public record FloatDownRequest(
    UUID tenantId,
    String lockId,
    String actorId,
    BigDecimal currentPriceImprovementBps,
    String pricingEvidenceRef,
    Instant requestedAt,
    String idempotencyKey,
    String correlationId
  ) {
    public FloatDownRequest {
      if (tenantId == null) throw new LockServiceException("VALIDATION_FAILED", "tenantId is required");
      requireText(lockId, "lockId");
      requireText(actorId, "actorId");
      currentPriceImprovementBps = currentPriceImprovementBps == null ? BigDecimal.ZERO : currentPriceImprovementBps;
      requireText(pricingEvidenceRef, "pricingEvidenceRef");
      if (requestedAt == null) throw new LockServiceException("VALIDATION_FAILED", "requestedAt is required");
      requireText(idempotencyKey, "idempotencyKey");
      requireText(correlationId, "correlationId");
    }
  }

  public record FloatDownDecision(
    String lockId,
    FloatDownStatus status,
    String ruleRef,
    BigDecimal requiredImprovementBps,
    BigDecimal observedImprovementBps,
    String pricingEvidenceRef,
    List<String> reasonCodes,
    String auditRef,
    String outboxEventType
  ) {}

  public record SecondaryDeliveryPackage(
    String lockId,
    String profileCode,
    DeliveryType deliveryType,
    List<String> guidelineRefs,
    List<String> poolingAttributes,
    List<String> requiredPreDeliveryConditions,
    Map<String, String> exportFields,
    String auditRef,
    String outboxEventType
  ) {}

  public record LockCommitmentLetter(
    String lockId,
    String letterRef,
    String productType,
    String investorCode,
    int lockPeriodBusinessDays,
    Instant expiresAt,
    List<String> borrowerVisibleAssumptions,
    List<String> secondaryGuidelineRefs,
    String pricingWaterfallRef,
    String auditRef
  ) {}

  static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new LockServiceException("VALIDATION_FAILED", field + " is required");
    }
  }

  static String normalizedStatus(String status) {
    return status == null || status.isBlank() ? "ACTIVE" : status.trim().toUpperCase();
  }
}
