package com.wcpe.eligibility.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.eligibility.domain.models.EligibilityResult;
import com.wcpe.eligibility.domain.models.RuleDecision;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
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
}
