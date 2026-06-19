package com.wcpe.adjustment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Tenant-scoped fee catalog model for PII-06-S06.
 *
 * <p>The catalog stores fee definitions and configuration references only. It
 * does not embed tenant fee amounts, pricing rates, investor policy, or
 * jurisdiction-specific compliance values. Amount calculation is owned by the
 * downstream fee calculation slice.</p>
 */
public record FeeCatalogVersion(
    UUID tenantId,
    UUID catalogVersionId,
    int version,
    CatalogStatus status,
    EffectiveWindow effectiveWindow,
    List<FeeDefinition> definitions,
    String createdBy,
    String approvedBy,
    Instant publishedAt,
    String contentHash
) {
    public static final String EVENT_TOPIC = "pricing.fees.catalog.v1";
    public static final int EVENT_VERSION = 1;
    public static final String EVENT_SCHEMA_VERSION = "pricing.fees.catalog.v1";
    public static final String SOURCE_SERVICE = "adjustment-service";
    public static final Set<String> SUPPORTED_EVENT_TYPES = Set.of(
        "FeeCatalogDrafted",
        "FeeCatalogPublished",
        "FeeCatalogSuspended",
        "FeeCatalogRolledBack"
    );

    public FeeCatalogVersion {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(catalogVersionId, "catalogVersionId is required");
        if (version <= 0) {
            throw new IllegalArgumentException("catalog version must be positive");
        }
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(effectiveWindow, "effectiveWindow is required");
        definitions = List.copyOf(definitions == null ? List.of() : definitions);
        requireText(createdBy, "createdBy is required");
        if (status == CatalogStatus.PUBLISHED) {
            requireText(approvedBy, "published catalog requires approvedBy");
            Objects.requireNonNull(publishedAt, "published catalog requires publishedAt");
        }
        contentHash = contentHash == null || contentHash.isBlank()
            ? hashOf(tenantId, catalogVersionId, version, status, effectiveWindow, definitions)
            : contentHash;
    }

    public List<String> validateDefinitions() {
        List<String> errors = new ArrayList<>();
        if (definitions.isEmpty()) {
            errors.add("at least one fee definition is required");
        }
        Set<String> feeCodes = new LinkedHashSet<>();
        for (FeeDefinition definition : definitions) {
            if (!feeCodes.add(definition.feeCode())) {
                errors.add("duplicate fee code in catalog version: " + definition.feeCode());
            }
            errors.addAll(definition.validate());
        }
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }
        return errors;
    }

    public FeeCatalogVersion replaceDraftDefinitions(List<FeeDefinition> replacementDefinitions) {
        if (status != CatalogStatus.DRAFT) {
            throw new IllegalStateException("published, suspended, rolled-back, and approval-pending fee catalogs are immutable");
        }
        return new FeeCatalogVersion(
            tenantId,
            catalogVersionId,
            version,
            status,
            effectiveWindow,
            replacementDefinitions,
            createdBy,
            approvedBy,
            publishedAt,
            null
        );
    }

    public FeeCatalogVersion publish(String approver, Instant when, List<FeeCatalogVersion> existingCatalogs) {
        if (status != CatalogStatus.DRAFT && status != CatalogStatus.VALIDATED && status != CatalogStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("only draft, validated, or approval-pending fee catalogs can be published");
        }
        requireText(approver, "approver is required");
        if (approver.equals(createdBy)) {
            throw new IllegalArgumentException("requester cannot publish the same fee catalog");
        }
        validateDefinitions();
        FeeCatalogVersion published = new FeeCatalogVersion(
            tenantId,
            catalogVersionId,
            version,
            CatalogStatus.PUBLISHED,
            effectiveWindow,
            definitions,
            createdBy,
            approver,
            Objects.requireNonNull(when, "publishedAt is required"),
            null
        );
        assertNoPublishedOverlap(published, existingCatalogs == null ? List.of() : existingCatalogs);
        return published;
    }

    public FeeCatalogVersion suspend(String actor, Instant when) {
        if (status != CatalogStatus.PUBLISHED) {
            throw new IllegalStateException("only published fee catalogs can be suspended");
        }
        requireText(actor, "actor is required");
        Objects.requireNonNull(when, "suspendedAt is required");
        return new FeeCatalogVersion(tenantId, catalogVersionId, version, CatalogStatus.SUSPENDED, effectiveWindow,
            definitions, createdBy, approvedBy, publishedAt, null);
    }

    public FeeCatalogVersion rollback(String actor, Instant when) {
        if (status != CatalogStatus.PUBLISHED && status != CatalogStatus.SUSPENDED) {
            throw new IllegalStateException("only published or suspended fee catalogs can be rolled back");
        }
        requireText(actor, "actor is required");
        Objects.requireNonNull(when, "rolledBackAt is required");
        return new FeeCatalogVersion(tenantId, catalogVersionId, version, CatalogStatus.ROLLED_BACK, effectiveWindow,
            definitions, createdBy, approvedBy, publishedAt, null);
    }

    public FeeDefinition resolve(String feeCode, FeeCatalogRequestContext context) {
        requireText(feeCode, "feeCode is required");
        Objects.requireNonNull(context, "context is required");
        if (!tenantId.equals(context.tenantId())) {
            throw new IllegalArgumentException("fee catalog tenant mismatch");
        }
        if (status != CatalogStatus.PUBLISHED || !effectiveWindow.contains(context.quoteDate())) {
            throw new IllegalStateException("no published fee catalog resolves for context");
        }
        return definitions.stream()
            .filter(FeeDefinition::enabled)
            .filter(definition -> definition.feeCode().equals(feeCode))
            .filter(definition -> definition.applicability().matches(context))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("no fee definition resolves for fee code and context"));
    }

    public FeeCatalogEvent event(
        String eventType,
        String actorId,
        String correlationId,
        String causationId,
        String idempotencyKey,
        UUID eventId,
        Instant occurredAt,
        String snapshotUri
    ) {
        if (!SUPPORTED_EVENT_TYPES.contains(eventType)) {
            throw new IllegalArgumentException("unsupported fee catalog event type: " + eventType);
        }
        requireText(actorId, "actorId is required");
        requireText(correlationId, "correlationId is required");
        requireText(causationId, "causationId is required");
        requireText(idempotencyKey, "idempotencyKey is required");
        Objects.requireNonNull(eventId, "eventId is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        requireText(snapshotUri, "snapshotUri is required");
        List<String> feeCodes = definitions.stream().map(FeeDefinition::feeCode).sorted().toList();
        return new FeeCatalogEvent(
            EVENT_TOPIC,
            eventType,
            EVENT_VERSION,
            EVENT_SCHEMA_VERSION,
            eventId,
            SOURCE_SERVICE,
            occurredAt,
            tenantId + ":" + catalogVersionId + ":" + version,
            tenantId,
            catalogVersionId,
            version,
            status.name(),
            effectiveWindow,
            actorId,
            correlationId,
            causationId,
            idempotencyKey,
            snapshotUri,
            contentHash,
            feeCodes,
            hashOf(feeCodes)
        );
    }

    public FeeCatalogAudit audit(String action, String actorId, String correlationId, String beforeHash) {
        requireText(action, "audit action is required");
        requireText(actorId, "actorId is required");
        requireText(correlationId, "correlationId is required");
        return new FeeCatalogAudit(tenantId, catalogVersionId, action, actorId, correlationId, beforeHash, contentHash);
    }

    public static FeeCatalogVersion resolvePublished(
        UUID tenantId,
        FeeCatalogRequestContext context,
        List<FeeCatalogVersion> catalogs
    ) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(context, "context is required");
        return catalogs.stream()
            .filter(catalog -> catalog.tenantId.equals(tenantId))
            .filter(catalog -> catalog.status == CatalogStatus.PUBLISHED)
            .filter(catalog -> catalog.effectiveWindow.contains(context.quoteDate()))
            .max(Comparator.comparing(FeeCatalogVersion::publishedAt))
            .orElseThrow(() -> new IllegalStateException("no published fee catalog resolves for tenant and quote date"));
    }

    private static void assertNoPublishedOverlap(FeeCatalogVersion candidate, List<FeeCatalogVersion> existingCatalogs) {
        for (FeeCatalogVersion existing : existingCatalogs) {
            if (existing.status == CatalogStatus.PUBLISHED
                && existing.tenantId.equals(candidate.tenantId)
                && existing.effectiveWindow.overlaps(candidate.effectiveWindow)) {
                throw new IllegalArgumentException("published fee catalog effective window overlaps existing catalog");
            }
        }
    }

    static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String hashOf(Object... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Object value : values) {
                digest.update(String.valueOf(value).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public enum CatalogStatus {
        DRAFT,
        VALIDATED,
        PENDING_APPROVAL,
        PUBLISHED,
        SUSPENDED,
        ROLLED_BACK
    }

    public enum CalculationMethod {
        FIXED_AMOUNT,
        PERCENT_OF_LOAN_AMOUNT,
        BPS_OF_LOAN_AMOUNT,
        PER_UNIT,
        PASS_THROUGH,
        MANUAL_INPUT_ALLOWED,
        WAIVED,
        FORMULA_EXPRESSION_APPROVED
    }

    public record EffectiveWindow(Instant start, Instant end) {
        public EffectiveWindow {
            Objects.requireNonNull(start, "effective start is required");
            if (end != null && !end.isAfter(start)) {
                throw new IllegalArgumentException("effective end must be after start");
            }
        }

        boolean contains(Instant instant) {
            Objects.requireNonNull(instant, "quoteDate is required");
            return !instant.isBefore(start) && (end == null || instant.isBefore(end));
        }

        boolean overlaps(EffectiveWindow other) {
            Instant thisEnd = end == null ? Instant.MAX : end;
            Instant otherEnd = other.end == null ? Instant.MAX : other.end;
            return start.isBefore(otherEnd) && other.start.isBefore(thisEnd);
        }
    }

    public record FeeCatalogRequestContext(
        UUID tenantId,
        String productId,
        String investorId,
        String channel,
        String jurisdiction,
        Instant quoteDate
    ) {
        public FeeCatalogRequestContext {
            Objects.requireNonNull(tenantId, "tenantId is required");
            requireText(productId, "productId is required");
            requireText(investorId, "investorId is required");
            requireText(channel, "channel is required");
            requireText(jurisdiction, "jurisdiction is required");
            Objects.requireNonNull(quoteDate, "quoteDate is required");
        }
    }

    public record FeeApplicability(
        List<String> allowedProducts,
        List<String> investors,
        List<String> channels,
        List<String> jurisdictions
    ) {
        public FeeApplicability {
            allowedProducts = copyConfiguredValues(allowedProducts);
            investors = copyConfiguredValues(investors);
            channels = copyConfiguredValues(channels);
            jurisdictions = copyConfiguredValues(jurisdictions);
        }

        List<String> validate() {
            if (allowedProducts.isEmpty() && investors.isEmpty() && channels.isEmpty() && jurisdictions.isEmpty()) {
                return List.of("active fee definition requires at least one applicability selector");
            }
            return List.of();
        }

        boolean matches(FeeCatalogRequestContext context) {
            return matches(allowedProducts, context.productId())
                && matches(investors, context.investorId())
                && matches(channels, context.channel())
                && matches(jurisdictions, context.jurisdiction());
        }

        private static boolean matches(List<String> configuredValues, String candidate) {
            return configuredValues.isEmpty() || configuredValues.contains(candidate);
        }
    }

    public record FeePrecisionPolicy(int moneyScale, int rateScale, int precision, RoundingMode roundingMode) {
        public FeePrecisionPolicy {
            if (moneyScale < 0 || rateScale < 0 || precision <= 0) {
                throw new IllegalArgumentException("precision values must be positive or zero scale");
            }
            roundingMode = roundingMode == null ? RoundingMode.HALF_UP : roundingMode;
        }

        public static FeePrecisionPolicy defaultPolicy() {
            return new FeePrecisionPolicy(2, 6, 18, RoundingMode.HALF_UP);
        }

        public BigDecimal normalizeMoney(BigDecimal amount) {
            return normalize(amount, moneyScale);
        }

        public BigDecimal normalizeRate(BigDecimal rate) {
            return normalize(rate, rateScale);
        }

        private BigDecimal normalize(BigDecimal value, int scale) {
            Objects.requireNonNull(value, "configured numeric value is required");
            BigDecimal normalized = value.setScale(scale, roundingMode);
            if (normalized.precision() > precision) {
                throw new IllegalArgumentException("configured numeric value exceeds precision policy");
            }
            return normalized;
        }
    }

    public record FeeDefinition(
        UUID feeDefinitionId,
        String feeCode,
        String displayName,
        String description,
        String category,
        String payer,
        String payee,
        boolean financeCharge,
        boolean aprIncluded,
        String toleranceBucket,
        String disclosureSection,
        CalculationMethod calculationMethod,
        Map<String, String> formulaParameters,
        FeeApplicability applicability,
        String reasonCode,
        String sourceRef,
        boolean enabled,
        String contentHash
    ) {
        public FeeDefinition {
            Objects.requireNonNull(feeDefinitionId, "feeDefinitionId is required");
            requireText(feeCode, "feeCode is required");
            requireText(displayName, "displayName is required");
            Objects.requireNonNull(calculationMethod, "calculationMethod is required");
            formulaParameters = Map.copyOf(new TreeMap<>(formulaParameters == null ? Map.of() : formulaParameters));
            applicability = applicability == null ? new FeeApplicability(List.of(), List.of(), List.of(), List.of()) : applicability;
            requireText(sourceRef, "sourceRef is required");
            contentHash = contentHash == null || contentHash.isBlank()
                ? hashOf(feeDefinitionId, feeCode, displayName, category, toleranceBucket, disclosureSection,
                    calculationMethod, formulaParameters, applicability, reasonCode, sourceRef, enabled)
                : contentHash;
        }

        List<String> validate() {
            List<String> errors = new ArrayList<>();
            if (!enabled) {
                return errors;
            }
            requireConfiguredText(errors, category, "active fee definition requires category");
            requireConfiguredText(errors, reasonCode, "active fee definition requires reasonCode");
            requireConfiguredText(errors, toleranceBucket, "active fee definition requires toleranceBucket");
            requireConfiguredText(errors, disclosureSection, "active fee definition requires disclosureSection");
            errors.addAll(applicability.validate());
            errors.addAll(validateFormulaParameters());
            return errors;
        }

        private List<String> validateFormulaParameters() {
            return switch (calculationMethod) {
                case FIXED_AMOUNT -> requireAnyParameter("amountConfigRef", "amountCalculationOutputRef");
                case PERCENT_OF_LOAN_AMOUNT -> requireAnyParameter("percentConfigRef", "percentCalculationOutputRef");
                case BPS_OF_LOAN_AMOUNT -> requireAnyParameter("bpsConfigRef", "bpsCalculationOutputRef");
                case PER_UNIT -> requireAnyParameter("unitAmountConfigRef", "unitCalculationOutputRef");
                case PASS_THROUGH -> requireParameter("trustedSourceRef");
                case MANUAL_INPUT_ALLOWED -> requireParameters("manualInputPermissionRef", "auditReasonRequired");
                case WAIVED -> requireParameter("waiverPolicyRef");
                case FORMULA_EXPRESSION_APPROVED -> validateApprovedFormulaReference();
            };
        }

        private List<String> validateApprovedFormulaReference() {
            List<String> errors = new ArrayList<>(requireParameters(
                "approvedExpressionRef",
                "approvedVariablesRef",
                "approvedFunctionsRef"
            ));
            if (formulaParameters.containsKey("expression")) {
                errors.add("formula expressions must reference an approved expression catalog entry");
            }
            return errors;
        }

        private List<String> requireParameter(String key) {
            return requireParameters(key);
        }

        private List<String> requireAnyParameter(String primaryKey, String alternateKey) {
            String primary = formulaParameters.get(primaryKey);
            String alternate = formulaParameters.get(alternateKey);
            if ((primary == null || primary.isBlank()) && (alternate == null || alternate.isBlank())) {
                return List.of(calculationMethod + " requires formula parameter " + primaryKey + " or " + alternateKey);
            }
            return List.of();
        }

        private List<String> requireParameters(String... keys) {
            List<String> errors = new ArrayList<>();
            for (String key : keys) {
                String value = formulaParameters.get(key);
                if (value == null || value.isBlank()) {
                    errors.add(calculationMethod + " requires formula parameter " + key);
                }
            }
            return errors;
        }

        private static void requireConfiguredText(List<String> errors, String value, String message) {
            if (value == null || value.isBlank()) {
                errors.add(message);
            }
        }
    }

    public record FeeCatalogEvent(
        String topic,
        String eventType,
        int eventVersion,
        String schemaVersion,
        UUID eventId,
        String sourceService,
        Instant occurredAt,
        String key,
        UUID tenantId,
        UUID catalogVersionId,
        int version,
        String status,
        EffectiveWindow effectiveWindow,
        String actorId,
        String correlationId,
        String causationId,
        String idempotencyKey,
        String snapshotUri,
        String snapshotHash,
        List<String> feeCodes,
        String feeCodeListHash
    ) {
        public FeeCatalogEvent {
            requireText(topic, "topic is required");
            requireText(eventType, "eventType is required");
            requireText(schemaVersion, "schemaVersion is required");
            Objects.requireNonNull(eventId, "eventId is required");
            requireText(sourceService, "sourceService is required");
            Objects.requireNonNull(occurredAt, "occurredAt is required");
            requireText(key, "key is required");
            Objects.requireNonNull(tenantId, "tenantId is required");
            Objects.requireNonNull(catalogVersionId, "catalogVersionId is required");
            requireText(status, "status is required");
            Objects.requireNonNull(effectiveWindow, "effectiveWindow is required");
            requireText(actorId, "actorId is required");
            requireText(correlationId, "correlationId is required");
            requireText(causationId, "causationId is required");
            requireText(idempotencyKey, "idempotencyKey is required");
            requireText(snapshotUri, "snapshotUri is required");
            requireText(snapshotHash, "snapshotHash is required");
            feeCodes = List.copyOf(feeCodes == null ? List.of() : feeCodes);
            requireText(feeCodeListHash, "feeCodeListHash is required");
        }
    }

    public record FeeCatalogAudit(
        UUID tenantId,
        UUID catalogVersionId,
        String action,
        String actorId,
        String correlationId,
        String beforeHash,
        String afterHash
    ) {
        public FeeCatalogAudit {
            Objects.requireNonNull(tenantId, "tenantId is required");
            Objects.requireNonNull(catalogVersionId, "catalogVersionId is required");
            requireText(action, "action is required");
            requireText(actorId, "actorId is required");
            requireText(correlationId, "correlationId is required");
            requireText(afterHash, "afterHash is required");
        }
    }

    private static List<String> copyConfiguredValues(List<String> values) {
        return List.copyOf(values == null ? List.of() : values);
    }
}
