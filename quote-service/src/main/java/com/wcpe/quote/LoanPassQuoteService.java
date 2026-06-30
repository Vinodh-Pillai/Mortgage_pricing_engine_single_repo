package com.wcpe.quote;

import com.wcpe.quote.LoanPassQuoteModels.CatalogProduct;
import com.wcpe.quote.LoanPassQuoteModels.CatalogSnapshot;
import com.wcpe.quote.LoanPassQuoteModels.ExecuteProductResponse;
import com.wcpe.quote.LoanPassQuoteModels.ExecuteSummaryResponse;
import com.wcpe.quote.LoanPassQuoteModels.SummaryProduct;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class LoanPassQuoteService {
    private static final List<String> CANONICAL_STATUS_BUCKETS = List.of("approved", "rejected", "error", "no_pricing");

    private final LoanPassQuoteCatalogRepository repository;
    private final LoanPassWarmEvaluator evaluator;

    public LoanPassQuoteService(LoanPassQuoteCatalogRepository repository, LoanPassWarmEvaluator evaluator) {
        this.repository = repository;
        this.evaluator = evaluator;
    }

    public ExecuteSummaryResponse executeSummary(Map<String, Object> body, UUID headerTenantId, String correlationId) {
        Map<String, Object> normalized = normalizedRequest(body, headerTenantId);
        UUID tenantId = (UUID) normalized.get("tenantId");
        CatalogSnapshot snapshot = activeSnapshot(tenantId);
        List<CatalogProduct> products = evaluator.executeSummary(snapshot, normalized);
        Map<String, Long> statusCounts = canonicalStatusCounts(products);
        List<Integer> lockPeriods = products.stream()
            .flatMap(product -> product.lockPeriods().stream())
            .distinct()
            .sorted()
            .toList();
        return new ExecuteSummaryResponse(
            true,
            "execute-summary",
            tenantId,
            correlationId,
            snapshot.snapshotId(),
            snapshot.sourceSystem(),
            snapshot.synthetic(),
            (int) products.stream().map(CatalogProduct::productType).filter(Objects::nonNull).distinct().count(),
            (int) products.stream().map(CatalogProduct::investorName).filter(Objects::nonNull).distinct().count(),
            products.size(),
            statusCounts,
            lockPeriods,
            products.stream().map(this::summaryProduct).toList(),
            evaluator.benchmark(snapshot, normalized, 25),
            versionMetadata(snapshot)
        );
    }

    public ExecuteProductResponse executeProduct(Map<String, Object> body, UUID headerTenantId, String correlationId) {
        Map<String, Object> normalized = normalizedRequest(body, headerTenantId);
        UUID tenantId = (UUID) normalized.get("tenantId");
        String productId = stringValue(body, "productId", stringValue(body, "selectedProgramId", ""));
        if (productId.isBlank()) {
            throw new QuoteCreateException("LOANPASS_PRODUCT_ID_REQUIRED", "execute-product requires productId or selectedProgramId");
        }
        CatalogSnapshot snapshot = activeSnapshot(tenantId);
        CatalogProduct product = snapshot.products().stream()
            .filter(candidate -> productId.equals(candidate.productId()))
            .findFirst()
            .orElseThrow(() -> new QuoteCreateException("LOANPASS_PRODUCT_NOT_FOUND", "Requested product is not present in active durable quote catalog snapshot"));
        String executableStatus = executableStatus(product);
        boolean executable = "approved".equals(executableStatus);
        List<Map<String, String>> rates = executable ? executableRates(product) : List.of();
        Map<String, String> calculations = new LinkedHashMap<>();
        calculations.put("calculationPolicy", snapshot.synthetic() ? "synthetic-dev-only-no-production-rules" : "durable-source-derived");
        calculations.put("sourcePayloadHash", snapshot.payloadHash());
        putSourceRef(calculations, product, "monthlyPi", "monthly_pi");
        putSourceRef(calculations, product, "apr", "apr");
        putSourceRef(calculations, product, "adjustedPrice", "adjusted_price");
        putSourceRef(calculations, product, "basePrice", "base_price");
        putSourceRef(calculations, product, "engineAdjustedPrice", "engine_adjusted_price");
        putSourceRef(calculations, product, "losAdjustedPrice", "los_adjusted_price");
        if (!executable) {
            calculations.put("nonExecutableReason", String.join(",", errorsFor(product)));
        }
        return new ExecuteProductResponse(
            executable,
            "execute-product",
            tenantId,
            correlationId,
            snapshot.snapshotId(),
            snapshot.sourceSystem(),
            snapshot.synthetic(),
            product.productId(),
            product.productName(),
            product.investorName(),
            product.productType(),
            executableStatus,
            product.lockPeriods(),
            rates,
            calculations,
            product.rules(),
            product.stipulations(),
            product.rejections(),
            errorsFor(product),
            adjustmentsFor(product),
            product.sourceRefs(),
            versionMetadata(snapshot)
        );
    }

    private static Map<String, Long> canonicalStatusCounts(List<CatalogProduct> products) {
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        CANONICAL_STATUS_BUCKETS.forEach(status -> statusCounts.put(status, 0L));
        products.stream()
            .collect(Collectors.groupingBy(LoanPassQuoteService::executableStatus, LinkedHashMap::new, Collectors.counting()))
            .forEach(statusCounts::put);
        return statusCounts;
    }

    private static List<Map<String, String>> executableRates(CatalogProduct product) {
        if (product.noteRatePercent() == null || product.priceBps() == null) {
            return List.of();
        }
        return product.lockPeriods().stream()
            .map(lock -> Map.of(
                "lockPeriodDays", Integer.toString(lock),
                "noteRatePercent", product.noteRatePercent().toPlainString(),
                "priceBps", product.priceBps().toPlainString()
            ))
            .toList();
    }

    private CatalogSnapshot activeSnapshot(UUID tenantId) {
        return repository.activeSnapshot(tenantId)
            .orElseThrow(() -> new QuoteCreateException(
                "LOANPASS_CATALOG_NOT_AVAILABLE",
                "No durable LoanPass-compatible quote catalog snapshot is available for tenant; refusing to fabricate pricing data"
            ));
    }

    private SummaryProduct summaryProduct(CatalogProduct product) {
        return new SummaryProduct(
            product.productId(),
            product.productName(),
            product.investorName(),
            product.productType(),
            executableStatus(product),
            product.lockPeriods(),
            product.noteRatePercent(),
            product.priceBps(),
            executableRates(product),
            summaryCalculations(product),
            product.sourceRefs()
        );
    }

    private static Map<String, String> summaryCalculations(CatalogProduct product) {
        Map<String, String> calculations = new LinkedHashMap<>();
        putSourceRef(calculations, product, "monthlyPi", "monthly_pi");
        putSourceRef(calculations, product, "apr", "apr");
        putSourceRef(calculations, product, "adjustedPrice", "adjusted_price");
        putSourceRef(calculations, product, "basePrice", "base_price");
        return Map.copyOf(calculations);
    }

    private static void putSourceRef(Map<String, String> target, CatalogProduct product, String responseKey, String sourceRefKey) {
        String value = product.sourceRefs().get(sourceRefKey);
        if (value != null && !value.isBlank()) {
            target.put(responseKey, value);
        }
    }

    private Map<String, Object> normalizedRequest(Map<String, Object> body, UUID headerTenantId) {
        Map<String, Object> safeBody = body == null ? Map.of() : body;
        UUID bodyTenantId = parseOptionalTenantId(stringValue(safeBody, "tenantId", ""));
        if (headerTenantId != null && bodyTenantId != null && !headerTenantId.equals(bodyTenantId)) {
            throw new QuoteCreateException("LOANPASS_TENANT_MISMATCH", "X-Tenant-ID header must match request tenantId");
        }
        UUID tenantId = headerTenantId != null ? headerTenantId : bodyTenantId;
        if (tenantId == null) {
            throw new QuoteCreateException("LOANPASS_TENANT_REQUIRED", "tenantId is required in X-Tenant-ID header or request body");
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("tenantId", tenantId);
        normalized.put("lockPeriods", lockPeriods(safeBody));
        normalized.put("loan", safeBody.getOrDefault("loan", Map.of()));
        normalized.put("rawFieldPolicy", "concept-aligned-only-public-evidence-no-unverified-fields");
        return normalized;
    }

    private UUID parseOptionalTenantId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new QuoteCreateException("LOANPASS_TENANT_INVALID", "tenantId must be a UUID when supplied in the request body");
        }
    }

    @SuppressWarnings("unchecked")
    private List<Integer> lockPeriods(Map<String, Object> body) {
        Object value = body.get("lockPeriods");
        if (value == null) {
            value = body.get("requestedLockPeriods");
        }
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        List<Integer> locks = new ArrayList<>();
        for (Object item : values) {
            locks.add(item instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(item)));
        }
        locks.sort(Comparator.naturalOrder());
        return List.copyOf(locks);
    }

    private static List<String> errorsFor(CatalogProduct product) {
        List<String> errors = new ArrayList<>();
        String status = safeStatus(product.status());
        if (!"approved".equals(status)) {
            errors.add("source-status:" + status);
        }
        if (product.lockPeriods().isEmpty()) {
            errors.add("missing:lockPeriods");
        }
        if (product.noteRatePercent() == null) {
            errors.add("missing:noteRatePercent");
        }
        if (product.priceBps() == null) {
            errors.add("missing:priceBps");
        }
        return List.copyOf(errors);
    }

    private static String executableStatus(CatalogProduct product) {
        String status = safeStatus(product.status());
        if ("rejected".equals(status) || "error".equals(status) || "no_pricing".equals(status)) {
            return status;
        }
        if (!"approved".equals(status) || product.lockPeriods().isEmpty()
            || product.noteRatePercent() == null || product.priceBps() == null) {
            return "no_pricing";
        }
        return "approved";
    }

    private static List<String> adjustmentsFor(CatalogProduct product) {
        if (product.rules().isEmpty()) {
            return List.of();
        }
        return product.rules().entrySet().stream()
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .toList();
    }

    private static Map<String, String> versionMetadata(CatalogSnapshot snapshot) {
        return Map.of(
            "schemaVersion", snapshot.schemaVersion(),
            "generatorVersion", snapshot.generatorVersion(),
            "seed", snapshot.seed(),
            "payloadHash", snapshot.payloadHash(),
            "fieldPolicy", "concept-aligned-only-public-evidence-no-unverified-fields"
        );
    }

    private static String safeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "no_pricing";
        }
        return status.toLowerCase(java.util.Locale.ROOT);
    }

    private static String stringValue(Map<String, Object> body, String key, String defaultValue) {
        Object value = body.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }
}
