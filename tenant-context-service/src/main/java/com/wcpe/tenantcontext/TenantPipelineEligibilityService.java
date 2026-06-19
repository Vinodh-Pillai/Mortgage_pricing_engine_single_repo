package com.wcpe.tenantcontext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class TenantPipelineEligibilityService {
    private static final String METADATA_READ_SCOPE = TenantAccessPolicy.DEFAULT_CONTEXT_READ_SCOPE;

    private final TenantFieldConfigurationStoreService fieldConfigurationStore;
    private final Map<String, TenantPipelineConfiguration> configurationsByTenant = new ConcurrentHashMap<>();
    private final Map<String, UserTenantAssignment> userAssignments = new ConcurrentHashMap<>();
    private final List<TenantPipelineAccessAuditRecord> accessAuditRecords = Collections.synchronizedList(new ArrayList<>());

    public TenantPipelineEligibilityService(TenantFieldConfigurationStoreService fieldConfigurationStore) {
        this.fieldConfigurationStore = Objects.requireNonNull(fieldConfigurationStore, "fieldConfigurationStore is required");
    }

    public TenantPipelineConfiguration configureTenant(TenantPipelineConfiguration command) {
        if (command == null) {
            throw pipelineError("TENANT_PIPELINE_CONFIGURATION_REQUIRED", "configuration", "Tenant pipeline configuration is required.");
        }
        String tenantId = required(command.tenantId(), "tenantId");
        List<TenantProductOption> products = Optional.ofNullable(command.products()).orElseGet(List::of).stream()
            .map(product -> validatedProduct(tenantId, product))
            .sorted(Comparator.comparing(TenantProductOption::productId))
            .toList();
        List<TenantInvestorOption> investors = Optional.ofNullable(command.investors()).orElseGet(List::of).stream()
            .map(investor -> validatedInvestor(tenantId, investor))
            .sorted(Comparator.comparing(TenantInvestorOption::investorId))
            .toList();
        Map<String, String> companySettings = normalizedSettings(command.companySettings(), "companySettings");
        List<TenantUserSettings> userSettings = Optional.ofNullable(command.userSettings()).orElseGet(List::of).stream()
            .map(settings -> validatedUserSettings(tenantId, settings))
            .sorted(Comparator.comparing(TenantUserSettings::userId))
            .toList();

        TenantPipelineConfiguration validated = new TenantPipelineConfiguration(tenantId, products, investors, companySettings, userSettings);
        configurationsByTenant.put(key(tenantId), validated);
        userSettings.forEach(settings -> assignUser(tenantId, settings.userId()));
        return validated;
    }

    public UserTenantAssignment assignUser(String tenantId, String userId) {
        UserTenantAssignment assignment = new UserTenantAssignment(required(userId, "userId"), required(tenantId, "tenantId"));
        UserTenantAssignment existing = userAssignments.putIfAbsent(key(assignment.userId()), assignment);
        if (existing != null && !same(existing.tenantId(), assignment.tenantId())) {
            throw pipelineError("TENANT_PIPELINE_USER_TENANT_MISMATCH", "userId", "User is already assigned to a different tenant.");
        }
        return userAssignments.get(key(assignment.userId()));
    }

    public TenantPipelineEligibility eligibleForUser(TenantPipelineEligibilityRequest request) {
        throw new TenantContextValidationException("TENANT_CONTEXT_MISSING", "tenant context is required for tenant metadata queries");
    }

    public TenantPipelineEligibility eligibleForUser(TenantContext tenantContext, TenantPipelineEligibilityRequest request) {
        if (request == null) {
            throw pipelineError("TENANT_PIPELINE_REQUEST_REQUIRED", "request", "Tenant pipeline eligibility request is required.");
        }
        requireTenantMetadataContext(tenantContext, request);
        String tenantId = required(request.tenantId(), "tenantId");
        String userId = required(request.userId(), "userId");
        String actorTenantId = required(request.actorTenantId(), "actorTenantId");
        if (!same(tenantId, actorTenantId)) {
            throwDeniedWithAudit(tenantContext, request, "TENANT_PIPELINE_ACCESS_DENIED", "tenantId", "Actor tenant cannot query another tenant's pipeline configuration.", "tenant", actorTenantId);
        }
        UserTenantAssignment assignment = Optional.ofNullable(userAssignments.get(key(userId)))
            .orElseThrow(() -> pipelineError("TENANT_PIPELINE_USER_NOT_ASSIGNED", "userId", "User must be assigned to the tenant before querying pipeline configuration."));
        if (!same(assignment.tenantId(), tenantId)) {
            throwDeniedWithAudit(tenantContext, request, "TENANT_PIPELINE_ACCESS_DENIED", "userId", "User cannot query another tenant's products or investors.", "user", userId);
        }

        TenantPipelineConfiguration configuration = Optional.ofNullable(configurationsByTenant.get(key(tenantId)))
            .orElse(new TenantPipelineConfiguration(tenantId, List.of(), List.of(), Map.of(), List.of()));
        List<TenantProductOption> activeProducts = configuration.products().stream().filter(TenantProductOption::enabled).toList();
        List<TenantInvestorOption> activeInvestors = configuration.investors().stream().filter(TenantInvestorOption::enabled).toList();

        if (hasText(request.requestedProductId()) && activeProducts.stream().noneMatch(product -> same(product.productId(), request.requestedProductId()))) {
            throwDeniedWithAudit(tenantContext, request, "TENANT_PIPELINE_PRODUCT_DENIED", "productId", "Product is not authorized for this tenant.", "product", request.requestedProductId());
        }
        if (hasText(request.requestedInvestorId()) && activeInvestors.stream().noneMatch(investor -> same(investor.investorId(), request.requestedInvestorId()))) {
            throwDeniedWithAudit(tenantContext, request, "TENANT_PIPELINE_INVESTOR_DENIED", "investorId", "Investor is not authorized for this tenant.", "investor", request.requestedInvestorId());
        }

        Map<String, String> userSettings = configuration.userSettings().stream()
            .filter(settings -> same(settings.userId(), userId))
            .findFirst()
            .map(TenantUserSettings::settings)
            .orElse(Map.of());
        TenantPipelineEligibility eligibility = new TenantPipelineEligibility(
            tenantId,
            userId,
            activeProducts.stream().map(TenantProductOption::productId).toList(),
            activeInvestors.stream().map(TenantInvestorOption::investorId).toList(),
            configuration.companySettings(),
            userSettings
        );
        recordMetadataEvaluation(tenantContext, request);
        return eligibility;
    }

    public List<TenantPipelineAccessAuditRecord> accessAuditRecordsForTenant(String tenantId) {
        String normalizedTenantId = required(tenantId, "tenantId");
        synchronized (accessAuditRecords) {
            return accessAuditRecords.stream()
                .filter(record -> same(record.tenantId(), normalizedTenantId))
                .toList();
        }
    }

    private void requireTenantMetadataContext(TenantContext tenantContext, TenantPipelineEligibilityRequest request) {
        if (tenantContext == null) {
            throw new TenantContextValidationException("TENANT_CONTEXT_MISSING", "tenant context is required for tenant metadata queries");
        }
        String requestTenantId = required(request.tenantId(), "tenantId");
        if (!same(tenantContext.tenantId(), requestTenantId)) {
            recordDeniedAccess(tenantContext, request, "TENANT_ACCESS_DENIED", "tenant", requestTenantId);
            throw new TenantContextValidationException("TENANT_ACCESS_DENIED", "tenant context does not match requested tenant metadata");
        }
        if (tenantContext.scopes() == null || !tenantContext.scopes().contains(METADATA_READ_SCOPE)) {
            recordDeniedAccess(tenantContext, request, "TENANT_ACCESS_DENIED", "scope", METADATA_READ_SCOPE);
            throw new TenantContextValidationException("TENANT_ACCESS_DENIED", "tenant metadata read scope is required");
        }
        if (tenantContext.actor() == null || !hasText(tenantContext.actor().actorId()) || !hasText(tenantContext.actor().actorType())) {
            recordDeniedAccess(tenantContext, request, "TENANT_CONTEXT_MISSING", "actor", "");
            throw new TenantContextValidationException("TENANT_CONTEXT_MISSING", "actor context is required for tenant metadata queries");
        }
    }

    private void throwDeniedWithAudit(TenantContext tenantContext, TenantPipelineEligibilityRequest request, String code, String field, String message, String entityType, String entityId) {
        recordDeniedAccess(tenantContext, request, code, entityType, entityId);
        throw pipelineError(code, field, message);
    }

    private void recordDeniedAccess(TenantContext tenantContext, TenantPipelineEligibilityRequest request, String code, String entityType, String entityId) {
        String tenantId = request == null || !hasText(request.tenantId())
            ? tenantContext == null ? "UNKNOWN" : tenantContext.tenantId()
            : request.tenantId().trim();
        String userId = request == null || !hasText(request.userId()) ? "" : request.userId().trim();
        String actorId = tenantContext == null || tenantContext.actor() == null ? "" : optional(tenantContext.actor().actorId());
        String actorType = tenantContext == null || tenantContext.actor() == null ? "" : optional(tenantContext.actor().actorType());
        accessAuditRecords.add(new TenantPipelineAccessAuditRecord(tenantId, userId, actorId, actorType, code, optional(entityType), optional(entityId)));
    }

    private void recordMetadataEvaluation(TenantContext tenantContext, TenantPipelineEligibilityRequest request) {
        accessAuditRecords.add(new TenantPipelineAccessAuditRecord(
            required(request.tenantId(), "tenantId"),
            required(request.userId(), "userId"),
            optional(tenantContext.actor().actorId()),
            optional(tenantContext.actor().actorType()),
            "TENANT_PIPELINE_METADATA_EVALUATED",
            "tenant",
            required(request.tenantId(), "tenantId")
        ));
    }

    private TenantProductOption validatedProduct(String tenantId, TenantProductOption product) {
        if (product == null) {
            throw pipelineError("TENANT_PIPELINE_PRODUCT_REQUIRED", "products", "Tenant product option is required.");
        }
        List<FieldReference> fieldRefs = validatedFieldRefs(tenantId, product.fieldRefs(), "products.fieldRefs");
        return new TenantProductOption(required(product.productId(), "productId"), optional(product.displayName()), fieldRefs, product.enabled());
    }

    private TenantInvestorOption validatedInvestor(String tenantId, TenantInvestorOption investor) {
        if (investor == null) {
            throw pipelineError("TENANT_PIPELINE_INVESTOR_REQUIRED", "investors", "Tenant investor option is required.");
        }
        List<FieldReference> fieldRefs = validatedFieldRefs(tenantId, investor.fieldRefs(), "investors.fieldRefs");
        return new TenantInvestorOption(required(investor.investorId(), "investorId"), optional(investor.displayName()), fieldRefs, investor.enabled());
    }

    private TenantUserSettings validatedUserSettings(String tenantId, TenantUserSettings settings) {
        if (settings == null) {
            throw pipelineError("TENANT_PIPELINE_USER_SETTINGS_REQUIRED", "userSettings", "Tenant user settings are required.");
        }
        return new TenantUserSettings(required(settings.userId(), "userId"), tenantId, normalizedSettings(settings.settings(), "userSettings"));
    }

    private List<FieldReference> validatedFieldRefs(String tenantId, Collection<FieldReference> refs, String fieldName) {
        List<FieldReference> validated = new ArrayList<>();
        for (FieldReference ref : Optional.ofNullable(refs).orElseGet(List::of)) {
            if (ref == null) {
                throw pipelineError("TENANT_PIPELINE_FIELD_REFERENCE_REQUIRED", fieldName, "Field reference is required.");
            }
            FieldReference normalized = new FieldReference(required(ref.surface(), fieldName + ".surface"), required(ref.fieldId(), fieldName + ".fieldId"));
            if (fieldConfigurationStore.activeField(tenantId, normalized.surface(), normalized.fieldId()).isEmpty()) {
                throw pipelineError("TENANT_PIPELINE_FIELD_REFERENCE_MISSING", fieldName, "Referenced field must exist in the tenant's active field-library version.");
            }
            validated.add(normalized);
        }
        return List.copyOf(validated);
    }

    private static Map<String, String> normalizedSettings(Map<String, String> settings, String fieldName) {
        if (settings == null || settings.isEmpty()) {
            return Map.of();
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        settings.forEach((key, value) -> normalized.put(required(key, fieldName + ".key"), optional(value)));
        return Map.copyOf(normalized);
    }

    private static TenantPipelineException pipelineError(String code, String field, String message) {
        return new TenantPipelineException(code, List.of(new FieldError(field, code, message)));
    }

    private static String required(String value, String fieldName) {
        if (!hasText(value)) {
            throw pipelineError("TENANT_PIPELINE_VALIDATION_FAILED", fieldName, fieldName + " is required.");
        }
        return value.trim();
    }

    private static String optional(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean same(String left, String right) {
        return key(left).equals(key(right));
    }

    private static String key(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record TenantPipelineConfiguration(
        String tenantId,
        List<TenantProductOption> products,
        List<TenantInvestorOption> investors,
        Map<String, String> companySettings,
        List<TenantUserSettings> userSettings
    ) {
        public TenantPipelineConfiguration {
            products = List.copyOf(Optional.ofNullable(products).orElseGet(List::of));
            investors = List.copyOf(Optional.ofNullable(investors).orElseGet(List::of));
            companySettings = Map.copyOf(Optional.ofNullable(companySettings).orElseGet(Map::of));
            userSettings = List.copyOf(Optional.ofNullable(userSettings).orElseGet(List::of));
        }
    }

    public record TenantProductOption(String productId, String displayName, List<FieldReference> fieldRefs, boolean enabled) {
        public TenantProductOption {
            fieldRefs = List.copyOf(Optional.ofNullable(fieldRefs).orElseGet(List::of));
        }
    }

    public record TenantInvestorOption(String investorId, String displayName, List<FieldReference> fieldRefs, boolean enabled) {
        public TenantInvestorOption {
            fieldRefs = List.copyOf(Optional.ofNullable(fieldRefs).orElseGet(List::of));
        }
    }

    public record TenantUserSettings(String userId, String tenantId, Map<String, String> settings) {
        public TenantUserSettings {
            settings = Map.copyOf(Optional.ofNullable(settings).orElseGet(Map::of));
        }
    }

    public record FieldReference(String surface, String fieldId) { }

    public record UserTenantAssignment(String userId, String tenantId) { }

    public record TenantPipelineEligibilityRequest(String tenantId, String userId, String actorTenantId, String requestedProductId, String requestedInvestorId) { }

    public record TenantPipelineAccessAuditRecord(String tenantId, String userId, String actorId, String actorType, String code, String entityType, String entityId) { }

    public record TenantPipelineEligibility(
        String tenantId,
        String userId,
        List<String> productIds,
        List<String> investorIds,
        Map<String, String> companySettings,
        Map<String, String> userSettings
    ) {
        public TenantPipelineEligibility {
            productIds = List.copyOf(Optional.ofNullable(productIds).orElseGet(List::of));
            investorIds = List.copyOf(Optional.ofNullable(investorIds).orElseGet(List::of));
            companySettings = Map.copyOf(Optional.ofNullable(companySettings).orElseGet(Map::of));
            userSettings = Map.copyOf(Optional.ofNullable(userSettings).orElseGet(Map::of));
        }
    }

    public record FieldError(String field, String code, String message) { }

    public static class TenantPipelineException extends RuntimeException {
        private final String code;
        private final List<FieldError> fieldErrors;

        TenantPipelineException(String code, List<FieldError> fieldErrors) {
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
