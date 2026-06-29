package com.wcpe.quote;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LoanPassQuoteModels {
    private LoanPassQuoteModels() {
    }

    public record CatalogSnapshot(
        UUID tenantId,
        String snapshotId,
        String sourceSystem,
        boolean synthetic,
        String generatorVersion,
        String seed,
        String schemaVersion,
        Instant loadedAt,
        String payloadHash,
        List<CatalogProduct> products,
        Map<String, String> metadata
    ) {
        public CatalogSnapshot {
            products = List.copyOf(products == null ? List.of() : products);
            metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
        }
    }

    public record CatalogProduct(
        String productId,
        String productName,
        String investorName,
        String productType,
        String status,
        List<Integer> lockPeriods,
        BigDecimal noteRatePercent,
        BigDecimal priceBps,
        Map<String, String> rules,
        List<String> stipulations,
        List<String> rejections,
        Map<String, String> sourceRefs
    ) {
        public CatalogProduct {
            lockPeriods = List.copyOf(lockPeriods == null ? List.of() : lockPeriods);
            rules = Map.copyOf(rules == null ? Map.of() : rules);
            stipulations = List.copyOf(stipulations == null ? List.of() : stipulations);
            rejections = List.copyOf(rejections == null ? List.of() : rejections);
            sourceRefs = Map.copyOf(sourceRefs == null ? Map.of() : sourceRefs);
        }
    }

    public record ExecuteSummaryResponse(
        boolean success,
        String operation,
        UUID tenantId,
        String correlationId,
        String snapshotId,
        String sourceSystem,
        boolean synthetic,
        int productTypes,
        int investors,
        int productCount,
        Map<String, Long> statusCounts,
        List<Integer> lockPeriods,
        List<SummaryProduct> products,
        WarmBenchmark benchmark,
        Map<String, String> versionMetadata
    ) {
        public ExecuteSummaryResponse {
            statusCounts = Map.copyOf(statusCounts == null ? Map.of() : statusCounts);
            lockPeriods = List.copyOf(lockPeriods == null ? List.of() : lockPeriods);
            products = List.copyOf(products == null ? List.of() : products);
            versionMetadata = Map.copyOf(versionMetadata == null ? Map.of() : versionMetadata);
        }
    }

    public record SummaryProduct(
        String productId,
        String productName,
        String investorName,
        String productType,
        String status,
        List<Integer> lockPeriods,
        BigDecimal noteRatePercent,
        BigDecimal priceBps,
        List<Map<String, String>> rates,
        Map<String, String> calculations,
        Map<String, String> sourceRefs
    ) {
        public SummaryProduct {
            lockPeriods = List.copyOf(lockPeriods == null ? List.of() : lockPeriods);
            rates = List.copyOf(rates == null ? List.of() : rates);
            calculations = Map.copyOf(calculations == null ? Map.of() : calculations);
            sourceRefs = Map.copyOf(sourceRefs == null ? Map.of() : sourceRefs);
        }
    }

    public record ExecuteProductResponse(
        boolean success,
        String operation,
        UUID tenantId,
        String correlationId,
        String snapshotId,
        String sourceSystem,
        boolean synthetic,
        String productId,
        String productName,
        String investorName,
        String productType,
        String status,
        List<Integer> lockPeriods,
        List<Map<String, String>> rates,
        Map<String, String> calculations,
        Map<String, String> rules,
        List<String> stipulations,
        List<String> rejections,
        List<String> errors,
        List<String> adjustments,
        Map<String, String> sourceRefs,
        Map<String, String> versionMetadata
    ) {
        public ExecuteProductResponse {
            lockPeriods = List.copyOf(lockPeriods == null ? List.of() : lockPeriods);
            rates = List.copyOf(rates == null ? List.of() : rates);
            calculations = Map.copyOf(calculations == null ? Map.of() : calculations);
            rules = Map.copyOf(rules == null ? Map.of() : rules);
            stipulations = List.copyOf(stipulations == null ? List.of() : stipulations);
            rejections = List.copyOf(rejections == null ? List.of() : rejections);
            errors = List.copyOf(errors == null ? List.of() : errors);
            adjustments = List.copyOf(adjustments == null ? List.of() : adjustments);
            sourceRefs = Map.copyOf(sourceRefs == null ? Map.of() : sourceRefs);
            versionMetadata = Map.copyOf(versionMetadata == null ? Map.of() : versionMetadata);
        }
    }

    public record WarmBenchmark(long p50Micros, long p99Micros, int evaluatedProducts, int sampleSize) {
    }
}
