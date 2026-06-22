package com.wcpe.lock.nonqm;

import static com.wcpe.lock.nonqm.NonQmLockModels.*;

import com.wcpe.lock.BusinessDayCalculator;
import com.wcpe.lock.LockServiceException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class NonQmLockService {
  private final BusinessDayCalculator businessDayCalculator;
  private final NonQmLockRepository repository;

  public NonQmLockService(BusinessDayCalculator businessDayCalculator) {
    this(businessDayCalculator, new FailClosedNonQmLockRepository());
  }

  NonQmLockService(BusinessDayCalculator businessDayCalculator, NonQmLockRepository repository) {
    if (businessDayCalculator == null) {
      throw new LockServiceException("VALIDATION_FAILED", "businessDayCalculator is required");
    }
    if (repository == null) {
      throw new LockServiceException("VALIDATION_FAILED", "Non-QM lock repository is required");
    }
    this.businessDayCalculator = businessDayCalculator;
    this.repository = repository;
  }

  public void importDeliveryProfile(SecondaryDeliveryProfile profile) {
    if (profile == null) throw new LockServiceException("VALIDATION_FAILED", "delivery profile is required");
    repository.saveDeliveryProfile(profile);
    audit("NON_QM_SECONDARY_PROFILE_IMPORTED", profile.profileCode(), profile.investorCode(), "non_qm.secondary_profile.imported.v1");
  }

  public void importPolicy(NonQmLockPolicy policy) {
    if (policy == null) throw new LockServiceException("VALIDATION_FAILED", "lock policy is required");
    SecondaryDeliveryProfile profile = repository.findDeliveryProfile(policy.deliveryProfile().profileCode()).orElse(policy.deliveryProfile());
    if (!"ACTIVE".equals(profile.status())) {
      throw new LockServiceException("SECONDARY_PROFILE_INACTIVE", "Investor delivery profile must be active before policy import");
    }
    if (!profile.investorCode().equals(policy.investorCode())) {
      throw new LockServiceException("VALIDATION_FAILED", "policy investor must match delivery profile investor");
    }
    if (policy.termOptions().isEmpty()) {
      throw new LockServiceException("VALIDATION_FAILED", "Non-QM lock policy requires at least one term option");
    }
    repository.savePolicy(policy);
    repository.saveDeliveryProfile(profile);
    audit("NON_QM_LOCK_POLICY_IMPORTED", policy.policyCode(), policy.investorCode(), "non_qm.lock_policy.imported.v1");
  }

  public NonQmLockDecision requestLock(NonQmLockRequest request) {
    validateWaterfallFreshness(request);
    String payloadHash = hash(request);
    Object replay = replay(request.tenantId(), request.idempotencyKey(), payloadHash);
    if (replay instanceof NonQmLockDecision decision) return decision;

    NonQmLockPolicy policy = resolvePolicy(request.pricingWaterfall(), request.requestedAt())
      .orElseThrow(() -> new LockServiceException("LOCK_POLICY_MISSING", "No active Non-QM lock policy matched investor/channel/product/effective date"));
    LockTermOption term = selectTerm(policy, request.requestedTermDays());
    SecondaryDeliveryProfile deliveryProfile = policy.deliveryProfile();
    if (!"ACTIVE".equals(deliveryProfile.status())) {
      throw new LockServiceException("SECONDARY_PROFILE_INACTIVE", "Delivery profile is inactive and needs secondary review");
    }
    BusinessDayCalculator.ExpirationCalculation expiration = businessDayCalculator.calculateExpiration(
      request.tenantId(), request.requestedAt(), term.termDays()
    );
    String lockId = stableId(request.tenantId(), request.requestId(), request.idempotencyKey());
    String auditRef = "AUDIT-NONQM-LOCK-" + lockId;
    NonQmLockRecord record = new NonQmLockRecord(
      request.tenantId(), lockId, request.requestId(), request.pricingWaterfall().quoteId(), request.pricingWaterfall().productType(),
      request.pricingWaterfall().investorCode(), request.pricingWaterfall().channelCode(), policy.policyCode(), policy.version(),
      term.termCode(), term.termDays(), request.requestedAt(), expiration.expiresAt(), expiration.breakdown(), LockStatus.ACTIVE, 0,
      deliveryProfile, request.pricingWaterfall().waterfallRef(), request.pricingWaterfall().auditHash(), term.lockPriceAdjustmentBps(),
      request.projectContext(), auditRef, "non_qm.lock_requested.v1"
    );
    repository.saveLock(record);
    NonQmLockDecision decision = new NonQmLockDecision(
      lockId, policy.policyCode(), term.termDays(), expiration.expiresAt(), deliveryProfile.deliveryType(), record.status(),
      List.of("Non-QM policy selected", "Secondary profile assigned", "Expiration calculated with tenant business-day calendar"),
      auditRef, "non_qm.lock_requested.v1"
    );
    repository.saveIdempotency(request.tenantId(), request.idempotencyKey(), payloadHash, decision);
    audit("NON_QM_LOCK_REQUESTED", lockId, policy.policyCode(), decision.outboxEventType());
    return decision;
  }

  public NonQmLockRecord getLock(UUID tenantId, String lockId) {
    return repository.findLock(tenantId, lockId)
      .orElseThrow(() -> new LockServiceException("NOT_FOUND", "Non-QM lock was not found for tenant"));
  }

  public NonQmExtensionDecision requestExtension(NonQmExtensionRequest request) {
    String payloadHash = hash(request);
    Object replay = replay(request.tenantId(), request.idempotencyKey(), payloadHash);
    if (replay instanceof NonQmExtensionDecision decision) return decision;

    NonQmLockRecord lock = getLock(request.tenantId(), request.lockId());
    NonQmLockPolicy policy = repository.findPolicy(lock.policyCode()).orElse(null);
    if (policy == null) throw new LockServiceException("LOCK_POLICY_MISSING", "Policy used by lock is not available");
    ExtensionPolicy extensionPolicy = policy.extensionPolicies().stream()
      .filter(candidate -> candidate.extensionType() == request.extensionType())
      .findFirst()
      .orElseThrow(() -> new LockServiceException("EXTENSION_POLICY_MISSING", "No Non-QM extension policy matched requested extension type"));
    if (lock.extensionCount() >= extensionPolicy.maxExtensions()) {
      return rejectedExtension(request, lock, extensionPolicy, "EXTENSION_LIMIT_EXCEEDED");
    }
    if (request.requestedExtensionBusinessDays() > extensionPolicy.maxExtensionBusinessDays()) {
      return rejectedExtension(request, lock, extensionPolicy, "EXTENSION_DAYS_EXCEED_POLICY");
    }
    List<String> missingEvidence = missingEvidence(extensionPolicy, request.evidenceRefs());
    BusinessDayCalculator.ExpirationCalculation expiration = businessDayCalculator.calculateExpiration(
      request.tenantId(), lock.expiresAt(), request.requestedExtensionBusinessDays()
    );
    ExtensionFeeResult fee = new ExtensionFeeResult(
      extensionPolicy.feeRuleRef(), extensionPolicy.priceImpactBps(), lock.pricingWaterfallRef(),
      "Configured Non-QM extension price impact; no hidden fee math"
    );
    String extensionId = stableId(request.tenantId(), request.requestId(), request.idempotencyKey());
    ExtensionStatus status = missingEvidence.isEmpty() ? ExtensionStatus.APPROVED : ExtensionStatus.PENDING_EVIDENCE;
    String eventType = status == ExtensionStatus.APPROVED ? "non_qm.lock_extension_decisioned.v1" : "non_qm.lock_extension_requested.v1";
    NonQmExtensionDecision decision = new NonQmExtensionDecision(
      extensionId, lock.lockId(), status, request.extensionType(), request.requestedExtensionBusinessDays(), lock.expiresAt(),
      expiration.expiresAt(), fee, missingEvidence, missingEvidence.isEmpty() ? List.of("EXTENSION_POLICY_SATISFIED") : List.of("EVIDENCE_REQUIRED"),
      "AUDIT-NONQM-EXT-" + extensionId, eventType
    );
    repository.saveExtension(decision);
    repository.saveIdempotency(request.tenantId(), request.idempotencyKey(), payloadHash, decision);
    if (status == ExtensionStatus.APPROVED) {
      repository.saveLock(copyWithExtension(lock, expiration.expiresAt(), expiration.breakdown()));
    }
    audit("NON_QM_LOCK_EXTENSION_" + status.name(), extensionId, extensionPolicy.extensionCode(), decision.outboxEventType());
    return decision;
  }

  public FloatDownDecision requestFloatDown(FloatDownRequest request) {
    String payloadHash = hash(request);
    Object replay = replay(request.tenantId(), request.idempotencyKey(), payloadHash);
    if (replay instanceof FloatDownDecision decision) return decision;

    NonQmLockRecord lock = getLock(request.tenantId(), request.lockId());
    NonQmLockPolicy policy = repository.findPolicy(lock.policyCode()).orElse(null);
    if (policy == null) throw new LockServiceException("LOCK_POLICY_MISSING", "Policy used by lock is not available");
    LockTermOption term = policy.termOptions().stream()
      .filter(candidate -> candidate.termCode().equals(lock.termCode()))
      .findFirst()
      .orElseThrow(() -> new LockServiceException("LOCK_TERM_MISSING", "Lock term is no longer available in policy"));
    FloatDownDecision decision;
    if (!term.floatDownEligible()) {
      decision = new FloatDownDecision(lock.lockId(), FloatDownStatus.INELIGIBLE, term.floatDownRuleRef(), term.minimumImprovementBps(), request.currentPriceImprovementBps(), request.pricingEvidenceRef(), List.of("FLOAT_DOWN_NOT_CONFIGURED"), "AUDIT-NONQM-FLOATDOWN-" + lock.lockId(), "non_qm.float_down_rejected.v1");
    } else if (request.currentPriceImprovementBps().compareTo(term.minimumImprovementBps()) < 0) {
      decision = new FloatDownDecision(lock.lockId(), FloatDownStatus.REJECTED, term.floatDownRuleRef(), term.minimumImprovementBps(), request.currentPriceImprovementBps(), request.pricingEvidenceRef(), List.of("MINIMUM_IMPROVEMENT_NOT_MET"), "AUDIT-NONQM-FLOATDOWN-" + lock.lockId(), "non_qm.float_down_rejected.v1");
    } else {
      decision = new FloatDownDecision(lock.lockId(), FloatDownStatus.APPROVED, term.floatDownRuleRef(), term.minimumImprovementBps(), request.currentPriceImprovementBps(), request.pricingEvidenceRef(), List.of("FLOAT_DOWN_POLICY_SATISFIED"), "AUDIT-NONQM-FLOATDOWN-" + lock.lockId(), "non_qm.float_down_approved.v1");
    }
    repository.saveFloatDown(request.tenantId(), lock.lockId(), decision);
    repository.saveIdempotency(request.tenantId(), request.idempotencyKey(), payloadHash, decision);
    audit("NON_QM_FLOAT_DOWN_" + decision.status().name(), lock.lockId(), decision.ruleRef(), decision.outboxEventType());
    return decision;
  }

  public SecondaryDeliveryPackage secondaryDeliveryPackage(UUID tenantId, String lockId) {
    NonQmLockRecord lock = getLock(tenantId, lockId);
    SecondaryDeliveryProfile profile = lock.deliveryProfile();
    if (!"ACTIVE".equals(profile.status())) {
      throw new LockServiceException("SECONDARY_PROFILE_INACTIVE", "Inactive investor profile blocks delivery package creation");
    }
    Map<String, String> exportFields = new java.util.LinkedHashMap<>(profile.investorCustomFields());
    exportFields.put("lockPolicyCode", lock.policyCode());
    exportFields.put("lockTermDays", String.valueOf(lock.lockPeriodBusinessDays()));
    exportFields.put("pricingWaterfallRef", lock.pricingWaterfallRef());
    exportFields.put("nonQmProductType", lock.productType().name());
    SecondaryDeliveryPackage deliveryPackage = new SecondaryDeliveryPackage(
      lock.lockId(), profile.profileCode(), profile.deliveryType(), profile.guidelineRefs(), profile.poolingAttributes(),
      profile.requiredPreDeliveryConditions(), Map.copyOf(exportFields), "AUDIT-NONQM-SECONDARY-" + lock.lockId(),
      "non_qm.secondary_profile_assigned.v1"
    );
    audit("NON_QM_SECONDARY_PROFILE_ASSIGNED", lock.lockId(), profile.profileCode(), deliveryPackage.outboxEventType());
    return deliveryPackage;
  }

  public LockCommitmentLetter commitmentLetter(UUID tenantId, String lockId) {
    NonQmLockRecord lock = getLock(tenantId, lockId);
    List<String> borrowerVisibleAssumptions = List.of(
      "Lock terms are based on configured investor Non-QM policy " + lock.policyCode(),
      "Extension and float-down terms require configured lock desk approval and pricing evidence",
      "Secondary delivery conditions are subject to investor profile " + lock.deliveryProfile().profileCode()
    );
    return new LockCommitmentLetter(
      lock.lockId(), "LETTER-NONQM-" + lock.lockId(), lock.productType().name(), lock.investorCode(),
      lock.lockPeriodBusinessDays(), lock.expiresAt(), borrowerVisibleAssumptions, lock.deliveryProfile().guidelineRefs(),
      lock.pricingWaterfallRef(), "AUDIT-NONQM-LETTER-" + lock.lockId()
    );
  }

  public List<Map<String, String>> auditTrail() {
    return repository.auditTrail();
  }

  private Optional<NonQmLockPolicy> resolvePolicy(PricingWaterfallSnapshot waterfall, Instant effectiveAt) {
    return repository.policies().stream()
      .filter(policy -> "ACTIVE".equals(policy.status()))
      .filter(policy -> policy.investorCode().equals(waterfall.investorCode()))
      .filter(policy -> policy.channelCode().equals(waterfall.channelCode()))
      .filter(policy -> policy.productType() == waterfall.productType())
      .filter(policy -> !policy.effectiveStart().isAfter(effectiveAt))
      .filter(policy -> policy.effectiveEnd() == null || policy.effectiveEnd().isAfter(effectiveAt))
      .max(Comparator.comparing(NonQmLockPolicy::version));
  }

  private LockTermOption selectTerm(NonQmLockPolicy policy, int requestedTermDays) {
    return policy.termOptions().stream()
      .filter(term -> term.termDays() == requestedTermDays)
      .findFirst()
      .orElseThrow(() -> new LockServiceException("LOCK_TERM_UNAVAILABLE", "Requested Non-QM lock period is not configured for policy"));
  }

  private void validateWaterfallFreshness(NonQmLockRequest request) {
    if (!Objects.equals(request.quoteWaterfallHashAtRequest(), request.pricingWaterfall().auditHash())
      && (request.lockDeskOverrideArtifactRef() == null || request.lockDeskOverrideArtifactRef().isBlank())) {
      throw new LockServiceException("REPRICE_REQUIRED", "Pricing waterfall changed after quote; reprice or provide explicit lock desk override artifact");
    }
  }

  private NonQmExtensionDecision rejectedExtension(NonQmExtensionRequest request, NonQmLockRecord lock, ExtensionPolicy policy, String reasonCode) {
    ExtensionFeeResult fee = new ExtensionFeeResult(policy.feeRuleRef(), policy.priceImpactBps(), lock.pricingWaterfallRef(), "Rejected extension retains configured fee/price rule reference for audit");
    String extensionId = stableId(request.tenantId(), request.requestId(), request.idempotencyKey());
    NonQmExtensionDecision decision = new NonQmExtensionDecision(
      extensionId, lock.lockId(), ExtensionStatus.REJECTED, request.extensionType(), request.requestedExtensionBusinessDays(),
      lock.expiresAt(), lock.expiresAt(), fee, List.of(), List.of(reasonCode), "AUDIT-NONQM-EXT-" + extensionId,
      "non_qm.lock_extension_decisioned.v1"
    );
    repository.saveExtension(decision);
    audit("NON_QM_LOCK_EXTENSION_REJECTED", extensionId, reasonCode, decision.outboxEventType());
    return decision;
  }

  private List<String> missingEvidence(ExtensionPolicy policy, List<String> evidenceRefs) {
    return policy.requiredEvidenceTypes().stream()
      .filter(required -> evidenceRefs.stream().noneMatch(ref -> ref != null && ref.contains(required)))
      .toList();
  }

  private NonQmLockRecord copyWithExtension(
    NonQmLockRecord lock,
    Instant expiresAt,
    BusinessDayCalculator.ExpirationBreakdown breakdown
  ) {
    return new NonQmLockRecord(
      lock.tenantId(), lock.lockId(), lock.requestId(), lock.quoteId(), lock.productType(), lock.investorCode(), lock.channelCode(),
      lock.policyCode(), lock.policyVersion(), lock.termCode(), lock.lockPeriodBusinessDays(), lock.lockedAt(), expiresAt, breakdown,
      lock.status(), lock.extensionCount() + 1, lock.deliveryProfile(), lock.pricingWaterfallRef(), lock.pricingAuditHash(),
      lock.lockPriceAdjustmentBps(), lock.projectContext(), lock.auditRef(), lock.outboxEventType()
    );
  }

  private Object replay(UUID tenantId, String idempotencyKey, String payloadHash) {
    return repository.findIdempotency(tenantId, idempotencyKey, payloadHash).orElse(null);
  }

  private void audit(String action, String aggregateId, String policyOrProfileRef, String eventType) {
    repository.addAudit(Map.of(
      "action", action,
      "aggregateId", aggregateId,
      "policyOrProfileRef", policyOrProfileRef == null ? "" : policyOrProfileRef,
      "eventType", eventType,
      "recordedAt", Instant.now().toString()
    ));
  }

  private static String stableId(UUID tenantId, String requestId, String idempotencyKey) {
    return hash(tenantId + "|" + requestId + "|" + idempotencyKey).substring(0, 24).toUpperCase();
  }

  private static String hash(Object value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
