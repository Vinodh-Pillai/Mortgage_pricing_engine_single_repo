package com.wcpe.quote;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class QuotePresentationModelBuilder {
    public QuoteComparisonResponse build(Quote quote, ComparisonViewConfig config, Set<String> allowedFields, Clock clock) {
        Set<String> safeAllowedFields = allowedFields == null ? Set.of() : Set.copyOf(allowedFields);
        List<ComparisonHiddenField> hiddenFields = config.columns().stream()
            .filter(column -> isHidden(column.field(), config, safeAllowedFields))
            .map(column -> new ComparisonHiddenField(column.field(), "FIELD_PERMISSION_REQUIRED"))
            .toList();
        List<ComparisonColumn> visibleColumns = config.columns().stream()
            .filter(column -> !isHidden(column.field(), config, safeAllowedFields))
            .toList();
        List<QuoteOption> orderedOptions = orderedOptions(quote.options(), config);
        if (orderedOptions.size() > config.maxCompareCount()) {
            orderedOptions = orderedOptions.subList(0, config.maxCompareCount());
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (QuoteOption option : orderedOptions) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("optionId", option.optionId().toString());
            for (ComparisonColumn column : visibleColumns) {
                Object value = valueFor(column.field(), option);
                if (value != null) {
                    row.put(column.field(), value);
                }
            }
            rows.add(Map.copyOf(row));
        }

        List<String> warnings = new ArrayList<>();
        if (clock.instant().isAfter(quote.expiresAt())) {
            warnings.add("STALE_QUOTE");
        }
        if (quote.options().isEmpty()) {
            warnings.add("NO_COMPARABLE_OPTIONS");
        }
        if (quote.options().size() > config.maxCompareCount()) {
            warnings.add("MAX_COMPARE_COUNT_APPLIED");
        }

        return new QuoteComparisonResponse(
            quote.quoteId(),
            quote.status().name(),
            config.version(),
            visibleColumns,
            List.copyOf(rows),
            hiddenFields,
            List.copyOf(warnings),
            quote.auditRef()
        );
    }

    private static boolean isHidden(String field, ComparisonViewConfig config, Set<String> allowedFields) {
        return config.restrictedFields().contains(field) && !allowedFields.contains(field);
    }

    private static List<QuoteOption> orderedOptions(List<QuoteOption> options, ComparisonViewConfig config) {
        Comparator<QuoteOption> comparator = Comparator.comparingInt(QuoteOption::rank);
        if ("productId".equals(config.approvedSortField())) {
            comparator = Comparator.comparing(QuoteOption::productId).thenComparingInt(QuoteOption::rank);
        }
        return options.stream().sorted(comparator).toList();
    }

    private static Object valueFor(String field, QuoteOption option) {
        return switch (field) {
            case "rank" -> option.rank();
            case "productLabel", "productId" -> option.productId();
            case "investorLabel", "investorId" -> option.investorId();
            case "noteRate" -> scale(option.noteRatePercent(), 5);
            case "pricePoints", "finalPriceBps" -> scale(option.finalPriceBps(), 4);
            case "lockDays" -> option.lockPeriodDays();
            case "totalAdjustments" -> scale(option.totalAdjustmentBps(), 4);
            case "margin" -> scale(option.marginBps(), 4);
            case "expiration" -> option.expiresAt().toString();
            case "warnings" -> option.rankReasons();
            default -> null;
        };
    }

    private static BigDecimal scale(BigDecimal value, int scale) {
        return value == null ? null : value.setScale(scale, RoundingMode.HALF_UP);
    }
}
