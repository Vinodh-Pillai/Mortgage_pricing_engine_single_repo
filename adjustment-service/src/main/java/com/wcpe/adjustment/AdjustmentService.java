package com.wcpe.adjustment;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.wcpe.adjustment.AdjustmentRuleBook.RuleBookSelector;
import com.wcpe.adjustment.RuleBookResolver.JdbcRuleBookRepository;
import com.wcpe.adjustment.overlay.OverlayInputs;
import com.wcpe.adjustment.overlay.OverlayPolicyType;
import com.wcpe.adjustment.overlay.OverlayRule;
import com.wcpe.adjustment.overlay.OverlayRuleRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Real LLPA adjustment calculator. Rule values come from a resolved published
 * rule book; no LLPA rates, thresholds, or tenant policy are hard-coded here.
 */
@Service
public class AdjustmentService {
    private final RuleBookResolver resolver;
    private final RuleIndexer indexer;
    private final RuleEvaluationEngine engine;
    private final OverlayRuleRepository overlayRuleRepository;
    private final Cache<IndexCacheKey, RuleIndexer.RuleBookIndex> indexCache;

    public AdjustmentService() {
        throw new IllegalStateException("AdjustmentService requires a JDBC DataSource-backed rule-book repository in production");
    }

    @Autowired
    public AdjustmentService(DataSource dataSource) {
        this(new RuleBookResolver(new JdbcRuleBookRepository(dataSource)),
            new RuleIndexer(),
            OverlayRuleRepository.failClosed("overlay rule persistence schema is not configured; refusing to use a volatile overlay store of record"));
    }

    public AdjustmentService(RuleBookResolver resolver) {
        this(resolver, new RuleIndexer());
    }

    public AdjustmentService(RuleBookResolver resolver, RuleIndexer indexer) {
        this(resolver, indexer, new RuleEvaluationEngine(indexer, new PrecisionNormalizer()));
    }

    public AdjustmentService(RuleBookResolver resolver, RuleIndexer indexer, OverlayRuleRepository overlayRuleRepository) {
        this(resolver, indexer, new RuleEvaluationEngine(indexer, new PrecisionNormalizer()), overlayRuleRepository);
    }

    public AdjustmentService(RuleBookResolver resolver, RuleIndexer indexer, RuleEvaluationEngine engine) {
        this(resolver, indexer, engine, OverlayRuleRepository.empty());
    }

    public AdjustmentService(RuleBookResolver resolver, RuleIndexer indexer, RuleEvaluationEngine engine,
                             OverlayRuleRepository overlayRuleRepository) {
        this.resolver = Objects.requireNonNull(resolver, "resolver is required");
        this.indexer = Objects.requireNonNull(indexer, "indexer is required");
        this.engine = Objects.requireNonNull(engine, "engine is required");
        this.overlayRuleRepository = Objects.requireNonNull(overlayRuleRepository, "overlayRuleRepository is required");
        this.indexCache = Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(5)).build();
    }

    public AdjustmentCalculationResult calculate(AdjustmentCalculationRequest request) {
        Objects.requireNonNull(request, "request is required");
        if (request.tenantId() == null || request.selector() == null || request.quoteDate() == null) {
            return blocker(request, "NO_RULE_BOOK_CONTEXT", "tenantId, selector, and quoteDate are required for real rule-book execution");
        }
        RuleBookSelector selector = request.selector();
        Instant quoteDate = request.quoteDate();
        return resolver.resolve(request.tenantId(), selector, quoteDate)
            .map(ruleBook -> {
                RuleIndexer.RuleBookIndex index = indexCache.get(new IndexCacheKey(request.tenantId(), selector, quoteDate, ruleBook.ruleBookId(), ruleBook.contentHash()),
                    ignored -> indexer.index(ruleBook));
                AdjustmentCalculationResult baseResult = engine.evaluate(index, FactMap.from(request),
                    request.basePriceDecision().scenarioId(), request.basePriceDecision().basePriceId());
                return appendOverlayAdjustments(request, baseResult);
            })
            .orElseGet(() -> blocker(request, "NO_RULE_BOOK_RESOLVED", "No published adjustment rule book resolved for selector and quote date"));
    }

    private AdjustmentCalculationResult appendOverlayAdjustments(AdjustmentCalculationRequest request,
                                                                 AdjustmentCalculationResult baseResult) {
        OverlayInputs inputs = overlayInputs(request);
        List<OverlayRule> overlays;
        try {
            overlays = overlayRuleRepository.findApplicable(inputs).stream()
                .filter(rule -> rule.type().waterfallPosition() == OverlayPolicyType.WaterfallPosition.LLPA_ADJUSTMENT)
                .toList();
        } catch (IllegalStateException ex) {
            return blocker(request, "OVERLAY_PERSISTENCE_NOT_CONFIGURED", ex.getMessage());
        }
        if (overlays.isEmpty()) {
            return baseResult;
        }
        List<AdjustmentLine> lines = new ArrayList<>(baseResult.adjustments());
        List<String> auditRefs = new ArrayList<>(baseResult.auditRefs());
        BigDecimal overlayTotal = BigDecimal.ZERO;
        for (OverlayRule overlay : overlays) {
            BigDecimal points = overlay.boundedAdjustmentPoints();
            overlayTotal = overlayTotal.add(points);
            String auditRef = "overlay:" + overlay.ruleId() + ":" + overlay.type().waterfallPosition();
            auditRefs.add(auditRef);
            lines.add(new AdjustmentLine(
                overlay.type().name(),
                points.doubleValue(),
                overlay.reasonCode(),
                "overlay-policy",
                overlay.type().waterfallPosition().name(),
                overlay.ruleId().toString(),
                "overlay-rule-book:" + overlay.ruleBookId(),
                true,
                overlay.type().displayName(),
                auditRef,
                List.of(overlay.type().displayName(), overlay.type().waterfallPosition().name())
            ));
        }
        BigDecimal total = BigDecimal.valueOf(baseResult.totalAdjustment()).add(overlayTotal);
        Map<String, Object> totals = new java.util.LinkedHashMap<>(baseResult.totalsByType());
        totals.put("OVERLAY_LLPA_ADJUSTMENT", overlayTotal);
        return new AdjustmentCalculationResult(baseResult.scenarioId(), baseResult.basePriceId(), lines,
            total.doubleValue(), baseResult.referenceDataVersion(), baseResult.calculationMode(), auditRefs,
            baseResult.resultHash() + "|overlays:" + overlays.stream().map(OverlayRule::ruleId).toList(),
            totals, baseResult.blocked());
    }

    private static OverlayInputs overlayInputs(AdjustmentCalculationRequest request) {
        Map<String, Object> facts = request.normalizedFacts();
        RuleBookSelector selector = request.selector();
        return new OverlayInputs(request.tenantId(),
            selector == null ? text(facts, "investor") : selector.investor(),
            selector == null ? text(facts, "channel") : selector.channel(),
            selector == null ? text(facts, "productFamily") : selector.productFamily(),
            text(facts, "loanPurpose"), text(facts, "occupancy"), text(facts, "propertyType"),
            text(facts, "stateCode"), text(facts, "countyCode"), decimal(facts, "loanAmount"),
            bool(facts, "escrowWaived"), text(facts, "armCaps"), text(facts, "buydownType"),
            bool(facts, "highBalance"), bool(facts, "jumbo"), integer(facts, "loanTermMonths"), request.quoteDate());
    }

    private static String text(Map<String, Object> facts, String key) {
        Object value = facts.get(key);
        return value == null ? null : value.toString();
    }

    private static boolean bool(Map<String, Object> facts, String key) {
        Object value = facts.get(key);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return value != null && Boolean.parseBoolean(value.toString());
    }

    private static Integer integer(Map<String, Object> facts, String key) {
        Object value = facts.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? null : Integer.valueOf(value.toString());
    }

    private static BigDecimal decimal(Map<String, Object> facts, String key) {
        Object value = facts.get(key);
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return value == null ? null : new BigDecimal(value.toString());
    }

    public void invalidateRuleBook(UUID tenantId, RuleBookSelector selector) {
        resolver.invalidate(tenantId, selector);
        indexCache.asMap().keySet().removeIf(key -> key.tenantId().equals(tenantId) && key.selector().equals(selector));
    }

    private AdjustmentCalculationResult blocker(AdjustmentCalculationRequest request, String code, String message) {
        AdjustmentLine line = new AdjustmentLine(code, 0.0, message, "rule-book-resolver", "BLOCKING_CONFLICT",
            null, code, false, message, code, List.of(code));
        return new AdjustmentCalculationResult(
            request.basePriceDecision().scenarioId(),
            request.basePriceDecision().basePriceId(),
            List.of(line),
            0.0,
            request.referenceDataVersion() == null ? "unresolved" : request.referenceDataVersion(),
            "real-rule-book",
            List.of(code),
            code,
            Map.of("POINTS_DELTA", BigDecimal.ZERO),
            true
        );
    }

    private record IndexCacheKey(UUID tenantId, RuleBookSelector selector, Instant quoteDate, UUID ruleBookId, String contentHash) {}
}
