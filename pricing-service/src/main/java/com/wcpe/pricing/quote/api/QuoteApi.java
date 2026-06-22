package com.wcpe.pricing.quote.api;

import java.util.ArrayList;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class QuoteApi {
    public static final String REQUIRED_ROLE = "PRICING_LOAN_OFFICER";
    public static final String QUOTE_STATUS_ELIGIBILITY_ONLY = "ELIGIBILITY_ONLY";

    private final ScenarioAdapter scenarioAdapter;
    private final CatalogCandidateAdapter catalogCandidateAdapter;
    private final EligibilityEvaluationAdapter eligibilityEvaluationAdapter;
    private final QuoteRepository quoteRepository;

    public QuoteApi(
            ScenarioAdapter scenarioAdapter,
            CatalogCandidateAdapter catalogCandidateAdapter,
            EligibilityEvaluationAdapter eligibilityEvaluationAdapter,
            QuoteRepository quoteRepository) {
        this.scenarioAdapter = Objects.requireNonNull(scenarioAdapter);
        this.catalogCandidateAdapter = Objects.requireNonNull(catalogCandidateAdapter);
        this.eligibilityEvaluationAdapter = Objects.requireNonNull(eligibilityEvaluationAdapter);
        this.quoteRepository = Objects.requireNonNull(quoteRepository);
    }

    public QuoteResponse createQuote(String tenantId, QuoteHeaders headers, QuoteCreateRequest request) {
        requireTenant(tenantId);
        requireAuthorized(headers);
        validateCreateRequest(tenantId, headers, request);

        ScenarioReference scenario = scenarioAdapter.createScenario(tenantId, request);
        List<CatalogCandidate> candidates = new ArrayList<>(catalogCandidateAdapter.activeCandidates(tenantId, request.channel(), request.loanType()));
        candidates.sort(candidateComparator());

        List<QuoteOption> options = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            CatalogCandidate candidate = candidates.get(index);
            EligibilityEvaluation evaluation = eligibilityEvaluationAdapter.evaluate(tenantId, scenario, candidate);
            int displayOrder = preservedResponseDisplayOrder(candidate, index);
            options.add(new QuoteOption(
                    candidate.productCode(),
                    candidate.investorCode(),
                    candidate.channelCode(),
                    evaluation.eligible() ? "ELIGIBLE" : "INELIGIBLE",
                    displayOrder,
                    evaluation.reason().orElse(null)));
        }

        QuoteResponse response = new QuoteResponse(
                "quote-" + UUID.randomUUID(),
                tenantId,
                scenario.scenarioId(),
                scenario.scenarioVersion(),
                QUOTE_STATUS_ELIGIBILITY_ONLY,
                "audit-" + headers.correlationId(),
                headers.correlationId(),
                List.copyOf(options));
        quoteRepository.save(response);
        return response;
    }

    public QuoteResponse getQuote(String tenantId, String quoteId, QuoteHeaders headers) {
        requireTenant(tenantId);
        requireText(quoteId, "quote_id is required");
        requireAuthorized(headers);
        QuoteResponse quote = quoteRepository.findById(quoteId)
                .orElseThrow(() -> new QuoteNotFoundException("quote not found"));
        if (!tenantId.equals(quote.tenantId())) {
            throw new QuoteAccessDeniedException("quote tenant does not match request tenant");
        }
        return quote;
    }

    private static Comparator<CatalogCandidate> candidateComparator() {
        return Comparator.comparing((CatalogCandidate candidate) -> candidate.displayOrder().orElse(Integer.MAX_VALUE))
                .thenComparing(CatalogCandidate::productCode)
                .thenComparing(CatalogCandidate::investorCode)
                .thenComparing(CatalogCandidate::channelCode);
    }

    private static int preservedResponseDisplayOrder(CatalogCandidate candidate, int index) {
        Optional<Integer> explicitDisplayOrder = candidate.displayOrder();
        return explicitDisplayOrder.orElse(index + 1);
    }

    private static void requireAuthorized(QuoteHeaders headers) {
        if (headers == null || !headers.roles().contains(REQUIRED_ROLE)) {
            throw new QuoteAccessDeniedException("PRICING_LOAN_OFFICER role is required");
        }
    }

    private static void validateCreateRequest(String tenantId, QuoteHeaders headers, QuoteCreateRequest request) {
        if (headers == null) {
            throw new QuoteValidationException("headers are required");
        }
        requireText(headers.actorId(), "X-Actor-Id is required");
        requireText(headers.correlationId(), "X-Correlation-Id is required");
        requireText(headers.idempotencyKey(), "Idempotency-Key is required");
        if (request == null) {
            throw new QuoteValidationException("request is required");
        }
        if (!tenantId.equals(request.tenantId())) {
            throw new QuoteAccessDeniedException("request tenant does not match path tenant");
        }
        requireText(request.borrowerId(), "borrower_id is required");
        requireText(request.ficoBand(), "representative_fico is required");
        requirePositive(request.purchasePrice(), "purchase_price must be positive");
        requirePositive(request.loanAmount(), "loan_amount must be positive");
        requireText(request.propertyState(), "property_state is required");
        requireText(request.propertyZip(), "property_zip is required");
        requireText(request.propertyType(), "property_type is required");
        requireText(request.occupancy(), "occupancy is required");
        requireText(request.channel(), "channel is required");
        requirePositive(request.lockPeriodDays(), "lock_period_days must be positive");
        if (request.units() < 1) {
            throw new QuoteValidationException("units must be positive");
        }
        if (!supportedLoanType(request.loanType())) {
            throw new QuoteValidationException("only conventional and configured government loans are supported");
        }
        if (!"PURCHASE".equals(request.loanPurpose())) {
            throw new QuoteValidationException("only purchase scenarios are supported for REQ-001");
        }
    }

    private static void requireTenant(String tenantId) {
        requireText(tenantId, "tenant_id is required");
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new QuoteValidationException(message);
        }
    }

    private static void requirePositive(long value, String message) {
        if (value <= 0) {
            throw new QuoteValidationException(message);
        }
    }

    private static boolean supportedLoanType(String loanType) {
        return Set.of("CONVENTIONAL", "FHA", "VA", "USDA").contains(loanType);
    }

    public record QuoteHeaders(Set<String> roles, String actorId, String correlationId, String idempotencyKey) {
        public QuoteHeaders {
            roles = roles == null ? Set.of() : Set.copyOf(roles);
        }

        public static QuoteHeaders of(String rolesHeader, String actorId, String correlationId, String idempotencyKey) {
            Set<String> parsedRoles = new HashSet<>();
            if (rolesHeader != null && !rolesHeader.isBlank()) {
                for (String role : rolesHeader.split(",")) {
                    if (!role.isBlank()) {
                        parsedRoles.add(role.trim());
                    }
                }
            }
            return new QuoteHeaders(parsedRoles, actorId, correlationId, idempotencyKey);
        }
    }

    public record QuoteCreateRequest(
            String tenantId,
            String borrowerId,
            String ficoBand,
            long purchasePrice,
            long loanAmount,
            String propertyState,
            String propertyZip,
            String propertyType,
            int units,
            String occupancy,
            String loanType,
            String loanPurpose,
            String channel,
            int lockPeriodDays) {
    }

    public record ScenarioReference(String scenarioId, int scenarioVersion) {
    }

    public record CatalogCandidate(
            String productCode,
            String investorCode,
            String channelCode,
            Optional<Integer> displayOrder) {
        public CatalogCandidate(String productCode, String investorCode, String channelCode, Integer displayOrder) {
            this(productCode, investorCode, channelCode, Optional.ofNullable(displayOrder));
        }
    }

    public record EligibilityEvaluation(boolean eligible, Optional<QuoteReason> reason) {
        public static EligibilityEvaluation eligibleResult() {
            return new EligibilityEvaluation(true, Optional.empty());
        }

        public static EligibilityEvaluation ineligible(QuoteReason reason) {
            return new EligibilityEvaluation(false, Optional.of(Objects.requireNonNull(reason)));
        }
    }

    public record QuoteReason(
            String code,
            String severity,
            String text,
            String remediationHint,
            String actualValue,
            String requiredValue) {
    }

    public record QuoteOption(
            String productCode,
            String investorCode,
            String channelCode,
            String eligibilityStatus,
            int displayOrder,
            QuoteReason reason) {
    }

    public record QuoteResponse(
            String quoteId,
            String tenantId,
            String scenarioId,
            int scenarioVersion,
            String quoteStatus,
            String auditReference,
            String correlationId,
            List<QuoteOption> options) {
        public QuoteResponse {
            options = List.copyOf(options);
        }
    }

    public interface ScenarioAdapter {
        ScenarioReference createScenario(String tenantId, QuoteCreateRequest request);
    }

    public interface CatalogCandidateAdapter {
        default List<CatalogCandidate> activeCandidates(String tenantId, String channel, String loanType) {
            if ("CONVENTIONAL".equals(loanType)) {
                return activeConventionalCandidates(tenantId, channel);
            }
            return activeGovernmentCandidates(tenantId, channel, loanType);
        }

        List<CatalogCandidate> activeConventionalCandidates(String tenantId, String channel);

        default List<CatalogCandidate> activeGovernmentCandidates(String tenantId, String channel, String loanType) {
            return List.of();
        }
    }

    public interface EligibilityEvaluationAdapter {
        EligibilityEvaluation evaluate(String tenantId, ScenarioReference scenario, CatalogCandidate candidate);
    }

    public interface QuoteRepository {
        void save(QuoteResponse response);

        Optional<QuoteResponse> findById(String quoteId);
    }

    public static final class DurableQuoteRepository implements QuoteRepository {
        private final Path storageDirectory;

        public DurableQuoteRepository(Path storageDirectory) {
            this.storageDirectory = Objects.requireNonNull(storageDirectory);
        }

        @Override
        public void save(QuoteResponse response) {
            Objects.requireNonNull(response);
            String quoteId = response.quoteId();
            if (quoteId == null || !quoteId.matches("quote-[0-9a-fA-F-]{36}")) {
                throw new QuotePersistenceException("quote id is invalid");
            }
            try {
                Files.createDirectories(storageDirectory);
                Path normalizedStorageDirectory = storageDirectory.toAbsolutePath().normalize();
                Path quotePath = normalizedStorageDirectory.resolve(quoteId + ".quote").normalize();
                if (!quotePath.startsWith(normalizedStorageDirectory)) {
                    throw new QuotePersistenceException("quote path is invalid");
                }
                Files.writeString(quotePath, serialize(response), StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new QuotePersistenceException("quote persistence failed", exception);
            }
        }

        @Override
        public Optional<QuoteResponse> findById(String quoteId) {
            if (quoteId == null || !quoteId.matches("quote-[0-9a-fA-F-]{36}")) {
                return Optional.empty();
            }
            Path quotePath = storageDirectory.resolve(quoteId + ".quote");
            if (!Files.exists(quotePath)) {
                return Optional.empty();
            }
            try {
                return Optional.of(deserialize(Files.readString(quotePath, StandardCharsets.UTF_8)));
            } catch (IOException exception) {
                throw new QuotePersistenceException("quote retrieval failed", exception);
            }
        }

        private static String serialize(QuoteResponse response) {
            List<String> lines = new ArrayList<>();
            lines.add("quote-v1");
            lines.add(String.join("|",
                    encode(response.quoteId()),
                    encode(response.tenantId()),
                    encode(response.scenarioId()),
                    Integer.toString(response.scenarioVersion()),
                    encode(response.quoteStatus()),
                    encode(response.auditReference()),
                    encode(response.correlationId())));
            lines.add(Integer.toString(response.options().size()));
            for (QuoteOption option : response.options()) {
                QuoteReason reason = option.reason();
                lines.add(String.join("|",
                        encode(option.productCode()),
                        encode(option.investorCode()),
                        encode(option.channelCode()),
                        encode(option.eligibilityStatus()),
                        Integer.toString(option.displayOrder()),
                        encode(reason == null ? null : reason.code()),
                        encode(reason == null ? null : reason.severity()),
                        encode(reason == null ? null : reason.text()),
                        encode(reason == null ? null : reason.remediationHint()),
                        encode(reason == null ? null : reason.actualValue()),
                        encode(reason == null ? null : reason.requiredValue())));
            }
            return String.join(System.lineSeparator(), lines);
        }

        private static QuoteResponse deserialize(String payload) {
            String[] lines = payload.split("\\R");
            if (lines.length < 3 || !"quote-v1".equals(lines[0])) {
                throw new QuotePersistenceException("quote payload is invalid");
            }
            String[] header = lines[1].split("\\|", -1);
            int optionCount = Integer.parseInt(lines[2]);
            List<QuoteOption> options = new ArrayList<>();
            for (int index = 0; index < optionCount; index++) {
                String[] option = lines[index + 3].split("\\|", -1);
                QuoteReason reason = decode(option[5]) == null ? null : new QuoteReason(
                        decode(option[5]),
                        decode(option[6]),
                        decode(option[7]),
                        decode(option[8]),
                        decode(option[9]),
                        decode(option[10]));
                options.add(new QuoteOption(
                        decode(option[0]),
                        decode(option[1]),
                        decode(option[2]),
                        decode(option[3]),
                        Integer.parseInt(option[4]),
                        reason));
            }
            return new QuoteResponse(
                    decode(header[0]),
                    decode(header[1]),
                    decode(header[2]),
                    Integer.parseInt(header[3]),
                    decode(header[4]),
                    decode(header[5]),
                    decode(header[6]),
                    options);
        }

        private static String encode(String value) {
            if (value == null) {
                return "~";
            }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
        }

        private static String decode(String value) {
            if ("~".equals(value)) {
                return null;
            }
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        }
    }

    public static class QuoteValidationException extends RuntimeException {
        public QuoteValidationException(String message) {
            super(message);
        }
    }

    public static class QuoteAccessDeniedException extends RuntimeException {
        public QuoteAccessDeniedException(String message) {
            super(message);
        }
    }

    public static class QuoteNotFoundException extends RuntimeException {
        public QuoteNotFoundException(String message) {
            super(message);
        }
    }

    public static class QuotePersistenceException extends RuntimeException {
        public QuotePersistenceException(String message) {
            super(message);
        }

        public QuotePersistenceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
