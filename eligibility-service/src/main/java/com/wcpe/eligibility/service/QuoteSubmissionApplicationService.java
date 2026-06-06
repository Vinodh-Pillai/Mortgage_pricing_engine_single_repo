package com.wcpe.eligibility.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.eligibility.client.CatalogClient;
import com.wcpe.eligibility.domain.hashing.Hashing;
import com.wcpe.eligibility.domain.models.BorrowerProfile;
import com.wcpe.eligibility.domain.models.EligibilityRequest;
import com.wcpe.eligibility.domain.models.EligibilityResult;
import com.wcpe.eligibility.domain.models.LoanProfile;
import com.wcpe.eligibility.domain.models.ProductCandidate;
import com.wcpe.eligibility.domain.models.ProductFamily;
import com.wcpe.eligibility.domain.models.PropertyProfile;
import com.wcpe.eligibility.domain.models.QuoteSubmissionRequest;
import com.wcpe.eligibility.domain.models.QuoteSubmissionResponse;
import com.wcpe.eligibility.domain.models.QuoteType;
import com.wcpe.eligibility.domain.models.ScenarioFacts;
import com.wcpe.eligibility.repository.EligibilityPersistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class QuoteSubmissionApplicationService {
    private static final int SCENARIO_VERSION = 1;

    private final EligibilityApplicationService eligibilityApplicationService;
    private final EligibilityPersistRepository repository;
    private final CatalogClient catalogClient;
    private final ObjectMapper objectMapper;

    public QuoteSubmissionApplicationService(EligibilityApplicationService eligibilityApplicationService,
                                             EligibilityPersistRepository repository,
                                             CatalogClient catalogClient,
                                             ObjectMapper objectMapper) {
        this.eligibilityApplicationService = eligibilityApplicationService;
        this.repository = repository;
        this.catalogClient = catalogClient;
        this.objectMapper = objectMapper.findAndRegisterModules();
    }

    @Transactional
    public QuoteSubmissionResponse submitConventionalPurchase(UUID tenantId, QuoteSubmissionRequest request,
                                                              String idempotencyKey, String correlationId) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ValidationException("Idempotency-Key is required.", List.of(fieldError("Idempotency-Key", "REQUIRED", "Idempotency-Key header is required.")));
        }
        validateRequest(request);

        String requestHash = requestHash(tenantId, request);
        QuoteSubmissionResponse replay = repository.findIdempotentQuoteSubmission(tenantId, idempotencyKey, requestHash, QuoteSubmissionResponse.class).orElse(null);
        if (replay != null) {
            return replay;
        }

        UUID scenarioId = UUID.randomUUID();
        UUID quoteId = UUID.randomUUID();
        UUID actorUuid = stableActorUuid(request.actorId());
        Instant now = Instant.now();
        ScenarioFacts facts = normalizeFacts(tenantId, scenarioId, actorUuid, request);

        List<ProductCandidate> candidates = catalogClient.publishedConventionalPurchaseCandidates(request.channel(), request.property().state());
        if (candidates.isEmpty()) {
            throw new DependencyUnavailableException("No active conventional purchase product candidates are configured for this tenant/channel/state.");
        }

        List<QuoteSubmissionResponse.QuoteOptionOutput> options = new ArrayList<>();
        List<EligibilityResult> evaluationResults = new ArrayList<>();
        for (ProductCandidate candidate : candidates) {
            EligibilityRequest eligibilityRequest = new EligibilityRequest(
                new BorrowerProfile(request.borrower().representativeFico(), request.borrower().monthlyIncome(), request.borrower().monthlyDebt()),
                new PropertyProfile(request.property().state(), request.property().county(), request.property().zip(), request.property().propertyType(),
                    request.property().units(), request.property().occupancyType(), request.property().purchasePrice(), request.property().appraisedValue()),
                new LoanProfile(request.loan().loanPurpose(), request.loan().loanAmount(), defaultZero(request.loan().subordinateFinancingAmount()),
                    request.loan().requestedLockPeriodDays(), request.loan().documentationType(), request.loan().ausType(), 1),
                candidate,
                ProductFamily.CONVENTIONAL,
                QuoteType.CONVENTIONAL_PURCHASE
            );
            EligibilityResult result = eligibilityApplicationService.evaluate(tenantId, eligibilityRequest);
            evaluationResults.add(result);
            options.add(new QuoteSubmissionResponse.QuoteOptionOutput(
                UUID.randomUUID(),
                candidate.productCode(),
                candidate.investorCode(),
                result.status(),
                "NOT_REQUESTED",
                summaryReason(result.status())
            ));
        }

        String resultHash = resultHash(requestHash, evaluationResults);
        QuoteSubmissionResponse response = new QuoteSubmissionResponse(
            quoteId,
            scenarioId,
            SCENARIO_VERSION,
            "ELIGIBILITY_ONLY",
            (int) options.stream().filter(o -> "ELIGIBLE".equals(o.eligibilityStatus())).count(),
            (int) options.stream().filter(o -> "INELIGIBLE".equals(o.eligibilityStatus())).count(),
            (int) options.stream().filter(o -> "WARNING".equals(o.eligibilityStatus()) || "CANNOT_DECIDE".equals(o.eligibilityStatus())).count(),
            options,
            UUID.randomUUID(),
            resultHash,
            normalizeCorrelation(correlationId),
            now
        );

        repository.saveQuoteSubmission(tenantId, facts, request, response, evaluationResults, requestHash, Hashing.sha256(idempotencyKey), actorUuid, now);
        repository.saveIdempotentResponse(tenantId, idempotencyKey, requestHash, "QuoteSubmissionResponse", response);
        return response;
    }

    private void validateRequest(QuoteSubmissionRequest request) {
        List<Map<String, String>> errors = new ArrayList<>();
        if (request == null) {
            throw new ValidationException("Scenario contains invalid fields.", List.of(fieldError("request", "REQUIRED", "Request body is required.")));
        }
        if (!"CONVENTIONAL_PURCHASE".equals(request.quoteType())) errors.add(fieldError("quoteType", "UNSUPPORTED", "Only CONVENTIONAL_PURCHASE is supported."));
        if (request.channel() == null || request.channel().isBlank()) errors.add(fieldError("channel", "REQUIRED", "Channel is required."));
        if (request.borrower() == null) errors.add(fieldError("borrower", "REQUIRED", "Borrower is required."));
        if (request.property() == null) errors.add(fieldError("property", "REQUIRED", "Property is required."));
        if (request.loan() == null) errors.add(fieldError("loan", "REQUIRED", "Loan is required."));
        if (request.borrower() != null && request.borrower().representativeFico() != null
            && (request.borrower().representativeFico() < 300 || request.borrower().representativeFico() > 850)) {
            errors.add(fieldError("borrower.representativeFico", "OUT_OF_RANGE", "Representative FICO must be between 300 and 850."));
        }
        if (request.property() != null) {
            if (request.property().state() == null || request.property().state().isBlank()) errors.add(fieldError("property.state", "REQUIRED", "Property state is required."));
            if (request.property().zip() == null || request.property().zip().isBlank()) errors.add(fieldError("property.zip", "REQUIRED", "Property ZIP is required."));
            if (request.property().units() < 1 || request.property().units() > 4) errors.add(fieldError("property.units", "OUT_OF_RANGE", "Units must be between 1 and 4."));
            requirePositive(request.property().purchasePrice(), "property.purchasePrice", errors);
            if (request.property().appraisedValue() != null) requirePositive(request.property().appraisedValue(), "property.appraisedValue", errors);
        }
        if (request.loan() != null) {
            if (!"PURCHASE".equals(request.loan().loanPurpose())) errors.add(fieldError("loan.loanPurpose", "UNSUPPORTED", "Loan purpose must be PURCHASE."));
            requirePositive(request.loan().loanAmount(), "loan.loanAmount", errors);
            if (request.loan().subordinateFinancingAmount() != null && request.loan().subordinateFinancingAmount().compareTo(BigDecimal.ZERO) < 0) {
                errors.add(fieldError("loan.subordinateFinancingAmount", "MUST_NOT_BE_NEGATIVE", "Subordinate financing amount must not be negative."));
            }
            if (request.loan().requestedLockPeriodDays() == null || request.loan().requestedLockPeriodDays() <= 0) {
                errors.add(fieldError("loan.requestedLockPeriodDays", "MUST_BE_POSITIVE", "Requested lock period days must be greater than 0."));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("Scenario contains invalid fields.", errors);
        }
    }

    private ScenarioFacts normalizeFacts(UUID tenantId, UUID scenarioId, UUID actorUuid, QuoteSubmissionRequest request) {
        BigDecimal collateralValue = request.property().appraisedValue() == null
            ? request.property().purchasePrice()
            : request.property().purchasePrice().min(request.property().appraisedValue());
        BigDecimal subordinate = defaultZero(request.loan().subordinateFinancingAmount());
        BigDecimal ltv = request.loan().loanAmount().divide(collateralValue, 5, RoundingMode.HALF_UP);
        BigDecimal cltv = request.loan().loanAmount().add(subordinate).divide(collateralValue, 5, RoundingMode.HALF_UP);
        BigDecimal dti = null;
        if (request.borrower().monthlyIncome() != null && request.borrower().monthlyDebt() != null && request.borrower().monthlyIncome().compareTo(BigDecimal.ZERO) > 0) {
            dti = request.borrower().monthlyDebt().divide(request.borrower().monthlyIncome(), 5, RoundingMode.HALF_UP);
        }
        return new ScenarioFacts(scenarioId, tenantId, request.channel(), request.loan().loanPurpose(), request.property().occupancyType(),
            request.loan().loanAmount(), request.property().purchasePrice(), request.property().appraisedValue(), subordinate, ltv, cltv,
            request.borrower().representativeFico(), dti, request.property().state(), request.property().county(), request.property().zip(),
            request.property().propertyType(), request.property().units(), request.loan().requestedLockPeriodDays(), request.loan().ausType(),
            request.loan().documentationType(), "VALID", actorUuid);
    }

    private void requirePositive(BigDecimal value, String field, List<Map<String, String>> errors) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            errors.add(fieldError(field, "MUST_BE_POSITIVE", field + " must be greater than 0."));
        }
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String requestHash(UUID tenantId, QuoteSubmissionRequest request) {
        return Hashing.sha256(json(Map.of("tenantId", tenantId, "slice", "PII-03-S01", "request", request)));
    }

    private String resultHash(String requestHash, List<EligibilityResult> results) {
        return Hashing.sha256(json(Map.of("requestHash", requestHash, "evaluations", results.stream().map(EligibilityResult::resultHash).sorted().toList())));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to canonicalize quote submission", ex);
        }
    }

    private UUID stableActorUuid(String actorId) {
        if (actorId == null || actorId.isBlank()) {
            return UUID.nameUUIDFromBytes("unknown".getBytes(StandardCharsets.UTF_8));
        }
        try {
            return UUID.fromString(actorId);
        } catch (IllegalArgumentException ex) {
            return UUID.nameUUIDFromBytes(actorId.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String normalizeCorrelation(String correlationId) {
        return correlationId == null || correlationId.isBlank() ? UUID.randomUUID().toString() : correlationId;
    }

    private String summaryReason(String status) {
        return switch (status) {
            case "ELIGIBLE" -> "Eligible for conventional purchase initial pricing.";
            case "WARNING" -> "Eligible with conventional purchase eligibility warnings.";
            default -> "Conventional purchase candidate is not eligible for initial pricing.";
        };
    }

    private Map<String, String> fieldError(String field, String reason, String message) {
        return Map.of("field", field, "reason", reason, "message", message);
    }

    public static class ValidationException extends RuntimeException {
        private final List<Map<String, String>> fieldErrors;

        ValidationException(String message, List<Map<String, String>> fieldErrors) {
            super(message);
            this.fieldErrors = fieldErrors;
        }

        public List<Map<String, String>> fieldErrors() {
            return fieldErrors;
        }
    }

    public static class IdempotencyConflictException extends RuntimeException {
        public IdempotencyConflictException() {
            super("Idempotency key was already used with a different request payload.");
        }
    }

    public static class DependencyUnavailableException extends RuntimeException {
        DependencyUnavailableException(String message) {
            super(message);
        }
    }
}
