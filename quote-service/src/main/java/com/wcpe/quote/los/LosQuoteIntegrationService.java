package com.wcpe.quote.los;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.quote.QuoteJob;
import com.wcpe.quote.QuoteJobRepository;
import com.wcpe.quote.QuoteJobStatus;
import com.wcpe.quote.los.LosQuoteModels.LosQuoteRequest;
import com.wcpe.quote.los.LosQuoteModels.LosQuoteResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LosQuoteIntegrationService {
  private static final String LOANPASS_PUBLIC_API_DOCS_URL = "https://docs.loanpass.io/public-api/index.html";
  private static final String LOANPASS_PUBLIC_API_SCHEMA_URL = "https://api.loanpass.io/v1/swagger/schema.json";
  private static final String LOANPASS_PUBLIC_API_OPERATION_CONCEPTS = "execute-summary,execute-product";
  private static final String LOANPASS_CONTRACT_FIELD_POLICY = "concept-aligned-only-no-request-response-fields-adopted";
  private static final Duration LOS_JOB_RETENTION = Duration.ofHours(24);
  private final ObjectMapper objectMapper;
  private final QuoteJobRepository jobRepository;
  private final Clock clock;

  @Autowired
  public LosQuoteIntegrationService(ObjectMapper objectMapper, QuoteJobRepository jobRepository) {
    this(objectMapper, jobRepository, Clock.systemUTC());
  }

  LosQuoteIntegrationService(ObjectMapper objectMapper, QuoteJobRepository jobRepository, Clock clock) {
    this.objectMapper = objectMapper;
    this.jobRepository = jobRepository;
    this.clock = clock;
  }

  LosQuoteResponse start(LosQuoteRequest request, String requestId, String correlationId) {
    validate(request, requestId);
    String requestHash = hash(request);
    String idempotencyKey = requestId == null || requestId.isBlank() ? request.idempotencyKey() : requestId;
    UUID tenantUuid = durableTenantId(request.tenantId());
    Optional<QuoteJob> existing = jobRepository.findByIdempotencyKey(tenantUuid, idempotencyKey);
    if (existing.isPresent()) {
      if (!existing.get().requestHash().equals(requestHash)) {
        throw new LosQuoteValidationException("LOS_IDEMPOTENCY_CONFLICT", "Idempotency key already belongs to a different LOS quote request");
      }
      return toResponse(existing.get());
    }
    String quoteIdentity = request.quoteBorrowerInfo() == null ? request.requestId() : request.quoteBorrowerInfo().loanNumber();
    UUID jobId = UUID.nameUUIDFromBytes((request.tenantId() + ":" + quoteIdentity + ":" + idempotencyKey).getBytes(StandardCharsets.UTF_8));
    Instant acceptedAt = clock.instant();
    QuoteJob job = new QuoteJob(tenantUuid, jobId, QuoteJobStatus.QUEUED, durableRequestPayload(request), requestHash,
        null, null, null, progressSnapshot(request), 0, 1, idempotencyKey,
        blank(request.actorId()) ? "los:request" : request.actorId(), acceptedAt, acceptedAt,
        acceptedAt.plus(LOS_JOB_RETENTION), correlationId == null || correlationId.isBlank() ? request.correlationId() : correlationId, 1);
    return toResponse(jobRepository.save(job));
  }

  Optional<LosQuoteResponse> get(String jobId) {
    try {
      return jobRepository.findByJobId(UUID.fromString(jobId)).map(this::toResponse);
    } catch (IllegalArgumentException ex) {
      return Optional.empty();
    }
  }

  private void validate(LosQuoteRequest request, String requestId) {
    if (request == null) {
      throw new LosQuoteValidationException("LOS_QUOTE_REQUEST_REQUIRED", "LOS quote request body is required");
    }
    if (blank(request.tenantId()) || blank(request.requestId())) {
      throw new LosQuoteValidationException("LOS_QUOTE_REQUEST_INVALID", "tenantId and requestId are required");
    }
    if (request.quoteBorrowerInfo() == null || blank(request.quoteBorrowerInfo().borrowerLastName()) || blank(request.quoteBorrowerInfo().loanNumber())) {
      throw new LosQuoteValidationException("LOS_QUOTE_IDENTITY_INVALID", "quoteBorrowerInfo.borrowerLastName and loanNumber are required");
    }
    if (request.quoteAddressDTO() == null || blank(request.quoteAddressDTO().state()) || blank(request.quoteAddressDTO().zip())) {
      throw new LosQuoteValidationException("LOS_QUOTE_ADDRESS_INVALID", "quoteAddressDTO.state and quoteAddressDTO.zip are required");
    }
    validateProductAuthorizationMetadata(request);
    boolean mappingConfigAvailable = !blank(request.clientContext().get("mappingConfigRef"));
    validateConfiguredEnum("transactionType", request.transactionType(), mappingConfigAvailable);
    validateConfiguredEnum("propertyInformationType", request.propertyInformationType(), mappingConfigAvailable);
    validateConfiguredEnum("occupancyType", request.occupancyType(), mappingConfigAvailable);
    validateConfiguredEnum("incomeDocumentationType", request.incomeDocumentationType(), mappingConfigAvailable);
    validateConfiguredEnum("mortgageType", request.mortgageType(), mappingConfigAvailable);
    validateConfiguredEnum("amortizationType", request.amortizationType(), mappingConfigAvailable);
    validateConfiguredEnum("loanTermType", request.loanTermType(), mappingConfigAvailable);
    validateConfiguredEnum("lockPeriodType", request.lockPeriodType(), mappingConfigAvailable);
    validateCreditApplicationEnumMappings(request.creditApplicationFields(), mappingConfigAvailable);
    if (request.creditApplicationFields().isEmpty()) {
      throw new LosQuoteValidationException("LOS_QUOTE_ENGINE_FIELDS_REQUIRED", "creditApplicationFields are required");
    }
    if (blank(requestId) && blank(request.idempotencyKey())) {
      throw new LosQuoteValidationException("LOS_IDEMPOTENCY_REQUIRED", "X-Request-ID or idempotencyKey is required");
    }
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private Map<String, String> progressSnapshot(LosQuoteRequest request) {
    java.util.LinkedHashMap<String, String> progress = new java.util.LinkedHashMap<>();
    progress.put("source", "LOS");
    progress.put("stage", "accepted");
    progress.put("loanPassPublicApiDocsUrl", LOANPASS_PUBLIC_API_DOCS_URL);
    progress.put("loanPassPublicApiSchemaUrl", LOANPASS_PUBLIC_API_SCHEMA_URL);
    progress.put("loanPassPublicApiOperationConcepts", LOANPASS_PUBLIC_API_OPERATION_CONCEPTS);
    progress.put("loanPassContractFieldPolicy", LOANPASS_CONTRACT_FIELD_POLICY);
    progress.put("productAuthorizationPolicy", "fail-closed-unless-product-catalog-and-authorization-metadata-refs-exist");
    progress.put("catalogMetadataPolicy", "fail-closed-when-loanpass-catalog-codes-or-product-selection-are-present");
    progress.put("pipelineScenarioLinked", Boolean.toString(!blank(request.scenarioId())));
    putIfPresent(progress, "scenarioRef", request.scenarioId());
    if (request.scenarioVersion() > 0) {
      progress.put("scenarioVersion", Integer.toString(request.scenarioVersion()));
    }
    putIfPresent(progress, "requestSnapshotRef", request.clientContext().get("requestSnapshotRef"));
    putIfPresent(progress, "mappingConfigRef", request.clientContext().get("mappingConfigRef"));
    putIfPresent(progress, "productCatalogRef", request.clientContext().get("productCatalogRef"));
    putIfPresent(progress, "productAuthorizationMetadataRef", request.clientContext().get("productAuthorizationMetadataRef"));
    putIfPresent(progress, "ruleCatalogRef", request.clientContext().get("ruleCatalogRef"));
    putIfPresent(progress, "stipulationCatalogRef", request.clientContext().get("stipulationCatalogRef"));
    putIfPresent(progress, "rateCatalogRef", request.clientContext().get("rateCatalogRef"));
    putIfPresent(progress, "lockTermCatalogRef", request.clientContext().get("lockTermCatalogRef"));
    putIfPresent(progress, "productId", request.productId());
    putIfPresent(progress, "selectedProgramId", request.selectedProgramId());
    putIfPresent(progress, "priceGroupId", request.priceGroupId());
    putIfPresent(progress, "lockPeriodType", request.lockPeriodType());
    return Map.copyOf(progress);
  }

  private void putIfPresent(Map<String, String> target, String key, String value) {
    if (!blank(value)) {
      target.put(key, value);
    }
  }

  private void validateProductAuthorizationMetadata(LosQuoteRequest request) {
    if (blank(request.productId()) && blank(request.selectedProgramId()) && blank(request.priceGroupId())) {
      return;
    }
    java.util.List<String> missing = java.util.List.of(
            "productCatalogRef", "productAuthorizationMetadataRef", "ruleCatalogRef", "stipulationCatalogRef", "rateCatalogRef", "lockTermCatalogRef")
        .stream()
        .filter(key -> blank(request.clientContext().get(key)))
        .toList();
    if (!missing.isEmpty()) {
      throw new LosQuoteValidationException("LOS_QUOTE_PRODUCT_AUTHORIZATION_UNAVAILABLE",
          "Tenant product, rule, stipulation, rate, and lock-period metadata is not configured; quote job was not started. missing="
              + String.join(",", missing));
    }
  }

  private void validateConfiguredEnum(String fieldName, String value, boolean mappingConfigAvailable) {
    if (!mappingConfigAvailable && !blank(value) && value.trim().matches("\\d+")) {
      throw new LosQuoteValidationException("LOS_QUOTE_ENUM_MAPPING_REQUIRED",
          fieldName + " uses a numeric LoanPass code without configured mapping metadata");
    }
  }

  private void validateCreditApplicationEnumMappings(java.util.List<LosQuoteModels.CreditApplicationField> fields, boolean mappingConfigAvailable) {
    for (LosQuoteModels.CreditApplicationField field : fields == null ? java.util.List.<LosQuoteModels.CreditApplicationField>of() : fields) {
      if (field == null || field.value() == null || !"enum".equals(field.value().type())) {
        continue;
      }
      if (!mappingConfigAvailable && (looksNumeric(field.value().value()) || looksNumeric(field.value().variantId()))) {
        throw new LosQuoteValidationException("LOS_QUOTE_ENUM_MAPPING_REQUIRED",
            field.fieldId() + " uses a numeric LoanPass enum code without configured mapping metadata");
      }
    }
  }

  private boolean looksNumeric(Object value) {
    return value != null && value.toString().trim().matches("\\d+");
  }

  private String hash(Object value) {
    try {
      return UUID.nameUUIDFromBytes(objectMapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8)).toString();
    } catch (JsonProcessingException ex) {
      throw new LosQuoteValidationException("LOS_QUOTE_HASH_FAILED", "Unable to hash LOS quote request");
    }
  }

  private Map<String, String> durableRequestPayload(LosQuoteRequest request) {
    LinkedHashMap<String, String> payload = new LinkedHashMap<>();
    payload.put("source", "LOS");
    payload.put("tenantId", request.tenantId());
    payload.put("requestId", request.requestId());
    putIfPresent(payload, "scenarioId", request.scenarioId());
    if (request.scenarioVersion() > 0) {
      payload.put("scenarioVersion", Integer.toString(request.scenarioVersion()));
    }
    putIfPresent(payload, "productId", request.productId());
    putIfPresent(payload, "selectedProgramId", request.selectedProgramId());
    putIfPresent(payload, "priceGroupId", request.priceGroupId());
    putIfPresent(payload, "requestedLockPeriods", request.requestedLockPeriods().toString());
    putIfPresent(payload, "desiredRateLockPeriod", request.desiredRateLockPeriod() == null ? null : request.desiredRateLockPeriod().toString());
    putIfPresent(payload, "lockPeriodType", request.lockPeriodType());
    putIfPresent(payload, "creditApplicationFieldIds", request.creditApplicationFields().stream()
        .filter(field -> field != null && !blank(field.fieldId()))
        .map(LosQuoteModels.CreditApplicationField::fieldId)
        .toList().toString());
    putIfPresent(payload, "requestSnapshotRef", request.clientContext().get("requestSnapshotRef"));
    putIfPresent(payload, "mappingConfigRef", request.clientContext().get("mappingConfigRef"));
    putIfPresent(payload, "productCatalogRef", request.clientContext().get("productCatalogRef"));
    putIfPresent(payload, "productAuthorizationMetadataRef", request.clientContext().get("productAuthorizationMetadataRef"));
    putIfPresent(payload, "ruleCatalogRef", request.clientContext().get("ruleCatalogRef"));
    putIfPresent(payload, "stipulationCatalogRef", request.clientContext().get("stipulationCatalogRef"));
    putIfPresent(payload, "rateCatalogRef", request.clientContext().get("rateCatalogRef"));
    putIfPresent(payload, "lockTermCatalogRef", request.clientContext().get("lockTermCatalogRef"));
    return Map.copyOf(payload);
  }

  private UUID durableTenantId(String tenantId) {
    return UUID.nameUUIDFromBytes(("los-tenant:" + tenantId).getBytes(StandardCharsets.UTF_8));
  }

  private LosQuoteResponse toResponse(QuoteJob job) {
    return new LosQuoteResponse(job.jobId().toString(), job.status().name(), "/api/v1/los/quote-requests/" + job.jobId(),
        job.correlationId(), job.createdAt(), job.progress());
  }
}
