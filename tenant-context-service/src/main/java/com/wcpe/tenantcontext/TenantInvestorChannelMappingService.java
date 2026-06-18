package com.wcpe.tenantcontext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;

@Service
public class TenantInvestorChannelMappingService {
    private final CopyOnWriteArrayList<TenantInvestorChannelMapping> mappings;

    public TenantInvestorChannelMappingService() {
        this.mappings = new CopyOnWriteArrayList<>();
    }

    public TenantInvestorChannelMappingService(Collection<TenantInvestorChannelMapping> mappings) {
        this.mappings = new CopyOnWriteArrayList<>(mappings == null ? List.of() : mappings.stream().map(TenantInvestorChannelMappingService::validated).toList());
    }

    public List<TenantInvestorChannelMapping> configure(Collection<TenantInvestorChannelMapping> mappings) {
        this.mappings.clear();
        if (mappings != null) {
            this.mappings.addAll(mappings.stream().map(TenantInvestorChannelMappingService::validated).toList());
        }
        return list(null);
    }

    public TenantInvestorChannelMapping save(TenantInvestorChannelMapping mapping) {
        TenantInvestorChannelMapping validated = validated(mapping);
        this.mappings.removeIf(existing -> same(existing.mappingId(), validated.mappingId()));
        this.mappings.add(validated);
        return validated;
    }

    public List<TenantInvestorChannelMapping> list(String tenantId) {
        return mappings.stream()
            .filter(mapping -> !hasText(tenantId) || same(mapping.tenantId(), tenantId))
            .toList();
    }

    public TenantInvestorChannelContext resolve(TenantInvestorChannelMappingRequest request) {
        if (request == null) {
            throw mappingError("TENANT_MAPPING_REQUEST_REQUIRED", "request", "Tenant mapping request is required.");
        }
        Instant asOf = request.asOf() == null ? Instant.now() : request.asOf();
        boolean hasClientId = hasText(request.clientId());
        boolean hasPriceGroupId = hasText(request.priceGroupId());
        if (!hasClientId && !hasPriceGroupId) {
            throw mappingError("TENANT_MAPPING_IDENTITY_REQUIRED", "clientId", "clientId or priceGroupId is required.");
        }

        List<TenantInvestorChannelMapping> matches = mappings.stream()
            .filter(mapping -> mapping.isActiveAt(asOf))
            .filter(mapping -> request.tenantId() == null || same(mapping.tenantId(), request.tenantId()))
            .filter(mapping -> !hasClientId || same(mapping.clientId(), request.clientId()))
            .filter(mapping -> !hasPriceGroupId || same(mapping.priceGroupId(), request.priceGroupId()))
            .filter(mapping -> !hasText(request.originalLoanOfficerId()) || same(mapping.originalLoanOfficerId(), request.originalLoanOfficerId()))
            .filter(mapping -> !hasText(request.investorCode()) || same(mapping.investorCode(), request.investorCode()))
            .filter(mapping -> !hasText(request.channelCode()) || same(mapping.channelCode(), request.channelCode()))
            .toList();

        if (matches.isEmpty()) {
            throw mappingError("TENANT_MAPPING_NOT_FOUND", hasClientId ? "clientId" : "priceGroupId", "No active tenant mapping matched the submitted identity for the requested as-of timestamp.");
        }

        List<TenantInvestorChannelMapping> distinctContexts = matches.stream()
            .filter(mapping -> hasText(mapping.tenantId()) && hasText(mapping.investorCode()) && hasText(mapping.channelCode()) && hasText(mapping.auditRef()))
            .distinct()
            .toList();
        if (distinctContexts.isEmpty()) {
            throw mappingError("TENANT_MAPPING_INCOMPLETE", "tenantMapping", "Tenant mapping must include tenantId, investorCode, channelCode, and auditRef.");
        }
        if (distinctContexts.size() > 1) {
            throw mappingError("TENANT_MAPPING_AMBIGUOUS", hasClientId ? "clientId" : "priceGroupId", "Multiple active tenant mappings matched the submitted identity and as-of timestamp.");
        }

        TenantInvestorChannelMapping mapping = distinctContexts.get(0);
        return new TenantInvestorChannelContext(
            mapping.tenantId().trim(),
            mapping.channelCode().trim(),
            mapping.investorCode().trim(),
            mapping.mappingId(),
            mapping.auditRef().trim(),
            asOf,
            compactRefs(mapping)
        );
    }

    private static Map<String, String> compactRefs(TenantInvestorChannelMapping mapping) {
        Map<String, String> refs = new LinkedHashMap<>();
        putIfPresent(refs, "clientId", mapping.clientId());
        putIfPresent(refs, "priceGroupId", mapping.priceGroupId());
        putIfPresent(refs, "originalLoanOfficerId", mapping.originalLoanOfficerId());
        return Map.copyOf(refs);
    }

    private static void putIfPresent(Map<String, String> refs, String key, String value) {
        if (hasText(value)) {
            refs.put(key, value.trim());
        }
    }

    private static TenantMappingException mappingError(String code, String field, String message) {
        return new TenantMappingException(code, List.of(new FieldError(field, code, message)));
    }

    private static TenantInvestorChannelMapping validated(TenantInvestorChannelMapping mapping) {
        if (mapping == null) {
            throw mappingError("TENANT_MAPPING_REQUIRED", "tenantMapping", "Tenant mapping is required.");
        }
        if (!hasText(mapping.mappingId())) {
            throw mappingError("TENANT_MAPPING_ID_REQUIRED", "mappingId", "mappingId is required.");
        }
        if (!hasText(mapping.tenantId())) {
            throw mappingError("TENANT_MAPPING_TENANT_REQUIRED", "tenantId", "tenantId is required.");
        }
        if (!hasText(mapping.clientId()) && !hasText(mapping.priceGroupId())) {
            throw mappingError("TENANT_MAPPING_IDENTITY_REQUIRED", "clientId", "clientId or priceGroupId is required.");
        }
        if (!hasText(mapping.investorCode())) {
            throw mappingError("TENANT_MAPPING_INVESTOR_REQUIRED", "investorCode", "investorCode is required.");
        }
        if (!hasText(mapping.channelCode())) {
            throw mappingError("TENANT_MAPPING_CHANNEL_REQUIRED", "channelCode", "channelCode is required.");
        }
        if (!hasText(mapping.auditRef())) {
            throw mappingError("TENANT_MAPPING_AUDIT_REF_REQUIRED", "auditRef", "auditRef is required.");
        }
        if (mapping.effectiveStart() != null && mapping.effectiveEnd() != null && !mapping.effectiveEnd().isAfter(mapping.effectiveStart())) {
            throw mappingError("TENANT_MAPPING_EFFECTIVE_WINDOW_INVALID", "effectiveEnd", "effectiveEnd must be after effectiveStart.");
        }
        return mapping;
    }

    private static boolean same(String left, String right) {
        return normalize(left).equals(normalize(right));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record TenantInvestorChannelMappingRequest(
        String tenantId,
        String clientId,
        String originalLoanOfficerId,
        String priceGroupId,
        String investorCode,
        String channelCode,
        Instant asOf
    ) { }

    public record TenantInvestorChannelMapping(
        String mappingId,
        String tenantId,
        String clientId,
        String originalLoanOfficerId,
        String priceGroupId,
        String investorCode,
        String channelCode,
        Instant effectiveStart,
        Instant effectiveEnd,
        String auditRef,
        String status
    ) {
        boolean isActiveAt(Instant asOf) {
            Instant effectiveAsOf = asOf == null ? Instant.now() : asOf;
            boolean startsBefore = effectiveStart == null || !effectiveStart.isAfter(effectiveAsOf);
            boolean endsAfter = effectiveEnd == null || effectiveEnd.isAfter(effectiveAsOf);
            boolean active = status == null || status.isBlank() || "ACTIVE".equalsIgnoreCase(status.trim());
            return startsBefore && endsAfter && active;
        }
    }

    public record TenantInvestorChannelContext(
        String tenantId,
        String channelCode,
        String investorCode,
        String mappingId,
        String auditRef,
        Instant asOf,
        Map<String, String> sourceRefs
    ) { }

    public record FieldError(String field, String code, String message) { }

    public static class TenantMappingException extends RuntimeException {
        private final String code;
        private final List<FieldError> fieldErrors;

        TenantMappingException(String code, List<FieldError> fieldErrors) {
            super(code);
            this.code = code;
            this.fieldErrors = List.copyOf(Optional.ofNullable(fieldErrors).orElseGet(ArrayList::new));
        }

        public String code() {
            return code;
        }

        public List<FieldError> fieldErrors() {
            return fieldErrors;
        }
    }
}
