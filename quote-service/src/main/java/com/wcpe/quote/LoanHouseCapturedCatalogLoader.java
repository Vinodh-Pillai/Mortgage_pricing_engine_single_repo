package com.wcpe.quote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.quote.LoanPassQuoteModels.CatalogProduct;
import com.wcpe.quote.LoanPassQuoteModels.CatalogSnapshot;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;

public class LoanHouseCapturedCatalogLoader implements ApplicationRunner {
    static final String SOURCE_SYSTEM = "loanhouse-quickquote-reference-capture";
    static final String GENERATOR_VERSION = "loanhouse-capture-loader-v1";
    static final String SCHEMA_VERSION = "loanhouse-product-records-v1";

    private final LoanPassQuoteCatalogRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final UUID tenantId;
    private final Resource productRecords;

    public LoanHouseCapturedCatalogLoader(
        LoanPassQuoteCatalogRepository repository,
        ObjectMapper objectMapper,
        Clock clock,
        UUID tenantId,
        Resource productRecords
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.tenantId = tenantId;
        this.productRecords = productRecords;
    }

    @Override
    public void run(ApplicationArguments args) {
        repository.saveSnapshot(loadSnapshot());
    }

    CatalogSnapshot loadSnapshot() {
        try {
            byte[] payload = productRecords.getInputStream().readAllBytes();
            JsonNode root = objectMapper.readTree(payload);
            List<CatalogProduct> products = products(root.path("api_products"));
            if (products.isEmpty()) {
                products = products(root.path("approved_api_products"));
            }
            String payloadHash = ReplayHash.sha256(new String(payload, java.nio.charset.StandardCharsets.UTF_8));
            return new CatalogSnapshot(
                tenantId,
                "loanhouse-reference-capture-" + payloadHash.substring(0, 16),
                SOURCE_SYSTEM,
                false,
                GENERATOR_VERSION,
                payloadHash.substring(0, 16),
                SCHEMA_VERSION,
                clock.instant(),
                payloadHash,
                products,
                metadata(root, products.size())
            );
        } catch (IOException ex) {
            throw new IllegalStateException("LOANHOUSE_CAPTURE_CATALOG_LOAD_FAILED", ex);
        }
    }

    private List<CatalogProduct> products(JsonNode productsNode) {
        if (!productsNode.isArray()) {
            return List.of();
        }
        List<CatalogProduct> products = new ArrayList<>();
        for (JsonNode product : productsNode) {
            String productId = text(product, "product_id");
            if (productId.isBlank()) {
                continue;
            }
            products.add(new CatalogProduct(
                productId,
                firstText(product, "product_name", "product_investor_name"),
                firstText(product, "investor_name", "product_investor_name"),
                firstText(product, "product_name", "product_investor_name"),
                defaultText(product, "status", "no_pricing"),
                lockPeriods(product),
                decimal(product, "adjusted_rate"),
                decimal(product, "adjusted_price"),
                sourceRules(product),
                product.path("stipulations_present").asBoolean(false) ? List.of("source-stipulations-present") : List.of(),
                product.path("rejections_present").asBoolean(false) || "rejected".equalsIgnoreCase(text(product, "status"))
                    ? List.of("source-status:" + text(product, "status"))
                    : List.of(),
                sourceRefs(product)
            ));
        }
        return List.copyOf(products);
    }

    private List<Integer> lockPeriods(JsonNode product) {
        int lockTerm = product.path("lock_term_days").asInt(0);
        if (lockTerm > 0) {
            return List.of(lockTerm);
        }
        String rawLock = firstText(product, "lock_term");
        String digits = rawLock.replaceAll("[^0-9]", "");
        return digits.isBlank() ? List.of() : List.of(Integer.parseInt(digits));
    }

    private Map<String, String> sourceRules(JsonNode product) {
        Map<String, String> rules = new LinkedHashMap<>();
        putIfPresent(rules, "source.loanTermMonths", product, "loan_term_months");
        putIfPresent(rules, "source.interestOnlyPeriod", product, "interest_only_period");
        putIfPresent(rules, "source.productInvestorName", product, "product_investor_name");
        putIfPresent(rules, "source.priceGroup", product, "price_group");
        return Map.copyOf(rules);
    }

    private Map<String, String> sourceRefs(JsonNode product) {
        Map<String, String> refs = new LinkedHashMap<>();
        String[] keys = {
            "source_url", "source_index", "product_id", "product_code", "product_name", "product_investor_name",
            "investor_name", "investor_code", "status", "is_pricing_enabled", "loan_term_months", "interest_only_period",
            "adjusted_rate", "adjusted_price", "engine_adjusted_price", "los_adjusted_price", "base_price", "monthly_pi",
            "apr", "lock_term_days", "lock_expiration_date", "max_price", "rebate", "discount_points_or_cost",
            "investor_margin", "company_margin", "mlo_margin", "rejections_present", "stipulations_present",
            "status_rejections_present", "status_errors_present", "source", "note", "rate", "lock_term"
        };
        for (String key : keys) {
            putIfPresent(refs, key, product, key);
        }
        if (product.path("missing_fields").isArray()) {
            List<String> missing = new ArrayList<>();
            product.path("missing_fields").forEach(item -> missing.add(item.asText()));
            refs.put("missing_fields", String.join(",", missing));
        }
        return Map.copyOf(refs);
    }

    private Map<String, String> metadata(JsonNode root, int persistedProducts) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("sourceArtifact", ".local-harness/evidence/deployment-loop/loanhouse-integration/reference-capture/product-records.json");
        metadata.put("apiProductCount", defaultText(root, "api_product_count", Integer.toString(persistedProducts)));
        metadata.put("approvedApiProductCount", defaultText(root, "approved_api_product_count", "0"));
        metadata.put("persistedProductCount", Integer.toString(persistedProducts));
        metadata.put("synthetic", "false");
        metadata.put("operationConcepts", "execute-summary,execute-product");
        metadata.put("fieldPolicy", "captured-loanhouse-product-data-only-no-inferred-pricing-rules");
        return Map.copyOf(metadata);
    }

    private static BigDecimal decimal(JsonNode product, String key) {
        JsonNode value = product.path(key);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String raw = value.asText().replace("%", "").trim();
        if (raw.isBlank()) {
            return null;
        }
        BigDecimal parsed = new BigDecimal(raw);
        return BigDecimal.ZERO.compareTo(parsed) == 0 ? null : parsed;
    }

    private static void putIfPresent(Map<String, String> target, String targetKey, JsonNode product, String sourceKey) {
        JsonNode value = product.path(sourceKey);
        if (!value.isMissingNode() && !value.isNull()) {
            String raw = value.asText();
            if (!raw.isBlank()) {
                target.put(targetKey, raw);
            }
        }
    }

    private static String defaultText(JsonNode node, String key, String defaultValue) {
        String value = text(node, key);
        return value.isBlank() ? defaultValue : value;
    }

    private static String firstText(JsonNode node, String... keys) {
        for (String key : keys) {
            String value = text(node, key);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String text(JsonNode node, String key) {
        JsonNode value = node.path(key);
        return value.isMissingNode() || value.isNull() ? "" : value.asText().trim();
    }
}
