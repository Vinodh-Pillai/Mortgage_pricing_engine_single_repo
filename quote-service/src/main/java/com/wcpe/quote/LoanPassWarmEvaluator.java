package com.wcpe.quote;

import com.wcpe.quote.LoanPassQuoteModels.CatalogProduct;
import com.wcpe.quote.LoanPassQuoteModels.CatalogSnapshot;
import com.wcpe.quote.LoanPassQuoteModels.WarmBenchmark;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class LoanPassWarmEvaluator {
    public List<CatalogProduct> executeSummary(CatalogSnapshot snapshot, Map<String, Object> normalizedRequest) {
        Set<Integer> requestedLocks = requestedLocks(normalizedRequest);
        return snapshot.products().stream()
            .filter(product -> requestedLocks.isEmpty() || product.lockPeriods().stream().anyMatch(requestedLocks::contains))
            .toList();
    }

    public WarmBenchmark benchmark(CatalogSnapshot snapshot, Map<String, Object> normalizedRequest, int sampleSize) {
        int safeSampleSize = Math.max(1, Math.min(sampleSize, 100));
        List<Long> timings = new ArrayList<>();
        int productCount = 0;
        for (int i = 0; i < safeSampleSize; i++) {
            long start = System.nanoTime();
            productCount = executeSummary(snapshot, normalizedRequest).size();
            timings.add((System.nanoTime() - start) / 1_000L);
        }
        timings.sort(Comparator.naturalOrder());
        return new WarmBenchmark(percentile(timings, 50), percentile(timings, 99), productCount, safeSampleSize);
    }

    @SuppressWarnings("unchecked")
    private Set<Integer> requestedLocks(Map<String, Object> normalizedRequest) {
        Object value = normalizedRequest.get("lockPeriods");
        if (!(value instanceof List<?> values)) {
            return Set.of();
        }
        return values.stream()
            .map(item -> item instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(item)))
            .collect(Collectors.toUnmodifiableSet());
    }

    private static long percentile(List<Long> sortedMicros, int percentile) {
        int index = Math.min(sortedMicros.size() - 1, Math.max(0, (int) Math.ceil((percentile / 100.0) * sortedMicros.size()) - 1));
        return sortedMicros.get(index);
    }
}
