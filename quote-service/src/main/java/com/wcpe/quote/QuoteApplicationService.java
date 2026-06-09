package com.wcpe.quote;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class QuoteApplicationService {
    private final QuoteRepository repository;
    private final QuoteJobRepository jobRepository;
    private final QuoteSnapshotRepository snapshotRepository;
    private final QuoteDependencies dependencies;
    private final QuoteCache cache;
    private final BestExecutionRanker ranker;
    private final QuotePresentationModelBuilder presentationModelBuilder = new QuotePresentationModelBuilder();
    private final Clock clock;
    private final List<OutboxEvent> outboxEvents = new ArrayList<>();
    private final List<AuditEntry> auditEntries = new ArrayList<>();
    private final Map<String, QuoteComparisonExport> comparisonExportsByIdempotencyKey = new LinkedHashMap<>();
    private final Map<String, QuoteSelection> selectionsByIdempotencyKey = new LinkedHashMap<>();
    private final Map<String, QuoteSelection> activeSelectionsByQuote = new LinkedHashMap<>();

    public QuoteApplicationService(
        QuoteRepository repository,
        QuoteDependencies dependencies,
        QuoteCache cache,
        BestExecutionRanker ranker,
        Clock clock
    ) {
        this(repository, new InMemoryQuoteJobRepository(), new InMemoryQuoteSnapshotRepository(), dependencies, cache, ranker, clock);
    }

    public QuoteApplicationService(
        QuoteRepository repository,
        QuoteJobRepository jobRepository,
        QuoteSnapshotRepository snapshotRepository,
        QuoteDependencies dependencies,
        QuoteCache cache,
        BestExecutionRanker ranker,
        Clock clock
    ) {
        this.repository = repository;
        this.jobRepository = jobRepository;
        this.snapshotRepository = snapshotRepository;
        this.dependencies = dependencies;
        this.cache = cache;
        this.ranker = ranker;
        this.clock = clock;
    }

    public QuoteApplicationService(
        QuoteRepository repository,
        QuoteSnapshotRepository snapshotRepository,
        QuoteDependencies dependencies,
        QuoteCache cache,
        BestExecutionRanker ranker,
        Clock clock
    ) {
        this(repository, new InMemoryQuoteJobRepository(), snapshotRepository, dependencies, cache, ranker, clock);
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

    public List<OutboxEvent> quoteEvents(UUID tenantId, UUID quoteId) {
        if (tenantId == null || quoteId == null) {
            throw new QuoteCreateException("QUOTE_VALIDATION_FAILED", "tenantId and quoteId are required");
        }
        repository.findById(tenantId, quoteId)
            .orElseThrow(() -> new QuoteCreateException("NOT_FOUND", "Quote not found"));
        return outboxEvents.stream()
            .filter(event -> tenantId.toString().equals(event.tenantId()))
            .filter(event -> quoteId.toString().equals(event.aggregateId()))
            .sorted(Comparator.comparing(OutboxEvent::occurredAt).thenComparing(OutboxEvent::eventType))
            .toList();
    }

    public QuoteEventReplayResult replayQuoteEvent(UUID tenantId, String eventId, String actorId, String correlationId, String reasonForAccess) {
        validateReplayRequest(tenantId, eventId, actorId, correlationId, reasonForAccess);
        OutboxEvent original = outboxEvents.stream()
            .filter(event -> tenantId.toString().equals(event.tenantId()))
            .filter(event -> eventId.equals(event.eventId()))
            .findFirst()
            .orElseThrow(() -> new QuoteCreateException("NOT_FOUND", "Quote event not found"));
        String deliveryId = UUID.nameUUIDFromBytes((tenantId + ":" + eventId + ":" + correlationId + ":replay").getBytes()).toString();
        Map<String, String> replayHeaders = new LinkedHashMap<>(original.envelopeHeaders());
        replayHeaders.put("eventId", UUID.nameUUIDFromBytes((deliveryId + ":" + original.eventType()).getBytes()).toString());
        replayHeaders.put("causationId", original.eventId());
        replayHeaders.put("correlationId", correlationId);
        replayHeaders.put("actorId", actorId);
        replayHeaders.put("replay", "true");
        replayHeaders.put("originalEventId", original.eventId());
        replayHeaders.put("deliveryId", deliveryId);
        replayHeaders.put("reasonForAccess", reasonForAccess);
        OutboxEvent replayed = new OutboxEvent(
            original.eventType(),
            original.eventVersion(),
            original.key(),
            clock.instant(),
            replayHeaders,
            original.payload()
        );
        outboxEvents.add(replayed);
        AuditEntry auditEntry = new AuditEntry(
            "QUOTE_EVENT_REPLAY_REQUESTED",
            actorId,
            tenantId.toString(),
            correlationId,
            ReplayHash.sha256(original.payload().toString()),
            clock.instant(),
            Map.of(
                "originalEventId", original.eventId(),
                "deliveryId", deliveryId,
                "eventType", original.eventType(),
                "reasonForAccess", reasonForAccess
            )
        );
        auditEntries.add(auditEntry);
        return new QuoteEventReplayResult(original.eventId(), deliveryId, replayed, auditEntry);
    }

    public QuoteJob startQuoteJob(QuoteJobStartRequest request) {
        validateJobStart(request);
        String requestHash = quoteJobRequestHash(request);
        return jobRepository.findByIdempotencyKey(request.tenantId(), request.idempotencyKey())
            .map(existing -> sameJobRequestOrConflict(existing, requestHash))
            .orElseGet(() -> createJob(request, requestHash));
    }

    public QuoteJob getQuoteJob(UUID tenantId, UUID jobId) {
        return jobRepository.findById(tenantId, jobId)
            .orElseThrow(() -> new QuoteCreateException("NOT_FOUND", "Quote job not found"));
    }

    public QuoteJob claimQuoteJob(UUID tenantId, UUID jobId, String actorId, String correlationId) {
        validateJobActor(actorId, correlationId);
        QuoteJob running = getQuoteJob(tenantId, jobId).running(clock.instant());
        QuoteJob saved = jobRepository.save(running);
        recordJobAuditAndEvent("QUOTE_JOB_RUNNING", "quote_job.running.v1", saved, actorId, correlationId);
        return saved;
    }

    public QuoteJob completeQuoteJob(UUID tenantId, UUID jobId, String actorId, String correlationId) {
        validateJobActor(actorId, correlationId);
        QuoteJob job = getQuoteJob(tenantId, jobId);
        Quote quote = createQuote(quoteRequestFromJob(job));
        QuoteJob completed = jobRepository.save(job.completed(quote, clock.instant()));
        recordJobAuditAndEvent("QUOTE_JOB_COMPLETED", "quote_job.completed.v1", completed, actorId, correlationId);
        return completed;
    }

    public QuoteJob failQuoteJob(UUID tenantId, UUID jobId, String failureCode, String failureDetail, String actorId, String correlationId) {
        validateJobActor(actorId, correlationId);
        if (failureCode == null || failureCode.isBlank()) {
            throw new QuoteCreateException("QUOTE_VALIDATION_FAILED", "failureCode is required");
        }
        QuoteJob failed = jobRepository.save(getQuoteJob(tenantId, jobId).failed(failureCode, failureDetail, clock.instant()));
        recordJobAuditAndEvent("QUOTE_JOB_FAILED", "quote_job.failed.v1", failed, actorId, correlationId);
        return failed;
    }

    public QuoteJob cancelQuoteJob(UUID tenantId, UUID jobId, String actorId, String correlationId) {
        validateJobActor(actorId, correlationId);
        QuoteJob cancelled = jobRepository.save(getQuoteJob(tenantId, jobId).cancelled(clock.instant()));
        recordJobAuditAndEvent("QUOTE_JOB_CANCELLED", "quote_job.cancelled.v1", cancelled, actorId, correlationId);
        return cancelled;
    }

    public QuoteSnapshot getQuoteSnapshot(UUID tenantId, UUID quoteId, String actorId, String correlationId) {
        validateSnapshotAccess(actorId, correlationId);
        QuoteSnapshot snapshot = snapshotRepository.findByQuoteId(tenantId, quoteId)
            .orElseThrow(() -> new QuoteCreateException("NOT_FOUND", "Quote snapshot not found"));
        auditEntries.add(new AuditEntry(
            "QUOTE_SNAPSHOT_VIEWED",
            actorId,
            tenantId.toString(),
            correlationId,
            snapshot.replayHash(),
            clock.instant(),
            Map.of("quoteId", quoteId.toString(), "snapshotId", snapshot.snapshotId().toString())
        ));
        return snapshot;
    }

    public QuoteSnapshotExport exportQuoteSnapshot(
        UUID tenantId,
        UUID quoteId,
        String redactionProfile,
        boolean nonRedacted,
        String actorId,
        String correlationId
    ) {
        validateSnapshotAccess(actorId, correlationId);
        if (redactionProfile == null || redactionProfile.isBlank()) {
            throw new QuoteCreateException("POLICY_NOT_SATISFIED", "Snapshot export requires configured redaction profile");
        }
        QuoteSnapshot snapshot = snapshotRepository.findByQuoteId(tenantId, quoteId)
            .orElseThrow(() -> new QuoteCreateException("NOT_FOUND", "Quote snapshot not found"));
        if (!clock.instant().isBefore(snapshot.retentionUntil())) {
            throw new QuoteCreateException("POLICY_NOT_SATISFIED", "Snapshot export is outside the configured retention window");
        }
        QuoteSnapshotExport export = new QuoteSnapshotExport(
            tenantId,
            quoteId,
            snapshot.snapshotId(),
            snapshot.replayHash(),
            redactionProfile,
            nonRedacted,
            snapshot.evidenceRefs(),
            "audit:" + correlationId + ":" + snapshot.snapshotId(),
            clock.instant()
        );
        outboxEvents.add(new OutboxEvent(
            "quote.snapshot_exported.v1",
            "1",
            tenantId + ":" + quoteId,
            export.exportedAt(),
            Map.of(
                "tenantId", tenantId.toString(),
                "eventType", "quote.snapshot_exported.v1",
                "eventVersion", "1",
                "sourceService", "quote-service",
                "actorId", actorId,
                "correlationId", correlationId
            ),
            Map.of(
                "quoteId", quoteId.toString(),
                "snapshotId", snapshot.snapshotId().toString(),
                "replayHash", snapshot.replayHash(),
                "redactionProfile", redactionProfile
            )
        ));
        auditEntries.add(new AuditEntry(
            "QUOTE_SNAPSHOT_EXPORTED",
            actorId,
            tenantId.toString(),
            correlationId,
            snapshot.replayHash(),
            export.exportedAt(),
            Map.of(
                "quoteId", quoteId.toString(),
                "snapshotId", snapshot.snapshotId().toString(),
                "redactionProfile", redactionProfile,
                "nonRedacted", Boolean.toString(nonRedacted)
            )
        ));
        return export;
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

    public QuoteSelection selectQuoteOption(SelectQuoteOptionCommand command) {
        validateSelectionCommand(command);
        String idempotencyScope = command.tenantId() + ":" + command.idempotencyKey();
        QuoteSelection existing = selectionsByIdempotencyKey.get(idempotencyScope);
        if (existing != null) {
            if (!existing.quoteId().equals(command.quoteId()) || !existing.optionId().equals(command.optionId())) {
                throw new QuoteCreateException("IDEMPOTENCY_CONFLICT", "Quote selection idempotency key was used with different input");
            }
            return existing;
        }

        Quote quote = getQuote(command.tenantId(), command.quoteId());
        QuoteOption option = quote.options().stream()
            .filter(candidate -> candidate.optionId().equals(command.optionId()))
            .findFirst()
            .orElseThrow(() -> new QuoteCreateException("NOT_FOUND", "Quote option not found"));

        QuoteSelection selection = evaluateSelection(command, quote, option);
        if (selection.status() == QuoteSelectionStatus.SELECTED) {
            String quoteScope = command.tenantId() + ":" + command.quoteId();
            QuoteSelection previous = activeSelectionsByQuote.get(quoteScope);
            if (previous != null && !command.policy().allowReselection()) {
                throw new QuoteCreateException("POLICY_NOT_SATISFIED", "Quote already has an active selection");
            }
            if (previous != null) {
                activeSelectionsByQuote.put(quoteScope, supersede(previous, command.correlationId()));
            }
            activeSelectionsByQuote.put(quoteScope, selection);
            cache.put(quote);
        }
        selectionsByIdempotencyKey.put(idempotencyScope, selection);
        recordSelectionAuditAndEvent(selection, quote, option, command.actorId());
        return selection;
    }

    public List<OutboxEvent> outboxEvents() {
        return List.copyOf(outboxEvents);
    }

    public List<AuditEntry> auditEntries() {
        return List.copyOf(auditEntries);
    }

    private static void validateSelectionCommand(SelectQuoteOptionCommand command) {
        if (command == null || command.tenantId() == null || command.quoteId() == null || command.optionId() == null
            || command.actorId() == null || command.actorId().isBlank() || command.idempotencyKey() == null || command.idempotencyKey().isBlank()
            || command.correlationId() == null || command.correlationId().isBlank()) {
            throw new QuoteCreateException("QUOTE_VALIDATION_FAILED", "tenantId, quoteId, optionId, actorId, idempotencyKey, and correlationId are required");
        }
        if (command.policy() == null || command.policy().policyId() == null || command.policy().policyId().isBlank()
            || command.policy().policyVersion() == null || command.policy().policyVersion().isBlank()) {
            throw new QuoteCreateException("POLICY_NOT_SATISFIED", "Quote selection requires configured selection policy");
        }
    }

    private QuoteSelection evaluateSelection(SelectQuoteOptionCommand command, Quote quote, QuoteOption option) {
        if (quote.status() != QuoteStatus.READY || !clock.instant().isBefore(quote.expiresAt())) {
            return rejectedSelection(command, quote, option, "QUOTE_NOT_READY_OR_EXPIRED");
        }
        if (!command.policy().permissionGranted()) {
            return rejectedSelection(command, quote, option, "PERMISSION_DENIED");
        }
        if (!command.acknowledgements().containsAll(command.policy().requiredAcknowledgements())) {
            return rejectedSelection(command, quote, option, "ACKNOWLEDGEMENT_REQUIRED");
        }
        if (option.rank() != 1 && !command.policy().allowNonTopRank()) {
            return rejectedSelection(command, quote, option, "NON_TOP_RANK_NOT_ALLOWED");
        }
        if (option.rank() != 1 && (command.nonTopRankReason() == null || command.nonTopRankReason().isBlank())) {
            return rejectedSelection(command, quote, option, "NON_TOP_RANK_REASON_REQUIRED");
        }
        return selection(command, quote, option, QuoteSelectionStatus.SELECTED, "");
    }

    private QuoteSelection rejectedSelection(SelectQuoteOptionCommand command, Quote quote, QuoteOption option, String reason) {
        return selection(command, quote, option, QuoteSelectionStatus.REJECTED, reason);
    }

    private QuoteSelection selection(SelectQuoteOptionCommand command, Quote quote, QuoteOption option, QuoteSelectionStatus status, String rejectedReason) {
        UUID selectionId = UUID.nameUUIDFromBytes((command.tenantId() + ":selection:" + command.idempotencyKey()).getBytes());
        Map<String, String> lineageRefs = new LinkedHashMap<>();
        lineageRefs.put("quoteId", quote.quoteId().toString());
        lineageRefs.put("quoteVersion", Integer.toString(quote.version()));
        lineageRefs.put("scenarioVersion", Integer.toString(quote.scenarioVersion()));
        lineageRefs.put("quoteReplayHash", quote.replayHash());
        lineageRefs.put("rankingPolicyRef", quote.rankingPolicyId() + ":" + quote.rankingPolicyVersion());
        lineageRefs.put("selectionPolicyRef", command.policy().policyId() + ":" + command.policy().policyVersion());
        lineageRefs.put("optionRank", Integer.toString(option.rank()));
        lineageRefs.put("optionRefs", option.upstreamRefs().toString());
        lineageRefs.put("auditRef", quote.auditRef());
        snapshotRepository.findByQuoteId(quote.tenantId(), quote.quoteId())
            .ifPresent(snapshot -> lineageRefs.put("snapshotRef", "snapshot:" + snapshot.snapshotId()));
        return new QuoteSelection(
            command.tenantId(),
            selectionId,
            command.quoteId(),
            command.optionId(),
            status,
            command.policy().policyId(),
            command.policy().policyVersion(),
            command.actorId(),
            clock.instant(),
            command.acknowledgements(),
            "lock-eligibility:pending:" + selectionId,
            rejectedReason,
            command.idempotencyKey(),
            command.correlationId(),
            lineageRefs,
            1
        );
    }

    private static QuoteSelection supersede(QuoteSelection previous, String correlationId) {
        return new QuoteSelection(
            previous.tenantId(), previous.selectionId(), previous.quoteId(), previous.optionId(), QuoteSelectionStatus.SUPERSEDED,
            previous.selectionPolicyId(), previous.selectionPolicyVersion(), previous.selectedBy(), previous.selectedAt(),
            previous.acknowledgements(), previous.lockEligibilityRef(), "RESELECTION_SUPERSEDED", previous.idempotencyKey(),
            correlationId, previous.lineageRefs(), previous.version() + 1
        );
    }

    private void recordSelectionAuditAndEvent(QuoteSelection selection, Quote quote, QuoteOption option, String actorId) {
        auditEntries.add(new AuditEntry(
            "QUOTE_OPTION_SELECTION_ATTEMPTED",
            actorId,
            selection.tenantId().toString(),
            selection.correlationId(),
            quote.replayHash(),
            selection.selectedAt(),
            Map.of("quoteId", selection.quoteId().toString(), "optionId", selection.optionId().toString(), "status", selection.status().name())
        ));
        auditEntries.add(new AuditEntry(
            selection.status() == QuoteSelectionStatus.SELECTED ? "QUOTE_OPTION_SELECTED" : "QUOTE_OPTION_SELECTION_REJECTED",
            actorId,
            selection.tenantId().toString(),
            selection.correlationId(),
            quote.replayHash(),
            selection.selectedAt(),
            Map.of(
                "selectionId", selection.selectionId().toString(),
                "policyRef", selection.selectionPolicyId() + ":" + selection.selectionPolicyVersion(),
                "reason", selection.rejectedReason()
            )
        ));
        if (selection.status() == QuoteSelectionStatus.SELECTED) {
            outboxEvents.add(new OutboxEvent(
                "quote_option.selected.v1",
                "1",
                selection.tenantId() + ":" + selection.selectionId(),
                selection.selectedAt(),
                Map.of(
                    "tenantId", selection.tenantId().toString(),
                    "eventType", "quote_option.selected.v1",
                    "eventVersion", "1",
                    "sourceService", "quote-service",
                    "actorId", actorId,
                    "correlationId", selection.correlationId(),
                    "idempotencyKey", selection.idempotencyKey()
                ),
                Map.of(
                    "selectionId", selection.selectionId().toString(),
                    "tenantId", selection.tenantId().toString(),
                    "quoteId", selection.quoteId().toString(),
                    "optionId", selection.optionId().toString(),
                    "status", selection.status().name(),
                    "optionRank", Integer.toString(option.rank()),
                    "policyVersion", selection.selectionPolicyVersion(),
                    "lockEligibilityRef", selection.lockEligibilityRef(),
                    "lineageRefs", selection.lineageRefs().toString()
                )
            ));
        }
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
        QuoteSnapshot snapshot = snapshotRepository.saveNew(snapshotFrom(quote, request));
        cache.put(quote);
        recordAuditAndEvents(quote, snapshot);
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

    private static void validateJobStart(QuoteJobStartRequest request) {
        if (request == null || request.tenantId() == null || request.scenarioId() == null || request.actorId() == null || request.actorId().isBlank()
            || request.idempotencyKey() == null || request.idempotencyKey().isBlank() || request.correlationId() == null || request.correlationId().isBlank()) {
            throw new QuoteCreateException("QUOTE_VALIDATION_FAILED", "tenantId, scenarioId, actorId, idempotencyKey, and correlationId are required");
        }
        if (!request.preferAsync()) {
            throw new QuoteCreateException("POLICY_NOT_SATISFIED", "Async quote jobs require Prefer: respond-async or configured async threshold");
        }
        if (request.scenarioVersion() < 1 || request.requestedLockPeriods().isEmpty()) {
            throw new QuoteCreateException("QUOTE_VALIDATION_FAILED", "scenarioVersion and requested lock periods are required");
        }
        asyncMaxAttempts(request);
    }

    private static void validateJobActor(String actorId, String correlationId) {
        if (actorId == null || actorId.isBlank() || correlationId == null || correlationId.isBlank()) {
            throw new QuoteCreateException("QUOTE_VALIDATION_FAILED", "actorId and correlationId are required");
        }
    }

    private QuoteJob createJob(QuoteJobStartRequest request, String requestHash) {
        Instant now = clock.instant();
        QuoteJob job = new QuoteJob(
            request.tenantId(),
            UUID.nameUUIDFromBytes((request.tenantId() + ":quote-job:" + request.idempotencyKey()).getBytes()),
            QuoteJobStatus.QUEUED,
            jobRequestPayload(request),
            requestHash,
            null,
            null,
            null,
            Map.of("stage", "queued", "percent", "0"),
            0,
            asyncMaxAttempts(request),
            request.idempotencyKey(),
            request.actorId(),
            now,
            now,
            now.plus(java.time.Duration.ofDays(7)),
            request.correlationId(),
            1
        );
        QuoteJob saved = jobRepository.save(job);
        recordJobAuditAndEvent("QUOTE_JOB_CREATED", "quote_job.created.v1", saved, request.actorId(), request.correlationId());
        return saved;
    }

    private QuoteJob sameJobRequestOrConflict(QuoteJob existing, String requestHash) {
        if (!existing.requestHash().equals(requestHash)) {
            throw new QuoteCreateException("IDEMPOTENCY_CONFLICT", "Quote job idempotency key was used with different input");
        }
        return existing;
    }

    private static String quoteJobRequestHash(QuoteJobStartRequest request) {
        return ReplayHash.sha256(jobRequestPayload(request).toString());
    }

    private static Map<String, String> jobRequestPayload(QuoteJobStartRequest request) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("tenantId", request.tenantId().toString());
        payload.put("scenarioId", request.scenarioId().toString());
        payload.put("scenarioVersion", Integer.toString(request.scenarioVersion()));
        payload.put("requestedLockPeriods", request.requestedLockPeriods().toString());
        payload.put("presentationCurrency", request.presentationCurrency() == null ? "" : request.presentationCurrency());
        payload.put("effectiveDate", request.effectiveDate() == null ? "" : request.effectiveDate().toString());
        payload.put("clientContext", request.clientContext().toString());
        payload.put("maxAttempts", request.clientContext().getOrDefault("asyncMaxAttempts", ""));
        return payload;
    }

    private static int asyncMaxAttempts(QuoteJobStartRequest request) {
        String configured = request.clientContext().get("asyncMaxAttempts");
        if (configured == null || configured.isBlank()) {
            throw new QuoteCreateException("POLICY_NOT_SATISFIED", "Async quote jobs require configured retry policy");
        }
        try {
            int maxAttempts = Integer.parseInt(configured);
            if (maxAttempts < 1) {
                throw new NumberFormatException("maxAttempts must be positive");
            }
            return maxAttempts;
        } catch (NumberFormatException ex) {
            throw new QuoteCreateException("POLICY_NOT_SATISFIED", "Async quote job retry policy is invalid");
        }
    }

    private static QuoteCreateRequest quoteRequestFromJob(QuoteJob job) {
        return new QuoteCreateRequest(
            job.tenantId(),
            UUID.fromString(job.requestPayload().get("scenarioId")),
            Integer.parseInt(job.requestPayload().get("scenarioVersion")),
            parseLockPeriods(job.requestPayload().get("requestedLockPeriods")),
            new QuoteFilters(List.of(), List.of(), List.of(), List.of()),
            job.requestPayload().get("presentationCurrency"),
            Map.of("source", "async-quote-job", "jobId", job.jobId().toString()),
            job.createdBy(),
            job.idempotencyKey(),
            job.correlationId(),
            java.time.LocalDate.parse(job.requestPayload().get("effectiveDate"))
        );
    }

    private static List<Integer> parseLockPeriods(String value) {
        if (value == null || value.length() < 2) {
            return List.of();
        }
        String content = value.substring(1, value.length() - 1).trim();
        if (content.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(content.split(","))
            .map(String::trim)
            .map(Integer::parseInt)
            .toList();
    }

    private void recordJobAuditAndEvent(String auditAction, String eventType, QuoteJob job, String actorId, String correlationId) {
        outboxEvents.add(new OutboxEvent(
            eventType,
            "1",
            job.tenantId() + ":" + job.jobId(),
            job.updatedAt(),
            Map.of(
                "tenantId", job.tenantId().toString(),
                "eventType", eventType,
                "eventVersion", "1",
                "sourceService", "quote-service",
                "actorId", actorId,
                "correlationId", correlationId,
                "idempotencyKey", job.idempotencyKey()
            ),
            Map.of(
                "jobId", job.jobId().toString(),
                "tenantId", job.tenantId().toString(),
                "status", job.status().name(),
                "version", Integer.toString(job.version()),
                "summary", job.progress().toString(),
                "sourceRefs", job.quoteId() == null ? "[]" : "[quote:" + job.quoteId() + "]"
            )
        ));
        auditEntries.add(new AuditEntry(
            auditAction,
            actorId,
            job.tenantId().toString(),
            correlationId,
            job.requestHash(),
            job.updatedAt(),
            Map.of(
                "jobId", job.jobId().toString(),
                "status", job.status().name(),
                "requestHash", job.requestHash()
            )
        ));
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

    private static void validateSnapshotAccess(String actorId, String correlationId) {
        if (actorId == null || actorId.isBlank() || correlationId == null || correlationId.isBlank()) {
            throw new QuoteCreateException("QUOTE_VALIDATION_FAILED", "actorId and correlationId are required");
        }
    }

    private static void validateReplayRequest(UUID tenantId, String eventId, String actorId, String correlationId, String reasonForAccess) {
        if (tenantId == null || eventId == null || eventId.isBlank()) {
            throw new QuoteCreateException("QUOTE_VALIDATION_FAILED", "tenantId and eventId are required");
        }
        validateSnapshotAccess(actorId, correlationId);
        if (reasonForAccess == null || reasonForAccess.isBlank()) {
            throw new QuoteCreateException("POLICY_NOT_SATISFIED", "Quote event replay requires reason-for-access");
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

    private QuoteSnapshot snapshotFrom(Quote quote, QuoteCreateRequest request) {
        Map<String, String> canonicalRequest = new LinkedHashMap<>();
        canonicalRequest.put("tenantId", quote.tenantId().toString());
        canonicalRequest.put("scenarioId", quote.scenarioId().toString());
        canonicalRequest.put("scenarioVersion", Integer.toString(quote.scenarioVersion()));
        canonicalRequest.put("effectiveDate", request.effectiveDate().toString());
        canonicalRequest.put("requestedLockPeriods", request.requestedLockPeriods().toString());
        canonicalRequest.put("clientContext", request.clientContext().toString());

        Map<String, String> canonicalResponse = new LinkedHashMap<>();
        canonicalResponse.put("status", quote.status().name());
        canonicalResponse.put("optionCount", Integer.toString(quote.options().size()));
        canonicalResponse.put("rankedOptions", quote.options().stream().map(option -> option.rank() + ":" + option.optionId()).toList().toString());
        canonicalResponse.put("expiresAt", quote.expiresAt().toString());

        Map<String, String> evidenceRefs = new LinkedHashMap<>();
        evidenceRefs.put("auditRef", quote.auditRef());
        evidenceRefs.put("rankingPolicyRef", quote.rankingPolicyId() + ":" + quote.rankingPolicyVersion());
        evidenceRefs.put("optionRefs", quote.options().stream().map(option -> option.optionId().toString()).toList().toString());

        return new QuoteSnapshot(
            quote.tenantId(),
            UUID.nameUUIDFromBytes((quote.tenantId() + ":" + quote.quoteId() + ":snapshot").getBytes()),
            quote.quoteId(),
            quote.version(),
            "quote-snapshot.v1",
            canonicalRequest,
            canonicalResponse,
            quote.inputVersionSet().asMap(),
            ReplayHash.sha256(canonicalResponse.toString()),
            quote.replayHash(),
            evidenceRefs,
            "",
            quote.createdAt(),
            quote.createdAt().plus(java.time.Duration.ofDays(365L * 7L)),
            quote.auditRef(),
            quote.correlationId()
        );
    }

    private void recordAuditAndEvents(Quote quote, QuoteSnapshot snapshot) {
        outboxEvents.add(event("quote.created.v1", quote));
        outboxEvents.add(event(quote.status() == QuoteStatus.NO_OPTIONS ? "quote.no_options.v1" : "quote.ready.v1", quote));
        outboxEvents.add(bestExecutionRankedEvent(quote));
        outboxEvents.add(snapshotCreatedEvent(snapshot, quote));
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
        auditEntries.add(new AuditEntry(
            "QUOTE_SNAPSHOT_CREATED",
            quote.createdBy(),
            quote.tenantId().toString(),
            quote.correlationId(),
            snapshot.replayHash(),
            snapshot.createdAt(),
            Map.of(
                "quoteId", quote.quoteId().toString(),
                "snapshotId", snapshot.snapshotId().toString(),
                "outputDigest", snapshot.outputDigest(),
                "retentionUntil", snapshot.retentionUntil().toString()
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

    private static OutboxEvent snapshotCreatedEvent(QuoteSnapshot snapshot, Quote quote) {
        return new OutboxEvent(
            "quote.snapshot_created.v1",
            "1",
            quote.tenantId() + ":" + quote.quoteId(),
            snapshot.createdAt(),
            Map.of(
                "tenantId", quote.tenantId().toString(),
                "eventType", "quote.snapshot_created.v1",
                "eventVersion", "1",
                "sourceService", "quote-service",
                "actorId", quote.createdBy(),
                "correlationId", quote.correlationId(),
                "idempotencyKey", quote.idempotencyKey()
            ),
            Map.of(
                "quoteId", quote.quoteId().toString(),
                "tenantId", quote.tenantId().toString(),
                "snapshotId", snapshot.snapshotId().toString(),
                "replayHash", snapshot.replayHash(),
                "outputDigest", snapshot.outputDigest(),
                "retentionUntil", snapshot.retentionUntil().toString()
            )
        );
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
