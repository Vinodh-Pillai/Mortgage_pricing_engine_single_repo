package com.wcpe.quote;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class QuoteApplicationService {
    private final QuoteRepository repository;
    private final QuoteDependencies dependencies;
    private final QuoteCache cache;
    private final BestExecutionRanker ranker;
    private final QuotePresentationModelBuilder presentationModelBuilder = new QuotePresentationModelBuilder();
    private final Clock clock;
    private final List<OutboxEvent> outboxEvents = new ArrayList<>();
    private final List<AuditEntry> auditEntries = new ArrayList<>();
    private final Map<String, QuoteComparisonExport> comparisonExportsByIdempotencyKey = new LinkedHashMap<>();

    public QuoteApplicationService(
        QuoteRepository repository,
        QuoteDependencies dependencies,
        QuoteCache cache,
        BestExecutionRanker ranker,
        Clock clock
    ) {
        this.repository = repository;
        this.dependencies = dependencies;
        this.cache = cache;
        this.ranker = ranker;
        this.clock = clock;
    }

    public Quote createQuote(QuoteCreateRequest request) {
        validate(request);
        return repository.findByIdempotencyKey(request.tenantId(), request.idempotencyKey())
            .map(existing -> sameRequestOrConflict(existing, request))
            .orElseGet(() -> createNew(request));
    }

    public Quote getQuote(UUID tenantId, UUID quoteId) {
        Quote stored = repository.findById(tenantId, quoteId)
            .orElseThrow(() -> new QuoteCreateException("NOT_FOUND", "Quote not found"));
        Quote current = cache.get(tenantId, quoteId, stored.version()).orElse(stored);
        return expireIfNeeded(current);
    }

    public RankingPreviewResponse previewRanking(RankingPreviewRequest request) {
        validatePreview(request);
        RankingPolicyRef policyRef = request.policyRef();
        RankingPolicy previewPolicy = new RankingPolicy(
            policyRef.policyId(),
            policyRef.policyVersion(),
            java.time.Duration.ofHours(1),
            Map.of(),
            Map.of(),
            Map.of(),
            policyRef
        );
        Instant expiresAt = clock.instant().plus(previewPolicy.quoteTtl());
        List<QuoteOption> options = ranker.rank(request.candidates(), previewPolicy, expiresAt);
        String replayHash = ReplayHash.sha256(Map.of(
            "tenantId", request.tenantId(),
            "policyRef", policyRef.policyId() + ":" + policyRef.policyVersion(),
            "options", options.stream().map(option -> option.rank() + ":" + option.productId() + ":" + option.rankScore()).toList()
        ).toString());
        return new RankingPreviewResponse(
            request.tenantId(),
            policyRef.policyId(),
            policyRef.policyVersion(),
            options,
            replayHash,
            request.correlationId()
        );
    }

    public QuoteComparisonResponse compareQuoteOptions(
        UUID tenantId,
        UUID quoteId,
        ComparisonViewConfig config,
        Set<String> allowedFields,
        String actorId,
        String correlationId
    ) {
        validateComparisonRequest(config, actorId, correlationId);
        Quote quote = getQuote(tenantId, quoteId);
        QuoteComparisonResponse response = presentationModelBuilder.build(quote, config, allowedFields, clock);
        outboxEvents.add(event("quote.comparison_viewed.v1", quote));
        auditEntries.add(new AuditEntry(
            "QUOTE_COMPARISON_VIEWED",
            actorId,
            tenantId.toString(),
            correlationId,
            quote.replayHash(),
            clock.instant(),
            Map.of(
                "quoteId", quote.quoteId().toString(),
                "viewId", config.viewId(),
                "viewVersion", config.version(),
                "maskedFieldCount", Integer.toString(response.hiddenFields().size())
            )
        ));
        return response;
    }

    public QuoteComparisonExport exportComparison(
        UUID tenantId,
        UUID quoteId,
        ComparisonViewConfig config,
        Set<String> allowedFields,
        String actorId,
        String correlationId,
        String idempotencyKey,
        String format
    ) {
        validateComparisonRequest(config, actorId, correlationId);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new QuoteCreateException("IDEMPOTENCY_KEY_REQUIRED", "Comparison export requires an idempotency key");
        }
        String key = tenantId + ":" + idempotencyKey;
        QuoteComparisonExport existing = comparisonExportsByIdempotencyKey.get(key);
        if (existing != null) {
            if (!existing.quoteId().equals(quoteId) || !existing.viewId().equals(config.viewId()) || !existing.viewVersion().equals(config.version())) {
                throw new QuoteCreateException("IDEMPOTENCY_CONFLICT", "Comparison export idempotency key was used with different input");
            }
            return existing;
        }

        Quote quote = getQuote(tenantId, quoteId);
        QuoteComparisonResponse comparison = presentationModelBuilder.build(quote, config, allowedFields, clock);
        UUID exportId = UUID.nameUUIDFromBytes((tenantId + ":" + idempotencyKey).getBytes());
        QuoteComparisonExport export = new QuoteComparisonExport(
            tenantId,
            exportId,
            quoteId,
            config.viewId(),
            config.version(),
            comparison.rows().stream().map(row -> UUID.fromString(row.get("optionId").toString())).toList(),
            format == null || format.isBlank() ? "json" : format,
            config.redactionProfile(),
            "audit-safe-export:" + exportId,
            actorId,
            clock.instant(),
            idempotencyKey,
            "audit:" + correlationId
        );
        comparisonExportsByIdempotencyKey.put(key, export);
        outboxEvents.add(event("quote.comparison_exported.v1", quote));
        auditEntries.add(new AuditEntry(
            "QUOTE_COMPARISON_EXPORTED",
            actorId,
            tenantId.toString(),
            correlationId,
            quote.replayHash(),
            export.createdAt(),
            Map.of(
                "quoteId", quote.quoteId().toString(),
                "exportId", export.exportId().toString(),
                "viewId", config.viewId(),
                "viewVersion", config.version(),
                "redactionProfile", export.redactionProfile()
            )
        ));
        return export;
    }

    public QuoteExplanationResponse explainQuoteOption(
        UUID tenantId,
        UUID quoteId,
        UUID optionId,
        Set<String> allowedFields,
        String actorId,
        String correlationId
    ) {
        validateExplanationRequest(optionId, actorId, correlationId);
        Quote quote = getQuote(tenantId, quoteId);
        QuoteOption option = quote.options().stream()
            .filter(candidate -> candidate.optionId().equals(optionId))
            .findFirst()
            .orElseThrow(() -> new QuoteCreateException("NOT_FOUND", "Quote option not found"));
        PriceWaterfall waterfall = option.waterfall();
        Set<String> safeAllowedFields = allowedFields == null ? Set.of() : allowedFields;
        List<PriceWaterfall.WaterfallSection> sections = maskedSections(waterfall, safeAllowedFields);
        List<String> hiddenFields = sections.stream()
            .flatMap(section -> section.lines().stream())
            .filter(line -> "MASKED".equals(line.visibility()))
            .map(PriceWaterfall.WaterfallLine::lineId)
            .toList();
        String auditRef = "audit:" + correlationId + ":" + optionId;

        outboxEvents.add(event("quote.explanation_viewed.v1", quote));
        auditEntries.add(new AuditEntry(
            "QUOTE_WATERFALL_VIEWED",
            actorId,
            tenantId.toString(),
            correlationId,
            quote.replayHash(),
            clock.instant(),
            Map.of(
                "quoteId", quote.quoteId().toString(),
                "optionId", option.optionId().toString(),
                "maskedFieldCount", Integer.toString(hiddenFields.size()),
                "sourceRefs", waterfall.upstreamRefs().toString()
            )
        ));

        return new QuoteExplanationResponse(
            quote.quoteId(),
            option.optionId(),
            sections,
            option.finalPriceBps(),
            waterfall.roundingTrace(),
            waterfall.upstreamRefs(),
            hiddenFields,
            option.warnings(),
            auditRef
        );
    }

    public List<OutboxEvent> outboxEvents() {
        return List.copyOf(outboxEvents);
    }

    public List<AuditEntry> auditEntries() {
        return List.copyOf(auditEntries);
    }

    private Quote createNew(QuoteCreateRequest request) {
        RankingPolicy policy = dependencies.rankingPolicyFor(request)
            .orElseThrow(() -> new QuoteCreateException("NO_ACTIVE_RANKING_POLICY", "Missing effective-dated ranking policy"));
        Instant now = clock.instant();
        Instant expiresAt = now.plus(policy.quoteTtl());
        List<QuoteOption> options = ranker.rank(dependencies.candidatesFor(request), policy, expiresAt);
        QuoteStatus status = options.isEmpty() ? QuoteStatus.NO_OPTIONS : QuoteStatus.READY;
        QuoteInputVersionSet versionSet = new QuoteInputVersionSet(
            request.scenarioVersion(),
            dependencies.eligibilityVersion(),
            dependencies.pricingVersion(),
            dependencies.adjustmentVersion(),
            dependencies.marginVersion(),
            policy.policyVersion()
        );
        String replayHash = ReplayHash.sha256(normalizedReplayInput(request, versionSet, policy, options));
        Quote quote = new Quote(
            request.tenantId(),
            UUID.nameUUIDFromBytes((request.tenantId() + ":" + request.idempotencyKey()).getBytes()),
            request.scenarioId(),
            request.scenarioVersion(),
            status,
            policy.policyId(),
            policy.policyVersion(),
            versionSet,
            options,
            expiresAt,
            "audit:" + request.correlationId(),
            replayHash,
            request.idempotencyKey(),
            request.actorId(),
            now,
            request.correlationId(),
            1
        );
        repository.save(quote);
        cache.put(quote);
        recordAuditAndEvents(quote);
        return quote;
    }

    private Quote sameRequestOrConflict(Quote existing, QuoteCreateRequest request) {
        if (!existing.scenarioId().equals(request.scenarioId()) || existing.scenarioVersion() != request.scenarioVersion()) {
            throw new QuoteCreateException("DUPLICATE_IDEMPOTENCY_CONFLICT", "Idempotency key was used with different quote input");
        }
        return existing;
    }

    private Quote expireIfNeeded(Quote quote) {
        if (!canExpire(quote.status()) || clock.instant().isBefore(quote.expiresAt())) {
            return quote;
        }
        Quote expired = new Quote(
            quote.tenantId(),
            quote.quoteId(),
            quote.scenarioId(),
            quote.scenarioVersion(),
            QuoteStatus.EXPIRED,
            quote.rankingPolicyId(),
            quote.rankingPolicyVersion(),
            quote.inputVersionSet(),
            quote.options(),
            quote.expiresAt(),
            quote.auditRef(),
            quote.replayHash(),
            quote.idempotencyKey(),
            quote.createdBy(),
            quote.createdAt(),
            quote.correlationId(),
            quote.version() + 1
        );
        repository.save(expired);
        cache.put(expired);
        outboxEvents.add(event("quote.expired.v1", expired));
        auditEntries.add(new AuditEntry(
            "QUOTE_EXPIRED",
            expired.createdBy(),
            expired.tenantId().toString(),
            expired.correlationId(),
            expired.replayHash(),
            clock.instant(),
            Map.of(
                "quoteId", expired.quoteId().toString(),
                "status", expired.status().name(),
                "expiresAt", expired.expiresAt().toString(),
                "rankingPolicyVersion", expired.rankingPolicyVersion()
            )
        ));
        return expired;
    }

    private static boolean canExpire(QuoteStatus status) {
        return status == QuoteStatus.READY || status == QuoteStatus.NO_OPTIONS;
    }

    private static void validate(QuoteCreateRequest request) {
        if (request.tenantId() == null || request.scenarioId() == null || request.actorId() == null || request.actorId().isBlank()
            || request.idempotencyKey() == null || request.idempotencyKey().isBlank()) {
            throw new QuoteCreateException("QUOTE_VALIDATION_FAILED", "tenantId, scenarioId, actorId, and idempotencyKey are required");
        }
        if (request.scenarioVersion() < 1 || request.requestedLockPeriods().isEmpty()) {
            throw new QuoteCreateException("QUOTE_VALIDATION_FAILED", "scenarioVersion and requested lock periods are required");
        }
    }

    private static void validatePreview(RankingPreviewRequest request) {
        if (request == null || request.tenantId() == null || request.actorId() == null || request.actorId().isBlank()
            || request.correlationId() == null || request.correlationId().isBlank()) {
            throw new QuoteCreateException("QUOTE_VALIDATION_FAILED", "tenantId, actorId, and correlationId are required");
        }
        if (request.policyRef() == null || request.policyRef().criteria().isEmpty()) {
            throw new QuoteCreateException("POLICY_NOT_SATISFIED", "Ranking preview requires configured policy criteria");
        }
        if (request.candidates() == null || request.candidates().isEmpty()) {
            throw new QuoteCreateException("QUOTE_VALIDATION_FAILED", "Ranking preview requires candidate options");
        }
    }

    private static void validateComparisonRequest(ComparisonViewConfig config, String actorId, String correlationId) {
        if (config == null) {
            throw new QuoteCreateException("POLICY_NOT_SATISFIED", "Comparison view configuration is required");
        }
        if (actorId == null || actorId.isBlank() || correlationId == null || correlationId.isBlank()) {
            throw new QuoteCreateException("QUOTE_VALIDATION_FAILED", "actorId and correlationId are required");
        }
    }

    private static void validateExplanationRequest(UUID optionId, String actorId, String correlationId) {
        if (optionId == null) {
            throw new QuoteCreateException("QUOTE_VALIDATION_FAILED", "optionId is required");
        }
        if (actorId == null || actorId.isBlank() || correlationId == null || correlationId.isBlank()) {
            throw new QuoteCreateException("QUOTE_VALIDATION_FAILED", "actorId and correlationId are required");
        }
    }

    private static List<PriceWaterfall.WaterfallSection> maskedSections(PriceWaterfall waterfall, Set<String> allowedFields) {
        return waterfall.sections().stream()
            .map(section -> new PriceWaterfall.WaterfallSection(
                section.sectionId(),
                section.label(),
                section.displayOrder(),
                section.lines().stream().map(line -> line.maskedFor(allowedFields)).toList()
            ))
            .toList();
    }

    private static String normalizedReplayInput(
        QuoteCreateRequest request,
        QuoteInputVersionSet versionSet,
        RankingPolicy policy,
        List<QuoteOption> options
    ) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("tenantId", request.tenantId());
        values.put("scenarioId", request.scenarioId());
        values.put("scenarioVersion", request.scenarioVersion());
        values.put("lockPeriods", request.requestedLockPeriods());
        values.put("versions", versionSet.asMap());
        values.put("rankingPolicy", policy.policyId() + ":" + policy.policyVersion());
        values.put("options", options.stream().map(option -> option.rank() + ":" + option.productId() + ":" + option.finalPriceBps()).toList());
        return values.toString();
    }

    private void recordAuditAndEvents(Quote quote) {
        outboxEvents.add(event("quote.created.v1", quote));
        outboxEvents.add(event(quote.status() == QuoteStatus.NO_OPTIONS ? "quote.no_options.v1" : "quote.ready.v1", quote));
        outboxEvents.add(bestExecutionRankedEvent(quote));
        auditEntries.add(new AuditEntry(
            "QUOTE_CREATE_REQUESTED",
            quote.createdBy(),
            quote.tenantId().toString(),
            quote.correlationId(),
            quote.replayHash(),
            quote.createdAt(),
            Map.of("status", "REQUESTED", "scenarioId", quote.scenarioId().toString())
        ));
        auditEntries.add(new AuditEntry(
            quote.status() == QuoteStatus.NO_OPTIONS ? "QUOTE_NO_OPTIONS" : "QUOTE_ORCHESTRATION_COMPLETED",
            quote.createdBy(),
            quote.tenantId().toString(),
            quote.correlationId(),
            quote.replayHash(),
            quote.createdAt(),
            Map.of("status", quote.status().name(), "quoteId", quote.quoteId().toString())
        ));
        auditEntries.add(new AuditEntry(
            "BEST_EXECUTION_POLICY_APPLIED",
            quote.createdBy(),
            quote.tenantId().toString(),
            quote.correlationId(),
            quote.replayHash(),
            quote.createdAt(),
            Map.of(
                "quoteId", quote.quoteId().toString(),
                "policyId", quote.rankingPolicyId(),
                "policyVersion", quote.rankingPolicyVersion(),
                "rankedOptionCount", Integer.toString(quote.options().size())
            )
        ));
        if (quote.options().stream().anyMatch(option -> option.tieBreakerTrace() != null && !option.tieBreakerTrace().isBlank())) {
            auditEntries.add(new AuditEntry(
                "BEST_EXECUTION_TIE_BREAKER_APPLIED",
                quote.createdBy(),
                quote.tenantId().toString(),
                quote.correlationId(),
                quote.replayHash(),
                quote.createdAt(),
                Map.of(
                    "quoteId", quote.quoteId().toString(),
                    "policyVersion", quote.rankingPolicyVersion(),
                    "trace", quote.options().stream().map(QuoteOption::tieBreakerTrace).distinct().toList().toString()
                )
            ));
        }
    }

    private static OutboxEvent bestExecutionRankedEvent(Quote quote) {
        return new OutboxEvent(
            "best_execution.ranked.v1",
            "1",
            quote.tenantId() + ":" + quote.quoteId(),
            quote.createdAt(),
            Map.of(
                "tenantId", quote.tenantId().toString(),
                "eventType", "best_execution.ranked.v1",
                "eventVersion", "1",
                "sourceService", "quote-service",
                "actorId", quote.createdBy(),
                "correlationId", quote.correlationId(),
                "idempotencyKey", quote.idempotencyKey()
            ),
            Map.of(
                "quoteId", quote.quoteId().toString(),
                "tenantId", quote.tenantId().toString(),
                "policyRef", quote.rankingPolicyId() + ":" + quote.rankingPolicyVersion(),
                "rankedOptionIds", quote.options().stream().map(option -> option.optionId().toString()).toList().toString(),
                "scores", quote.options().stream().map(option -> option.rank() + ":" + option.rankScore()).toList().toString(),
                "warnings", quote.options().stream().flatMap(option -> option.warnings().stream()).distinct().toList().toString(),
                "replayHash", quote.replayHash()
            )
        );
    }

    private static OutboxEvent event(String eventType, Quote quote) {
        return new OutboxEvent(
            eventType,
            "1",
            quote.tenantId() + ":" + quote.quoteId(),
            quote.createdAt(),
            Map.of(
                "tenantId", quote.tenantId().toString(),
                "eventType", eventType,
                "eventVersion", "1",
                "sourceService", "quote-service",
                "actorId", quote.createdBy(),
                "correlationId", quote.correlationId(),
                "idempotencyKey", quote.idempotencyKey()
            ),
            Map.of(
                "quoteId", quote.quoteId().toString(),
                "tenantId", quote.tenantId().toString(),
                "status", quote.status().name(),
                "version", Integer.toString(quote.version()),
                "replayHash", quote.replayHash()
            )
        );
    }
}
