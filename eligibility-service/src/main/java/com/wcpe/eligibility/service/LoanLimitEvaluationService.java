package com.wcpe.eligibility.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.eligibility.domain.hashing.Hashing;
import com.wcpe.eligibility.domain.models.LoanLimitDecision;
import com.wcpe.eligibility.domain.models.LoanLimitEvaluationRequest;
import com.wcpe.eligibility.domain.models.LoanLimitEvaluationResult;
import com.wcpe.eligibility.domain.models.LoanLimitFacts;
import com.wcpe.eligibility.domain.models.LoanLimitProductCandidate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LoanLimitEvaluationService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public LoanLimitEvaluationService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper.findAndRegisterModules();
    }

    @Transactional
    public LoanLimitEvaluationResult evaluate(UUID tenantId, LoanLimitEvaluationRequest request, String correlationId) {
        validateRequest(request);
        LocalDate asOfDate = LocalDate.parse(request.asOfDate());
        UUID evaluationId = UUID.randomUUID();
        List<LoanLimitDecision> decisions = new ArrayList<>();

        for (LoanLimitProductCandidate candidate : request.productCandidates()) {
            decisions.add(evaluateCandidate(tenantId, request.facts(), candidate, asOfDate));
        }

        String resultHash = resultHash(request, decisions);
        LoanLimitEvaluationResult result = new LoanLimitEvaluationResult(evaluationId, "COMPLETED", decisions, resultHash);
        persistEvaluation(tenantId, request, result, correlationId);
        return result;
    }

    private LoanLimitDecision evaluateCandidate(UUID tenantId, LoanLimitFacts facts, LoanLimitProductCandidate candidate, LocalDate asOfDate) {
        UUID productVersionId = candidate.productVersionId();
        if (facts.loanAmount() == null || facts.propertyState() == null || facts.propertyState().isBlank()) {
            return decision(candidate, "INSUFFICIENT_DATA", "WARNING", "MISSING_LOAN_AMOUNT_OR_STATE",
                "Loan amount and property state are required for loan-limit evaluation.", null, facts.loanAmount(), null, null);
        }
        if (facts.units() == null || facts.units() < 1 || facts.units() > 4) {
            return decision(candidate, "INSUFFICIENT_DATA", "WARNING", "INVALID_UNIT_COUNT",
                "Unit count must be between 1 and 4.", null, facts.loanAmount(), null, null);
        }

        String state = facts.propertyState().trim().toUpperCase();
        String county = facts.propertyCounty() == null ? null : facts.propertyCounty().trim();
        if ((county == null || county.isBlank()) && hasCountySpecificLimits(tenantId, state, facts.units(), asOfDate)) {
            return decision(candidate, "INSUFFICIENT_DATA", "WARNING", "MISSING_COUNTY",
                "County is required because published county-specific loan limits exist for " + state + ".",
                null, facts.loanAmount(), null, null);
        }

        List<LimitRow> rows = resolveRows(tenantId, candidate.investorCode(), state, county, facts.units(), asOfDate);
        if (rows.isEmpty()) {
            return decision(candidate, "INELIGIBLE", "HARD_STOP", "LIMIT_NOT_CONFIGURED",
                "No published conforming loan limit is configured for " + location(state, county, facts.units()) + ".",
                null, facts.loanAmount(), null, null);
        }

        int bestPriority = rows.get(0).agency().equals(candidate.investorCode()) ? 0 : 1;
        List<LimitRow> bestRows = rows.stream()
            .filter(row -> (row.agency().equals(candidate.investorCode()) ? 0 : 1) == bestPriority)
            .toList();
        if (bestRows.size() > 1) {
            return decision(candidate, "INELIGIBLE", "HARD_STOP", "OVERLAPPING_LIMIT_VERSION",
                "Multiple published conforming loan-limit versions match " + location(state, county, facts.units()) + ".",
                null, facts.loanAmount(), null, bestRows.get(0).limitSetId());
        }

        LimitRow row = bestRows.get(0);
        boolean eligible = facts.loanAmount().compareTo(row.limitAmount()) <= 0;
        if (eligible) {
            return decision(candidate, "ELIGIBLE", "PASS", "LOAN_LIMIT_WITHIN_LIMIT",
                "Loan amount " + money(facts.loanAmount()) + " is within " + money(row.limitAmount()) + " for " + location(state, county, facts.units()) + ".",
                row.limitAmount(), facts.loanAmount(), row.limitRowId(), row.limitSetId());
        }
        return decision(candidate, "INELIGIBLE", "HARD_STOP", "LOAN_AMOUNT_EXCEEDS_CONFORMING_LIMIT",
            "Loan amount " + money(facts.loanAmount()) + " exceeds " + money(row.limitAmount()) + " for " + location(state, county, facts.units()) + ".",
            row.limitAmount(), facts.loanAmount(), row.limitRowId(), row.limitSetId());
    }

    private boolean hasCountySpecificLimits(UUID tenantId, String state, Integer units, LocalDate asOfDate) {
        Integer count = jdbc.queryForObject(
            "select count(*) from eligibility.conforming_loan_limit_set s " +
                "join eligibility.conforming_loan_limit_row r on r.limit_set_id = s.limit_set_id and r.tenant_id = s.tenant_id " +
                "where s.tenant_id = ? and s.status = 'PUBLISHED' and s.effective_from <= ? " +
                "and (s.effective_to is null or s.effective_to >= ?) and r.state_code = ? and r.units = ? and r.county_name is not null",
            Integer.class, tenantId, asOfDate, asOfDate, state, units);
        return count != null && count > 0;
    }

    private List<LimitRow> resolveRows(UUID tenantId, String investorCode, String state, String county, Integer units, LocalDate asOfDate) {
        List<LimitRow> countyRows = List.of();
        if (county != null && !county.isBlank()) {
            countyRows = queryRows(tenantId, investorCode, state, county, units, asOfDate, true);
        }
        if (!countyRows.isEmpty()) {
            return countyRows;
        }
        return queryRows(tenantId, investorCode, state, null, units, asOfDate, false);
    }

    private List<LimitRow> queryRows(UUID tenantId, String investorCode, String state, String county, Integer units, LocalDate asOfDate, boolean countySpecific) {
        String countyPredicate = countySpecific ? "and lower(r.county_name) = lower(?) " : "and r.county_name is null ";
        Object[] args = countySpecific
            ? new Object[] {tenantId, investorCode, state, units, asOfDate, asOfDate, county}
            : new Object[] {tenantId, investorCode, state, units, asOfDate, asOfDate};
        return jdbc.query(
            "select s.limit_set_id, s.agency, r.limit_row_id, r.limit_amount, s.effective_from, s.version " +
                "from eligibility.conforming_loan_limit_set s " +
                "join eligibility.conforming_loan_limit_row r on r.limit_set_id = s.limit_set_id and r.tenant_id = s.tenant_id " +
                "where s.tenant_id = ? and s.agency in (?, 'GENERIC') and s.status = 'PUBLISHED' " +
                "and r.state_code = ? and r.units = ? and s.effective_from <= ? and (s.effective_to is null or s.effective_to >= ?) " + countyPredicate +
                "order by case when s.agency = ? then 0 else 1 end, s.effective_from desc, s.version desc",
            (rs, rowNum) -> new LimitRow(
                rs.getObject("limit_set_id", UUID.class),
                rs.getString("agency"),
                rs.getObject("limit_row_id", UUID.class),
                rs.getBigDecimal("limit_amount")
            ),
            appendInvestor(args, investorCode)
        ).stream().sorted(Comparator.comparing(row -> row.agency().equals(investorCode) ? 0 : 1)).toList();
    }

    private Object[] appendInvestor(Object[] args, String investorCode) {
        Object[] out = new Object[args.length + 1];
        System.arraycopy(args, 0, out, 0, args.length);
        out[args.length] = investorCode;
        return out;
    }

    private LoanLimitDecision decision(LoanLimitProductCandidate candidate, String status, String severity, String reasonCode, String message,
                                       BigDecimal limitAmount, BigDecimal actualAmount, UUID matchedRowId, UUID ruleVersionId) {
        return new LoanLimitDecision(candidate.productVersionId(), candidate.productCode(), candidate.investorCode(),
            "CONF_LOAN_LIMIT", status, severity, reasonCode, message,
            limitAmount, actualAmount, matchedRowId, ruleVersionId);
    }

    private void validateRequest(LoanLimitEvaluationRequest request) {
        if (request == null || request.scenarioId() == null || request.scenarioVersion() < 1 || request.asOfDate() == null ||
            request.productCandidates() == null || request.productCandidates().isEmpty() || request.facts() == null) {
            throw new IllegalArgumentException("scenarioId, scenarioVersion, asOfDate, productCandidates, and facts are required.");
        }
    }

    private void persistEvaluation(UUID tenantId, LoanLimitEvaluationRequest request, LoanLimitEvaluationResult result, String correlationId) {
        String requestHash = Hashing.sha256(json(request));
        jdbc.update(
            "insert into eligibility.eligibility_evaluation(tenant_id,evaluation_id,scenario_id,scenario_version,rule_set_version,evaluation_status,input_hash,result_hash) values (?,?,?,?,?,?,?,?)",
            tenantId, result.evaluationId(), request.scenarioId(), request.scenarioVersion(), 1, result.status(), requestHash, result.resultHash()
        );
        for (LoanLimitDecision decision : result.decisions()) {
            jdbc.update(
                "insert into eligibility.eligibility_decision(tenant_id,decision_id,evaluation_id,product_code,investor_code,rule_code,severity,decision,reason_code,message,actual_value,required_value,trace_json) values (?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb)",
                tenantId, UUID.randomUUID(), result.evaluationId(), decision.productCode(), decision.investorCode(), decision.ruleCode(), decision.severity(),
                decision.eligibilityStatus(), decision.reasonCode(), decision.message(), value(decision.actualAmount()), value(decision.limitAmount()),
                json(Map.of("matchedRowId", nullable(decision.matchedRowId()), "ruleVersionId", nullable(decision.ruleVersionId())))
            );
        }
        jdbc.update(
            "insert into eligibility.audit_record(tenant_id,audit_id,aggregate_id,action,replay_hash,payload_json,occurred_at) values (?,?,?,?,?,?::jsonb,?)",
            tenantId, UUID.randomUUID(), result.evaluationId(), "LOAN_LIMIT_CHECK_COMPLETED", result.resultHash(),
            json(Map.of("correlationId", correlationId == null || correlationId.isBlank() ? "not-provided" : correlationId,
                "scenarioId", request.scenarioId().toString(), "decisionCount", result.decisions().size())), Timestamp.from(Instant.now())
        );
    }

    private String resultHash(LoanLimitEvaluationRequest request, List<LoanLimitDecision> decisions) {
        Map<String, Object> material = new LinkedHashMap<>();
        material.put("scenarioId", request.scenarioId());
        material.put("scenarioVersion", request.scenarioVersion());
        material.put("asOfDate", request.asOfDate());
        material.put("facts", request.facts());
        material.put("decisions", decisions);
        return Hashing.sha256(json(material));
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String location(String state, String county, Integer units) {
        return state + "/" + (county == null || county.isBlank() ? "*" : county.trim()) + "/" + units + " unit" + (units == 1 ? "" : "s");
    }

    private String money(BigDecimal value) {
        return value.setScale(2).toPlainString();
    }

    private String value(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private String nullable(UUID value) {
        return value == null ? "" : value.toString();
    }

    private record LimitRow(UUID limitSetId, String agency, UUID limitRowId, BigDecimal limitAmount) {}
}
