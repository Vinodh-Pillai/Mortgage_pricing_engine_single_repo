package com.wcpe.migration.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class PpeMigrationService {
    private static final String CANONICAL_VERSION = "v2";
    private static final Set<String> SUPPORTED_SOURCE_SYSTEMS = Set.of("OPTIMAL_BLUE", "POLLY", "LOANPASS", "LOANWEFT");
    private static final Set<String> SUPPORTED_FORMATS = Set.of("JSON", "YAML");

    private final ObjectMapper mapper;
    private final Map<UUID, Map<String, StoredImport>> importsByTenant = new ConcurrentHashMap<>();
    private final Map<UUID, List<AuditEvent>> auditByTenant = new ConcurrentHashMap<>();

    public PpeMigrationService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ImportResponse importPackage(UUID tenantId, ImportRequest request) {
        requireTenant(tenantId);
        ImportRequest safeRequest = request == null ? new ImportRequest(null, null, true, null, null, null) : request;
        String importId = "ppe-import-" + UUID.randomUUID();
        ValidationReport report = validate(tenantId, importId, safeRequest);
        boolean loaded = !safeRequest.dryRun() && report.blockers().isEmpty();
        String state = loaded ? "DRAFT_VALIDATED" : safeRequest.dryRun() && report.blockers().isEmpty() ? "DRY_RUN_VALIDATED" : "VALIDATION_BLOCKED";
        report = report.withDraftState(state);
        List<IntegrationCommand> commands = report.blockers().isEmpty() ? integrationCommands(tenantId, importId, report.mappings()) : List.of();

        recordAudit(tenantId, new AuditEvent("IMPORT_VALIDATE", importId, safeRequest.requestedBy(), Instant.now(),
                safeRequest.sourceSystem(), safeRequest.sourceVersion(), safeRequest.dryRun(), report.validationHash(),
                report.blockers().isEmpty() ? "PASS" : "BLOCKED", List.of("migration-service")));
        if (loaded) {
            importsByTenant.computeIfAbsent(tenantId, ignored -> new ConcurrentHashMap<>())
                    .put(importId, new StoredImport(importId, safeRequest, report, commands, false));
            recordAudit(tenantId, new AuditEvent("IMPORT_LOAD", importId, safeRequest.requestedBy(), Instant.now(),
                    safeRequest.sourceSystem(), safeRequest.sourceVersion(), false, report.validationHash(), "DRAFT_LOADED",
                    List.of("governance-service", "catalog-service", "adjustment-service", "rate-feed-service")));
        } else {
            importsByTenant.computeIfAbsent(tenantId, ignored -> new ConcurrentHashMap<>())
                    .put(importId, new StoredImport(importId, safeRequest, report, commands, false));
        }
        return new ImportResponse(importId, report, loaded, safeRequest.dryRun(), commands, auditTrail(tenantId).events());
    }

    public ValidationReport validationReport(UUID tenantId, String importId) {
        requireTenant(tenantId);
        StoredImport stored = storedImport(tenantId, importId);
        return stored.report();
    }

    public PublishRequestResult requestPublish(UUID tenantId, String importId) {
        requireTenant(tenantId);
        StoredImport stored = storedImport(tenantId, importId);
        ValidationReport report = stored.report();
        if (!report.blockers().isEmpty()) {
            recordAudit(tenantId, new AuditEvent("PUBLISH_REQUEST", importId, stored.request().requestedBy(), Instant.now(),
                    stored.request().sourceSystem(), stored.request().sourceVersion(), stored.request().dryRun(), report.validationHash(),
                    "REJECTED_VALIDATION_BLOCKED", List.of("governance-service")));
            return new PublishRequestResult(importId, "REJECTED_VALIDATION_BLOCKED", false,
                    List.of(new IntegrationCommand("governance-service", "reject-publish-request", importId,
                            Map.of("reason", "validation blockers must be resolved before governance approval"))),
                    List.copyOf(report.blockers()));
        }
        StoredImport updated = stored.withPublishRequested(true);
        importsByTenant.get(tenantId).put(importId, updated);
        IntegrationCommand governanceCommand = new IntegrationCommand("governance-service", "request-draft-approval", importId,
                Map.of("draftState", report.draftState(), "pricingUsable", false, "validationHash", report.validationHash()));
        recordAudit(tenantId, new AuditEvent("PUBLISH_REQUEST", importId, stored.request().requestedBy(), Instant.now(),
                stored.request().sourceSystem(), stored.request().sourceVersion(), stored.request().dryRun(), report.validationHash(),
                "PENDING_GOVERNANCE_APPROVAL", List.of("governance-service")));
        return new PublishRequestResult(importId, "PENDING_GOVERNANCE_APPROVAL", false, List.of(governanceCommand), List.of());
    }

    public ExportResponse exportPackage(UUID tenantId, ExportRequest request) {
        requireTenant(tenantId);
        ExportRequest safeRequest = request == null ? new ExportRequest(null, null, null, null, null, null, null) : request;
        String targetSystem = normalizeSourceSystem(safeRequest.targetSystem());
        String format = normalizeFormat(safeRequest.format());
        List<PortableArtifact> artifacts = exportArtifacts(safeRequest);
        List<String> warnings = new ArrayList<>();
        if (!SUPPORTED_SOURCE_SYSTEMS.contains(targetSystem)) {
            warnings.add("UNSUPPORTED_TARGET_SYSTEM:" + targetSystem);
        }
        String exportId = "ppe-export-" + UUID.randomUUID();
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("schemaVersion", CANONICAL_VERSION);
        document.put("targetSystem", targetSystem);
        document.put("format", format);
        document.put("generatedAt", Instant.now().toString());
        document.put("pricingRules", artifacts.stream().filter(a -> "pricingRule".equals(a.artifactType())).toList());
        document.put("ruleBooks", artifacts.stream().filter(a -> "ruleBook".equals(a.artifactType())).toList());
        document.put("rateSheets", artifacts.stream().filter(a -> "rateSheet".equals(a.artifactType())).toList());
        document.put("eligibilityMatrices", artifacts.stream().filter(a -> "eligibilityMatrix".equals(a.artifactType())).toList());
        String portable = "YAML".equals(format) ? toYaml(document) : toJson(document);
        String hash = stableHash(document, portable);
        recordAudit(tenantId, new AuditEvent("EXPORT_CREATE", exportId, safeRequest.requestedBy(), Instant.now(), targetSystem,
                CANONICAL_VERSION, false, hash, warnings.isEmpty() ? "CREATED" : "CREATED_WITH_WARNINGS",
                List.of("catalog-service", "adjustment-service", "rate-feed-service")));
        return new ExportResponse(exportId, targetSystem, format, CANONICAL_VERSION, artifacts, portable, hash,
                warnings, auditTrail(tenantId).events());
    }

    public AuditTrail auditTrail(UUID tenantId) {
        requireTenant(tenantId);
        return new AuditTrail(List.copyOf(auditByTenant.getOrDefault(tenantId, List.of())));
    }

    private ValidationReport validate(UUID tenantId, String importId, ImportRequest request) {
        String sourceSystem = normalizeSourceSystem(request.sourceSystem());
        VersionCheck versionCheck = versionCheck(request.sourceVersion());
        List<ValidationIssue> blockers = new ArrayList<>();
        List<ValidationIssue> warnings = new ArrayList<>(versionCheck.warnings());
        if (!SUPPORTED_SOURCE_SYSTEMS.contains(sourceSystem)) {
            blockers.add(new ValidationIssue("UNSUPPORTED_SOURCE_SYSTEM", "sourceSystem", sourceSystem,
                    "Supported systems are Optimal Blue, Polly, LoanPASS, and LoanWeft exchange packages"));
        }
        blockers.addAll(versionCheck.blockers());
        if (request.migrationPackage() == null || request.migrationPackage().isEmpty()) {
            blockers.add(new ValidationIssue("EMPTY_MIGRATION_PACKAGE", "package", "<empty>",
                    "At least one pricing rule, rule book, rate sheet, eligibility matrix, adjustment, or product is required"));
        }

        List<MappedArtifact> mappings = new ArrayList<>();
        PpeMigrationPackage migrationPackage = request.migrationPackage() == null ? PpeMigrationPackage.empty() : request.migrationPackage();
        mapArtifacts(sourceSystem, "pricingRule", migrationPackage.pricingRules(), List.of("ruleId", "ruleExpression"), mappings, blockers, warnings);
        mapArtifacts(sourceSystem, "ruleBook", migrationPackage.ruleBooks(), List.of("ruleBookId"), mappings, blockers, warnings);
        mapArtifacts(sourceSystem, "rateSheet", migrationPackage.rateSheets(), List.of("rateSheetId", "effectiveDate"), mappings, blockers, warnings);
        mapArtifacts(sourceSystem, "eligibilityMatrix", migrationPackage.eligibilityMatrices(), List.of("matrixId", "ruleExpression"), mappings, blockers, warnings);
        mapArtifacts(sourceSystem, "adjustment", migrationPackage.adjustments(), List.of("adjustmentId"), mappings, blockers, warnings);
        mapArtifacts(sourceSystem, "product", migrationPackage.products(), List.of("productCode"), mappings, blockers, warnings);

        int compiledRuleCount = blockers.isEmpty()
                ? (int) mappings.stream().filter(a -> Set.of("pricingRule", "eligibilityMatrix").contains(a.artifactType())).count()
                : 0;
        boolean publishBlocked = !blockers.isEmpty();
        List<AdapterDeclaration> adapters = adapters(sourceSystem);
        String validationHash = stableHash(tenantId, importId, sourceSystem, versionCheck, mappings, blockers, warnings, request.dryRun());
        return new ValidationReport(importId, sourceSystem, versionCheck.effectiveVersion(), request.dryRun(), "VALIDATED",
                adapters, mappings, List.copyOf(blockers), List.copyOf(warnings), publishBlocked, compiledRuleCount,
                versionCheck, validationHash);
    }

    private void mapArtifacts(String sourceSystem, String artifactType, List<Map<String, Object>> sourceArtifacts,
            List<String> requiredCanonicalFields, List<MappedArtifact> mappings, List<ValidationIssue> blockers,
            List<ValidationIssue> warnings) {
        for (int index = 0; index < sourceArtifacts.size(); index++) {
            Map<String, Object> sourceArtifact = sourceArtifacts.get(index) == null ? Map.of() : sourceArtifacts.get(index);
            Map<String, Object> canonical = new LinkedHashMap<>();
            List<String> unsupported = new ArrayList<>();
            for (Map.Entry<String, Object> entry : sourceArtifact.entrySet()) {
                String canonicalField = canonicalField(sourceSystem, artifactType, entry.getKey());
                if (canonicalField == null) {
                    unsupported.add(entry.getKey());
                } else if (isBlank(entry.getValue())) {
                    blockers.add(new ValidationIssue("MISSING_REQUIRED_FIELD", artifactType + "." + entry.getKey(), "<blank>",
                            "Blank imported fields block publish; defaults are not invented"));
                } else {
                    canonical.put(canonicalField, entry.getValue());
                }
            }
            for (String required : requiredCanonicalFields) {
                if (!canonical.containsKey(required)) {
                    blockers.add(new ValidationIssue("MISSING_CANONICAL_FIELD", artifactType + "." + required,
                            "<missing>", "Required canonical field is missing after PPE field mapping"));
                }
            }
            boolean manualReview = !unsupported.isEmpty() || sourceArtifact.containsKey("manualReview") || sourceArtifact.containsKey("Manual Review");
            if (!unsupported.isEmpty()) {
                warnings.add(new ValidationIssue("UNSUPPORTED_FIELD", artifactType + "[" + index + "]", String.join(",", unsupported),
                        "Unsupported PPE fields are retained in the validation report and require review before publication"));
            }
            String sourceId = Objects.toString(canonical.getOrDefault("sourceId",
                    canonical.getOrDefault("productCode", canonical.getOrDefault("rateSheetId",
                            canonical.getOrDefault("matrixId", artifactType + "-" + index)))), artifactType + "-" + index);
            mappings.add(new MappedArtifact(sourceSystem, artifactType, sourceId, Map.copyOf(canonical), List.copyOf(unsupported),
                    manualReview, unsupported.isEmpty() ? "NONE" : "LOSSY_UNSUPPORTED_FIELDS_RETAINED_IN_REPORT",
                    integrationTargets(artifactType)));
        }
    }

    private String canonicalField(String sourceSystem, String artifactType, String fieldName) {
        if (fieldName == null) {
            return null;
        }
        String normalized = normalizeField(fieldName);
        if (Set.of("source_id", "external_id", "row_id", "id").contains(normalized)) return "sourceId";
        if (Set.of("schema_version", "source_version").contains(normalized)) return "sourceVersion";
        if (Set.of("product_code", "program_code", "loanpass_program_code", "ob_product_code", "polly_product_code").contains(normalized)) return "productCode";
        if (Set.of("product_name", "program_name", "name").contains(normalized)) return "productName";
        if (Set.of("investor", "investor_code", "investor_name").contains(normalized)) return "investorCode";
        if (Set.of("rule_id", "rule_code").contains(normalized)) return "ruleId";
        if (Set.of("rule_expression", "eligibility_rule", "condition", "condition_expression").contains(normalized)) return "ruleExpression";
        if (Set.of("rule_book_id", "rulebook_id", "policy_book").contains(normalized)) return "ruleBookId";
        if (Set.of("rate_sheet_id", "sheet_id", "sheet_name").contains(normalized)) return "rateSheetId";
        if (Set.of("effective_date", "effective_at", "lock_date").contains(normalized)) return "effectiveDate";
        if (Set.of("matrix_id", "eligibility_matrix_id").contains(normalized)) return "matrixId";
        if (Set.of("adjustment_id", "adjustment_code", "llpa_code").contains(normalized)) return "adjustmentId";
        if (Set.of("adjustment_type", "fee_type", "llpa_type").contains(normalized)) return "adjustmentType";
        if (Set.of("lock_term", "lock_term_days", "lock_days").contains(normalized)) return "lockTermDays";
        if (Set.of("rate", "note_rate", "base_rate", "par_rate").contains(normalized)) return "rateValue";
        if (Set.of("margin", "price", "price_adjustment", "adjustment_value").contains(normalized)) return "priceOrMarginValue";
        if (Set.of("fico", "fico_bucket", "credit_score_bucket").contains(normalized)) return "ficoBand";
        if (Set.of("ltv", "ltv_bucket", "loan_to_value_bucket").contains(normalized)) return "ltvBand";
        if (Set.of("occupancy", "property_type", "loan_purpose", "state", "county").contains(normalized)) return normalized;
        if ("LOANPASS".equals(sourceSystem) && Set.of("dscr", "dscr_ratio", "bank_statement_months", "asset_depletion", "non_qm_type").contains(normalized)) {
            return switch (normalized) {
                case "dscr", "dscr_ratio" -> "nonQmDscr";
                case "bank_statement_months" -> "nonQmBankStatementMonths";
                case "asset_depletion" -> "nonQmAssetDepletion";
                default -> "nonQmProductType";
            };
        }
        if ("POLLY".equals(sourceSystem) && Set.of("scenario_field", "value_set", "comparison_operator").contains(normalized)) {
            return normalized;
        }
        if ("OPTIMAL_BLUE".equals(sourceSystem) && Set.of("ob_rule_guid", "ob_rate_lock", "ob_investor_product").contains(normalized)) {
            return normalized;
        }
        return null;
    }

    private List<IntegrationCommand> integrationCommands(UUID tenantId, String importId, List<MappedArtifact> mappings) {
        List<IntegrationCommand> commands = new ArrayList<>();
        commands.add(new IntegrationCommand("governance-service", "create-draft-change-set", importId,
                Map.of("tenantId", tenantId.toString(), "initialState", "DRAFT", "requiresApprovalBeforePricingUse", true)));
        if (mappings.stream().anyMatch(a -> Set.of("product", "ruleBook").contains(a.artifactType()))) {
            commands.add(new IntegrationCommand("catalog-service", "stage-catalog-drafts", importId,
                    Map.of("artifactCount", countArtifacts(mappings, Set.of("product", "ruleBook")))));
        }
        if (mappings.stream().anyMatch(a -> Set.of("pricingRule", "adjustment", "eligibilityMatrix").contains(a.artifactType()))) {
            commands.add(new IntegrationCommand("adjustment-service", "stage-adjustment-and-rule-drafts", importId,
                    Map.of("artifactCount", countArtifacts(mappings, Set.of("pricingRule", "adjustment", "eligibilityMatrix")))));
        }
        if (mappings.stream().anyMatch(a -> "rateSheet".equals(a.artifactType()))) {
            commands.add(new IntegrationCommand("rate-feed-service", "stage-rate-sheet-drafts", importId,
                    Map.of("artifactCount", countArtifacts(mappings, Set.of("rateSheet")))));
        }
        return List.copyOf(commands);
    }

    private int countArtifacts(List<MappedArtifact> mappings, Set<String> types) {
        return (int) mappings.stream().filter(a -> types.contains(a.artifactType())).count();
    }

    private List<String> integrationTargets(String artifactType) {
        return switch (artifactType) {
            case "product", "ruleBook" -> List.of("governance-service", "catalog-service");
            case "rateSheet" -> List.of("governance-service", "rate-feed-service");
            case "pricingRule", "eligibilityMatrix", "adjustment" -> List.of("governance-service", "adjustment-service");
            default -> List.of("governance-service");
        };
    }

    private List<AdapterDeclaration> adapters(String sourceSystem) {
        String supportedFileType = switch (sourceSystem) {
            case "OPTIMAL_BLUE" -> "optimal-blue-lender-export-json-or-yaml";
            case "POLLY" -> "polly-lender-export-json-or-yaml";
            case "LOANPASS" -> "loanpass-lender-export-json-or-yaml";
            case "LOANWEFT" -> "loanweft-portable-package-json-or-yaml";
            default -> "unsupported";
        };
        return List.of(new AdapterDeclaration(sourceSystem, supportedFileType, CANONICAL_VERSION,
                "Supported fields are mapped to canonical product, rate-sheet, eligibility, adjustment, and Non-QM schema keys",
                "Unsupported fields are reported and retained as validation evidence; private API scraping is not supported",
                true));
    }

    private VersionCheck versionCheck(String sourceVersion) {
        String normalized = normalizeVersion(sourceVersion);
        if ("v1".equals(normalized)) {
            return new VersionCheck(sourceVersion, CANONICAL_VERSION, true,
                    List.of(new ValidationIssue("MIGRATED_V1_TO_V2", "schemaVersion", "v1", "v1 package shape was migrated to v2 canonical field names")),
                    List.of());
        }
        if (CANONICAL_VERSION.equals(normalized)) {
            return new VersionCheck(sourceVersion, CANONICAL_VERSION, false, List.of(), List.of());
        }
        return new VersionCheck(sourceVersion, normalized, false, List.of(),
                List.of(new ValidationIssue("UNSUPPORTED_SCHEMA_VERSION", "schemaVersion", Objects.toString(sourceVersion, "<missing>"),
                        "Only v1 and v2 PPE exchange schema versions are supported")));
    }

    private List<PortableArtifact> exportArtifacts(ExportRequest request) {
        List<PortableArtifact> artifacts = new ArrayList<>();
        addPortableArtifacts(artifacts, "pricingRule", request.pricingRules());
        addPortableArtifacts(artifacts, "ruleBook", request.ruleBooks());
        addPortableArtifacts(artifacts, "rateSheet", request.rateSheets());
        addPortableArtifacts(artifacts, "eligibilityMatrix", request.eligibilityMatrices());
        return artifacts.stream().sorted(Comparator.comparing(PortableArtifact::artifactType).thenComparing(PortableArtifact::artifactId)).toList();
    }

    private void addPortableArtifacts(List<PortableArtifact> artifacts, String artifactType, List<Map<String, Object>> source) {
        for (int i = 0; i < source.size(); i++) {
            Map<String, Object> payload = source.get(i) == null ? Map.of() : source.get(i);
            String artifactId = Objects.toString(payload.getOrDefault("id", payload.getOrDefault("code", artifactType + "-" + i)));
            artifacts.add(new PortableArtifact(artifactType, artifactId, CANONICAL_VERSION, Map.copyOf(payload)));
        }
    }

    private String toJson(Object value) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new MigrationException("EXPORT_SERIALIZATION_FAILED");
        }
    }

    private String toYaml(Map<String, Object> document) {
        StringBuilder yaml = new StringBuilder();
        document.forEach((key, value) -> yaml.append(key).append(": ").append(yamlValue(value)).append('\n'));
        return yaml.toString();
    }

    private String yamlValue(Object value) {
        if (value instanceof List<?> list) {
            return list.isEmpty() ? "[]" : "\n" + list.stream().map(item -> "  - " + item).reduce((a, b) -> a + "\n" + b).orElse("");
        }
        return Objects.toString(value, "");
    }

    private StoredImport storedImport(UUID tenantId, String importId) {
        StoredImport stored = importsByTenant.getOrDefault(tenantId, Map.of()).get(importId);
        if (stored == null) {
            throw new MigrationException("IMPORT_NOT_FOUND");
        }
        return stored;
    }

    private void recordAudit(UUID tenantId, AuditEvent event) {
        auditByTenant.computeIfAbsent(tenantId, ignored -> new ArrayList<>()).add(event);
    }

    private static void requireTenant(UUID tenantId) {
        if (tenantId == null) {
            throw new MigrationException("TENANT_ID_REQUIRED");
        }
    }

    private static String normalizeSourceSystem(String sourceSystem) {
        if (sourceSystem == null || sourceSystem.isBlank()) return "UNKNOWN";
        String normalized = sourceSystem.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        if ("OPTIMALBLUE".equals(normalized)) return "OPTIMAL_BLUE";
        return normalized;
    }

    private static String normalizeFormat(String format) {
        String normalized = format == null || format.isBlank() ? "JSON" : format.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_FORMATS.contains(normalized)) {
            throw new MigrationException("UNSUPPORTED_EXPORT_FORMAT");
        }
        return normalized;
    }

    private static String normalizeVersion(String version) {
        if (version == null || version.isBlank()) return "<missing>";
        String normalized = version.trim().toLowerCase(Locale.ROOT);
        if ("1".equals(normalized)) return "v1";
        if ("2".equals(normalized)) return "v2";
        return normalized;
    }

    private static String normalizeField(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replace("%", "percent").replace('-', '_').replace(' ', '_');
    }

    private static boolean isBlank(Object value) {
        return value == null || value instanceof String text && text.isBlank();
    }

    private static String stableHash(Object... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Object value : values) {
                digest.update(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private record StoredImport(String importId, ImportRequest request, ValidationReport report,
            List<IntegrationCommand> commands, boolean publishRequested) {
        StoredImport withPublishRequested(boolean requested) {
            return new StoredImport(importId, request, report, commands, requested);
        }
    }

    public record ImportRequest(String sourceSystem, String sourceVersion, boolean dryRun,
            PpeMigrationPackage migrationPackage, String requestedBy, String correlationId) {
        public ImportRequest {
            requestedBy = requestedBy == null || requestedBy.isBlank() ? "system" : requestedBy;
            correlationId = correlationId == null || correlationId.isBlank() ? UUID.randomUUID().toString() : correlationId;
        }
    }

    public record PpeMigrationPackage(List<Map<String, Object>> pricingRules, List<Map<String, Object>> ruleBooks,
            List<Map<String, Object>> rateSheets, List<Map<String, Object>> eligibilityMatrices,
            List<Map<String, Object>> adjustments, List<Map<String, Object>> products) {
        public PpeMigrationPackage {
            pricingRules = immutableList(pricingRules);
            ruleBooks = immutableList(ruleBooks);
            rateSheets = immutableList(rateSheets);
            eligibilityMatrices = immutableList(eligibilityMatrices);
            adjustments = immutableList(adjustments);
            products = immutableList(products);
        }

        static PpeMigrationPackage empty() {
            return new PpeMigrationPackage(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }

        boolean isEmpty() {
            return pricingRules.isEmpty() && ruleBooks.isEmpty() && rateSheets.isEmpty()
                    && eligibilityMatrices.isEmpty() && adjustments.isEmpty() && products.isEmpty();
        }
    }

    public record ImportResponse(String importId, ValidationReport validationReport, boolean loaded, boolean dryRun,
            List<IntegrationCommand> integrationCommands, List<AuditEvent> auditTrail) {}

    public record ValidationReport(String importId, String sourceSystem, String schemaVersion, boolean dryRun,
            String draftState, List<AdapterDeclaration> adapters, List<MappedArtifact> mappings,
            List<ValidationIssue> blockers, List<ValidationIssue> warnings, boolean publishBlocked,
            int compiledRuleCount, VersionCheck versionCheck, String validationHash) {
        ValidationReport withDraftState(String state) {
            return new ValidationReport(importId, sourceSystem, schemaVersion, dryRun, state, adapters, mappings,
                    blockers, warnings, publishBlocked, compiledRuleCount, versionCheck, validationHash);
        }
    }

    public record AdapterDeclaration(String sourceSystem, String supportedFileType, String schemaVersion,
            String fieldMap, String lossiness, boolean manualReviewDeclaration) {}

    public record MappedArtifact(String sourceSystem, String artifactType, String sourceId,
            Map<String, Object> canonicalFields, List<String> unsupportedFields, boolean manualReviewRequired,
            String lossiness, List<String> integrationTargets) {}

    public record ValidationIssue(String code, String field, String actual, String message) {}

    public record VersionCheck(String sourceVersion, String effectiveVersion, boolean migrated,
            List<ValidationIssue> warnings, List<ValidationIssue> blockers) {}

    public record IntegrationCommand(String targetService, String action, String importId, Map<String, Object> payload) {}

    public record PublishRequestResult(String importId, String status, boolean pricingUsable,
            List<IntegrationCommand> integrationCommands, List<ValidationIssue> blockers) {}

    public record ExportRequest(String targetSystem, String format, String requestedBy,
            List<Map<String, Object>> pricingRules, List<Map<String, Object>> ruleBooks,
            List<Map<String, Object>> rateSheets, List<Map<String, Object>> eligibilityMatrices) {
        public ExportRequest {
            requestedBy = requestedBy == null || requestedBy.isBlank() ? "system" : requestedBy;
            pricingRules = immutableList(pricingRules);
            ruleBooks = immutableList(ruleBooks);
            rateSheets = immutableList(rateSheets);
            eligibilityMatrices = immutableList(eligibilityMatrices);
        }
    }

    public record PortableArtifact(String artifactType, String artifactId, String schemaVersion, Map<String, Object> payload) {}

    public record ExportResponse(String exportId, String targetSystem, String format, String schemaVersion,
            List<PortableArtifact> artifacts, String portableDocument, String packageHash, List<String> warnings,
            List<AuditEvent> auditTrail) {}

    public record AuditEvent(String operation, String objectId, String actorId, Instant occurredAt, String sourceSystem,
            String schemaVersion, boolean dryRun, String evidenceHash, String outcome, List<String> integrationTargets) {}

    public record AuditTrail(List<AuditEvent> events) {}

    public static final class MigrationException extends RuntimeException {
        public MigrationException(String message) {
            super(message);
        }
    }

    private static List<Map<String, Object>> immutableList(List<Map<String, Object>> values) {
        if (values == null) return List.of();
        return values.stream().map(value -> value == null ? Map.<String, Object>of() : Map.copyOf(value)).toList();
    }
}
