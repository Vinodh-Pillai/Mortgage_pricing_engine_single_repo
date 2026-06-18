package com.wcpe.quote.los;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.quote.los.LosQuoteModels.LosQuoteRequest;
import com.wcpe.quote.los.LosQuoteModels.LosQuoteResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LosQuoteIntegrationService {
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final Map<String, StoredQuoteJob> jobsById = new ConcurrentHashMap<>();
  private final Map<String, StoredQuoteJob> jobsByIdempotencyKey = new ConcurrentHashMap<>();

  @Autowired
  public LosQuoteIntegrationService(ObjectMapper objectMapper) {
    this(objectMapper, Clock.systemUTC());
  }

  LosQuoteIntegrationService(ObjectMapper objectMapper, Clock clock) {
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  LosQuoteResponse start(LosQuoteRequest request, String requestId, String correlationId) {
    validate(request, requestId);
    String requestHash = hash(request);
    String idempotencyKey = requestId == null || requestId.isBlank() ? request.idempotencyKey() : requestId;
    StoredQuoteJob existing = jobsByIdempotencyKey.get(idempotencyKey);
    if (existing != null && existing.requestHash().equals(requestHash)) {
      return existing.toResponse();
    }
    String quoteIdentity = request.quoteBorrowerInfo() == null ? request.requestId() : request.quoteBorrowerInfo().loanNumber();
    String jobId = UUID.nameUUIDFromBytes((request.tenantId() + ":" + quoteIdentity + ":" + idempotencyKey).getBytes(StandardCharsets.UTF_8)).toString();
    StoredQuoteJob job = new StoredQuoteJob(jobId, request.tenantId(), request.scenarioId(), "QUEUED", requestHash,
        correlationId == null || correlationId.isBlank() ? request.correlationId() : correlationId, clock.instant(),
        progressSnapshot(request));
    jobsById.put(jobId, job);
    jobsByIdempotencyKey.put(idempotencyKey, job);
    return job.toResponse();
  }

  Optional<LosQuoteResponse> get(String jobId) {
    return Optional.ofNullable(jobsById.get(jobId)).map(StoredQuoteJob::toResponse);
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
    if (!blank(request.productId()) || !blank(request.selectedProgramId()) || !blank(request.priceGroupId())) {
      throw new LosQuoteValidationException("LOS_QUOTE_PRODUCT_AUTHORIZATION_UNAVAILABLE",
          "Tenant product, selectedProgramId, and priceGroup authorization metadata is not configured; quote job was not started");
    }
    validateConfiguredEnum("transactionType", request.transactionType());
    validateConfiguredEnum("propertyInformationType", request.propertyInformationType());
    validateConfiguredEnum("occupancyType", request.occupancyType());
    validateConfiguredEnum("incomeDocumentationType", request.incomeDocumentationType());
    validateConfiguredEnum("mortgageType", request.mortgageType());
    validateConfiguredEnum("amortizationType", request.amortizationType());
    validateConfiguredEnum("loanTermType", request.loanTermType());
    validateConfiguredEnum("lockPeriodType", request.lockPeriodType());
    validateCreditApplicationEnumMappings(request.creditApplicationFields());
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
    progress.put("pipelineScenarioLinked", Boolean.toString(!blank(request.scenarioId())));
    putIfPresent(progress, "scenarioRef", request.scenarioId());
    if (request.scenarioVersion() > 0) {
      progress.put("scenarioVersion", Integer.toString(request.scenarioVersion()));
    }
    putIfPresent(progress, "requestSnapshotRef", request.clientContext().get("requestSnapshotRef"));
    putIfPresent(progress, "mappingConfigRef", request.clientContext().get("mappingConfigRef"));
    return Map.copyOf(progress);
  }

  private void putIfPresent(Map<String, String> target, String key, String value) {
    if (!blank(value)) {
      target.put(key, value);
    }
  }

  private void validateConfiguredEnum(String fieldName, String value) {
    if (!blank(value) && value.trim().matches("\\d+")) {
      throw new LosQuoteValidationException("LOS_QUOTE_ENUM_MAPPING_REQUIRED",
          fieldName + " uses a numeric LoanPass code without configured mapping metadata");
    }
  }

  private void validateCreditApplicationEnumMappings(java.util.List<LosQuoteModels.CreditApplicationField> fields) {
    for (LosQuoteModels.CreditApplicationField field : fields == null ? java.util.List.<LosQuoteModels.CreditApplicationField>of() : fields) {
      if (field == null || field.value() == null || !"enum".equals(field.value().type())) {
        continue;
      }
      if (looksNumeric(field.value().value()) || looksNumeric(field.value().variantId())) {
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

  private record StoredQuoteJob(String jobId, String tenantId, String scenarioId, String status, String requestHash,
      String correlationId, Instant acceptedAt, Map<String, String> progress) {
    StoredQuoteJob {
      progress = Map.copyOf(progress == null ? Map.of() : progress);
    }

    LosQuoteResponse toResponse() {
      return new LosQuoteResponse(jobId, status, "/api/v1/los/quote-requests/" + jobId, correlationId, acceptedAt, progress);
    }
  }
}
