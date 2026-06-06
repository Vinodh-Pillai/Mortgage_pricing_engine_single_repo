package com.wcpe.eligibility.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.wcpe.eligibility.domain.models.EligibilityExplanationResponse;
import com.wcpe.eligibility.domain.models.EligibilityResult;
import com.wcpe.eligibility.domain.models.ProductFamily;
import com.wcpe.eligibility.domain.models.QuoteSubmissionRequest;
import com.wcpe.eligibility.domain.models.QuoteSubmissionResponse;
import com.wcpe.eligibility.domain.models.QuoteType;
import com.wcpe.eligibility.domain.models.RuleDecision;
import com.wcpe.eligibility.domain.models.ScenarioFacts;
import com.wcpe.eligibility.service.QuoteSubmissionApplicationService.IdempotencyConflictException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class EligibilityPersistRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public EligibilityPersistRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper.findAndRegisterModules();
    }

    public void saveEvaluation(UUID tenantId, EligibilityResult result) {
        jdbc.update(
            "insert into eligibility.eligibility_evaluation(tenant_id,evaluation_id,scenario_id,scenario_version,rule_set_version,evaluation_status,input_hash,result_hash) values (?,?,?,?,?,?,?,?)",
            tenantId, result.evaluationId(), UUID.randomUUID(), 1, 3, result.status(), result.requestHash(), result.resultHash()
        );

        for (RuleDecision decision : result.decisions()) {
            jdbc.update(
                "insert into eligibility.eligibility_decision(tenant_id,decision_id,evaluation_id,product_code,investor_code,rule_code,severity,decision,reason_code,message,actual_value,required_value,trace_json) values (?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb)",
                tenantId, decision.decisionId(), result.evaluationId(), decision.productCode(), decision.investorCode(),
                decision.ruleCode(), decision.severity(), decision.status(), decision.reasonCode(), decision.message(),
                decision.actualValue(), decision.requiredValue(), json(decision.trace())
            );
        }

        audit(tenantId, result.evaluationId(), "ELIGIBILITY_EVALUATED", result.resultHash(), result);
    }

    public <T> Optional<T> findIdempotentQuoteSubmission(UUID tenantId, String idempotencyKey, String requestHash, Class<T> responseType) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "select request_hash, response_json::text from eligibility.idempotency_record where tenant_id = ? and idempotency_key = ?",
            tenantId, idempotencyKey
        );
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        String existingHash = (String) rows.get(0).get("request_hash");
        if (!requestHash.equals(existingHash)) {
            throw new IdempotencyConflictException();
        }
        return Optional.of(readJson((String) rows.get(0).get("response_json"), responseType));
    }

    public void saveIdempotentResponse(UUID tenantId, String idempotencyKey, String requestHash, String responseType, Object response) {
        jdbc.update(
            "insert into eligibility.idempotency_record (tenant_id, idempotency_key, request_hash, response_type, response_json) " +
                "values (?, ?, ?, ?, ?::jsonb) on conflict (tenant_id, idempotency_key) do nothing",
            tenantId, idempotencyKey, requestHash, responseType, json(response)
        );
    }

    public void saveQuoteSubmission(UUID tenantId, ScenarioFacts facts, QuoteSubmissionRequest request,
                                    QuoteSubmissionResponse response, List<EligibilityResult> evaluationResults,
                                    String requestHash, String idempotencyKeyHash, UUID actorId, Instant now) {
        jdbc.update(
            "insert into eligibility.scenario(tenant_id,scenario_id,scenario_version,channel,loan_purpose,occupancy_type,loan_amount,purchase_price,appraised_value," +
                "subordinate_financing_amount,ltv,cltv,representative_fico,dti,property_state,property_county,property_zip,property_type,units,lock_period_days," +
                "aus_type,documentation_type,fact_quality_status,created_by,created_at_utc,updated_at_utc) " +
                "values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            tenantId, facts.scenarioId(), 1, facts.channel(), facts.loanPurpose(), facts.occupancyType(), facts.loanAmount(), facts.purchasePrice(),
            facts.appraisedValue(), facts.subordinateFinancingAmount(), facts.ltv(), facts.cltv(), facts.representativeFico(), facts.dti(),
            facts.propertyState(), facts.propertyCounty(), facts.propertyZip(), facts.propertyType(), facts.units(), facts.lockPeriodDays(), facts.ausType(),
            facts.documentationType(), facts.factQualityStatus(), actorId, Timestamp.from(now), Timestamp.from(now)
        );

        jdbc.update(
            "insert into eligibility.audit_package(audit_package_id,tenant_id,aggregate_id,actor_id,correlation_id,causation_id,request_hash,result_hash,scenario_version,rule_versions_json,created_at_utc) " +
                "values (?,?,?,?,?,?,?,?,?,?,?)",
            response.auditPackageId(), tenantId, response.quoteId(), actorId.toString(), response.correlationId(), response.scenarioId().toString(),
            requestHash, response.resultHash(), response.scenarioVersion(), json(Map.of("eligibilityRuleSetVersions", evaluationResults.stream().map(EligibilityResult::eligibilityRuleSetVersion).distinct().toList())), Timestamp.from(now)
        );

        jdbc.update(
            "insert into eligibility.quote(tenant_id,quote_id,scenario_id,scenario_version,quote_status,request_hash,result_hash,audit_package_id,request_json,response_json,requested_at," +
                "requested_by,requested_at_utc,idempotency_key_hash) values (?,?,?,?,?,?,?,?,?::jsonb,?::jsonb,?,?,?,?)",
            tenantId, response.quoteId(), response.scenarioId(), response.scenarioVersion(), response.quoteStatus(), requestHash, response.resultHash(),
            response.auditPackageId(), json(request), json(response), Timestamp.from(now), actorId, Timestamp.from(now), idempotencyKeyHash
        );

        for (int i = 0; i < response.options().size(); i++) {
            QuoteSubmissionResponse.QuoteOptionOutput option = response.options().get(i);
            EligibilityResult result = evaluationResults.get(i);
            jdbc.update(
                "insert into eligibility.quote_option(tenant_id,quote_option_id,quote_id,product_code,investor_code,eligibility_status,pricing_status,display_rank,summary_reason,decisions_json," +
                    "product_version_id,investor_id,channel,eligibility_evaluation_id) values (?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?,?)",
                tenantId, option.quoteOptionId(), response.quoteId(), option.productCode(), option.investorCode(), option.eligibilityStatus(),
                option.pricingStatus(), i + 1, option.summaryReason(), json(result.decisions()), stableUuid(option.productCode()), stableUuid(option.investorCode()),
                request.channel(), result.evaluationId()
            );
        }

        audit(tenantId, response.quoteId(), "CONVENTIONAL_ELIGIBILITY_CORE_COMPLETED", response.resultHash(), Map.of(
            "actorId", actorId.toString(),
            "scenarioId", response.scenarioId().toString(),
            "quoteId", response.quoteId().toString(),
            "resultHash", response.resultHash(),
            "correlationId", response.correlationId()
        ));

        saveOutbox(tenantId, response.scenarioId(), "ScenarioSubmitted.v1", response, now);
        saveOutbox(tenantId, response.scenarioId(), "ScenarioValidated.v1", response, now);
        saveOutbox(tenantId, response.quoteId(), "QuoteEligibilityRequested.v1", response, now);
        saveOutbox(tenantId, response.quoteId(), "QuoteEligibilityShellCreated.v1", response, now);
    }

    private void saveOutbox(UUID tenantId, UUID aggregateId, String eventType, QuoteSubmissionResponse response, Instant now) {
        jdbc.update(
            "insert into eligibility.outbox_event(tenant_id,event_id,aggregate_id,event_type,payload_json,occurred_at) values (?,?,?,?,?::jsonb,?)",
            tenantId, UUID.randomUUID(), aggregateId, eventType, json(Map.of(
                "eventType", eventType,
                "eventVersion", "1",
                "tenantId", tenantId.toString(),
                "aggregateId", aggregateId.toString(),
                "aggregateVersion", response.scenarioVersion(),
                "correlationId", response.correlationId(),
                "schemaUri", "https://pricing/events/" + eventType + ".json",
                "occurredAtUtc", now.toString(),
                "payload", Map.of(
                    "quoteId", response.quoteId().toString(),
                    "scenarioId", response.scenarioId().toString(),
                    "resultHash", response.resultHash(),
                    "summary", Map.of(
                        "eligibleOptionCount", response.eligibleOptionCount(),
                        "ineligibleOptionCount", response.ineligibleOptionCount(),
                        "warningOptionCount", response.warningOptionCount()
                    )
                )
            )), Timestamp.from(now)
        );
    }

    private UUID stableUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public void auditUnsupportedProductFamilyAttempt(UUID tenantId, ProductFamily productFamily, QuoteType quoteType) {
        UUID auditId = UUID.randomUUID();
        audit(tenantId, auditId, "UNSUPPORTED_PRODUCT_FAMILY_ATTEMPT", null, Map.of(
            "requestedFamily", productFamily.name(),
            "quoteType", quoteType.name(),
            "reasonCode", "PRODUCT_FAMILY_NOT_ENABLED_FOR_SLICE"
        ));
    }

    public Optional<EligibilityExplanationResponse> findEligibilityExplanation(UUID tenantId, UUID quoteId, UUID quoteOptionId) {
        List<EligibilityExplanationResponse> rows = jdbc.query(
            "select quote_id, quote_option_id, scenario_id, scenario_version, product_code, investor_code, eligibility_status, " +
                "summary_json::text, rules_json::text, audit_package_id, result_hash, rule_version_graph_hash " +
                "from eligibility.eligibility_explanation_read_model " +
                "where tenant_id = ? and quote_id = ? and quote_option_id = ?",
            (rs, rowNum) -> new EligibilityExplanationResponse(
                rs.getObject("quote_id", UUID.class),
                rs.getObject("quote_option_id", UUID.class),
                rs.getObject("scenario_id", UUID.class),
                rs.getInt("scenario_version"),
                rs.getString("product_code"),
                rs.getString("investor_code"),
                rs.getString("eligibility_status"),
                readJson(rs.getString("summary_json"), EligibilityExplanationResponse.Summary.class),
                readJson(rs.getString("rules_json"), new TypeReference<List<EligibilityExplanationResponse.Rule>>() {}),
                new EligibilityExplanationResponse.Audit(
                    rs.getObject("audit_package_id", UUID.class),
                    rs.getString("result_hash"),
                    rs.getString("rule_version_graph_hash")
                )
            ),
            tenantId, quoteId, quoteOptionId
        );
        return rows.stream().findFirst();
    }

    public void auditExplanationViewed(UUID tenantId, UUID quoteOptionId, String actorId, String correlationId, String resultHash) {
        audit(tenantId, quoteOptionId, "ELIGIBILITY_EXPLANATION_VIEWED", resultHash, Map.of(
            "actorId", actorId == null || actorId.isBlank() ? "unknown" : actorId,
            "quoteOptionId", quoteOptionId.toString(),
            "sensitivityLevel", "SENSITIVE",
            "correlationId", correlationId == null || correlationId.isBlank() ? "not-provided" : correlationId
        ));
    }

    void audit(UUID tenantId, UUID aggregateId, String action, String replayHash, Object payload) {
        jdbc.update(
            "insert into eligibility.audit_record(tenant_id,audit_id,aggregate_id,action,replay_hash,payload_json,occurred_at) values (?,?,?,?,?,?::jsonb,?)",
            tenantId, UUID.randomUUID(), aggregateId, action, replayHash, json(payload), Timestamp.from(Instant.now())
        );
    }

    String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    <T> T readJson(String value, Class<T> type) {
        try {
            return mapper.readValue(value, type);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    <T> T readJson(String value, TypeReference<T> type) {
        try {
            return mapper.readValue(value, type);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
