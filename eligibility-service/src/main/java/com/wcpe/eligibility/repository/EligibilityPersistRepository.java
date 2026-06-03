package com.wcpe.eligibility.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.wcpe.eligibility.domain.models.EligibilityExplanationResponse;
import com.wcpe.eligibility.domain.models.EligibilityResult;
import com.wcpe.eligibility.domain.models.ProductFamily;
import com.wcpe.eligibility.domain.models.QuoteType;
import com.wcpe.eligibility.domain.models.RuleDecision;
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
