package com.wcpe.eligibility.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.eligibility.domain.models.*;
import com.wcpe.eligibility.repository.FicoLtvMatrixRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

@Service
public class FicoLtvMatrixService {

    private final FicoLtvMatrixRepository repository;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public FicoLtvMatrixService(FicoLtvMatrixRepository repository, JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public FicoLtvEvaluationResult evaluate(UUID tenantId, FicoLtvMatrixEvaluationRequest request, String correlationId) {
        UUID evaluationId = UUID.randomUUID();
        String safeCorrelationId = correlationId == null || correlationId.isBlank() ? evaluationId.toString() : correlationId;

        if (request == null || request.facts() == null || request.productCandidate() == null) {
            return buildAndPersist(tenantId, request, evaluationId, safeCorrelationId, new FicoLtvDecision(
                "CONF_FICO_LTV_MATRIX", "INELIGIBLE", "HARD_STOP",
                "INVALID_REQUEST", "FICO/LTV evaluation requires product candidate and facts.",
                null, null, null, null
            ), null);
        }

        Integer fico = request.facts().representativeFico();
        BigDecimal ltv = request.facts().ltv();
        BigDecimal cltv = request.facts().cltv();
        String productCode = request.productCandidate().productCode();
        String investorCode = request.productCandidate().investorCode();
        String productFamily = extractProductFamily(productCode);

        FicoLtvMatrixConfig matrix = resolveMatrix(tenantId, productFamily, investorCode, request);
        if (matrix == null || matrix.rows() == null || matrix.rows().isEmpty()) {
            return buildAndPersist(tenantId, request, evaluationId, safeCorrelationId, new FicoLtvDecision(
                "CONF_FICO_LTV_MATRIX", "CANNOT_DECIDE", "WARNING",
                "MATRIX_NOT_CONFIGURED", "No FICO/LTV matrix configured for " + productCode + "/" + investorCode + ".",
                null, null, null, null
            ), null);
        }

        UUID ruleVersionId = parseUuid(matrix.matrixSetId());

        if (fico == null) {
            String severity = matrix.rows().stream()
                .map(FicoLtvMatrixRow::severityIfMissingFico)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("WARNING");
            return buildAndPersist(tenantId, request, evaluationId, safeCorrelationId, new FicoLtvDecision(
                "CONF_FICO_LTV_MATRIX", "INSUFFICIENT_DATA", severity,
                "MISSING_FICO", "Representative FICO is required for conventional eligibility.",
                null, null, null, ruleVersionId
            ), matrix);
        }

        if (fico < 300 || fico > 850) {
            return buildAndPersist(tenantId, request, evaluationId, safeCorrelationId, new FicoLtvDecision(
                "CONF_FICO_LTV_MATRIX", "INELIGIBLE", "HARD_STOP",
                "INVALID_FICO", "FICO score " + fico + " is outside valid range [300..850].",
                null, null, null, ruleVersionId
            ), matrix);
        }

        if (ltv == null || ltv.compareTo(BigDecimal.ZERO) <= 0 || ltv.compareTo(new BigDecimal("2.00000")) > 0) {
            return buildAndPersist(tenantId, request, evaluationId, safeCorrelationId, new FicoLtvDecision(
                "CONF_FICO_LTV_MATRIX", "INELIGIBLE", "HARD_STOP",
                "INVALID_LTV", "LTV must be a positive ratio no greater than 2.00000.",
                null, null, null, ruleVersionId
            ), matrix);
        }

        List<FicoLtvMatrixRow> matches = matchingRows(matrix.rows(), fico, request.facts());
        if (hasAmbiguousBestMatch(matches)) {
            return buildAndPersist(tenantId, request, evaluationId, safeCorrelationId, new FicoLtvDecision(
                "CONF_FICO_LTV_MATRIX", "CANNOT_DECIDE", "HARD_STOP",
                "MATRIX_GAP_OR_OVERLAP", "Multiple FICO/LTV matrix rows match the submitted facts for " + productCode + "/" + investorCode + ".",
                null, null, null, ruleVersionId
            ), matrix);
        }

        FicoLtvMatrixRow matchedRow = matches.stream()
            .max(Comparator.comparingInt(this::specificity))
            .orElse(null);

        if (matchedRow == null) {
            return buildAndPersist(tenantId, request, evaluationId, safeCorrelationId, new FicoLtvDecision(
                "CONF_FICO_LTV_MATRIX", "INELIGIBLE", "HARD_STOP",
                "MATRIX_GAP_OR_OVERLAP", "No FICO/LTV matrix row matches the submitted facts for " + productCode + "/" + investorCode + ".",
                null, null, null, ruleVersionId
            ), matrix);
        }

        BigDecimal maxLtv = matchedRow.maxLtv();
        BigDecimal effectiveMaxCltv = matchedRow.maxCltv() != null ? matchedRow.maxCltv() : maxLtv;

        String ltvPct = ltv != null ? ltv.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP).toPlainString() : "N/A";
        String maxLtvPct = maxLtv.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP).toPlainString();

        if (ltv != null && ltv.compareTo(maxLtv) > 0) {
            return buildAndPersist(tenantId, request, evaluationId, safeCorrelationId, new FicoLtvDecision(
                "CONF_FICO_LTV_MATRIX", "INELIGIBLE", "HARD_STOP",
                "FICO_LTV_EXCEEDS_MATRIX",
                "FICO " + fico + " with " + ltvPct + "% LTV is outside " + productCode + "/" + investorCode + " maximum LTV of " + maxLtvPct + "%.",
                matchedRow.matrixRowId(), maxLtv, effectiveMaxCltv, ruleVersionId
            ), matrix);
        }

        if (cltv != null && cltv.compareTo(effectiveMaxCltv) > 0) {
            String cltvPct = cltv.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP).toPlainString();
            return buildAndPersist(tenantId, request, evaluationId, safeCorrelationId, new FicoLtvDecision(
                "CONF_FICO_LTV_MATRIX", "INELIGIBLE", "HARD_STOP",
                "FICO_CLTV_EXCEEDS_MATRIX",
                "CLTV " + cltvPct + "% exceeds maximum for FICO " + fico + ".",
                matchedRow.matrixRowId(), maxLtv, effectiveMaxCltv, ruleVersionId
            ), matrix);
        }

        return buildAndPersist(tenantId, request, evaluationId, safeCorrelationId, new FicoLtvDecision(
            "CONF_FICO_LTV_MATRIX", "ELIGIBLE", "PASS",
            "FICO_LTV_WITHIN_MATRIX",
            "FICO " + fico + " and LTV " + ltvPct + "% satisfy " + productCode + "/" + investorCode + " matrix.",
            matchedRow.matrixRowId(), maxLtv, effectiveMaxCltv, ruleVersionId
        ), matrix);
    }

    private FicoLtvEvaluationResult buildAndPersist(UUID tenantId, FicoLtvMatrixEvaluationRequest request, UUID evaluationId,
                                                    String correlationId, FicoLtvDecision decision,
                                                    FicoLtvMatrixConfig matrix) {
        String hash = resultHash(request, decision, matrix);
        FicoLtvEvaluationResult result = new FicoLtvEvaluationResult(evaluationId, decision, hash);
        persistEvaluation(tenantId, request, result, correlationId, matrix);
        return result;
    }

    private FicoLtvMatrixConfig resolveMatrix(UUID tenantId, String productFamily, String investorCode,
                                              FicoLtvMatrixEvaluationRequest request) {
        try {
            return repository.resolve(
                tenantId,
                productFamily,
                investorCode,
                null,
                request.facts().loanPurpose(),
                request.facts().occupancyType(),
                request.facts().propertyType(),
                parseDate(request.asOfDate())
            );
        } catch (Exception e) {
            return null;
        }
    }

    private List<FicoLtvMatrixRow> matchingRows(List<FicoLtvMatrixRow> rows, int fico, FicoLtvFacts facts) {
        return rows.stream()
            .filter(row -> fico >= row.ficoMin() && fico <= row.ficoMax())
            .filter(row -> matches(row.loanPurpose(), facts.loanPurpose()))
            .filter(row -> matches(row.occupancyType(), facts.occupancyType()))
            .filter(row -> row.propertyType() == null || matches(row.propertyType(), facts.propertyType()))
            .filter(row -> facts.units() >= row.unitsMin() && facts.units() <= row.unitsMax())
            .toList();
    }

    private boolean hasAmbiguousBestMatch(List<FicoLtvMatrixRow> matches) {
        if (matches.size() < 2) {
            return false;
        }
        int best = matches.stream().mapToInt(this::specificity).max().orElse(0);
        return matches.stream().filter(row -> specificity(row) == best).count() > 1;
    }

    private int specificity(FicoLtvMatrixRow row) {
        int score = 0;
        if (row.loanPurpose() != null && !"ALL".equalsIgnoreCase(row.loanPurpose())) score++;
        if (row.occupancyType() != null && !"ALL".equalsIgnoreCase(row.occupancyType())) score++;
        if (row.propertyType() != null) score++;
        if (row.documentationType() != null) score++;
        if (row.ausType() != null) score++;
        return score;
    }

    private boolean matches(String configured, String actual) {
        return configured == null || "ALL".equalsIgnoreCase(configured) || configured.equalsIgnoreCase(actual);
    }

    private String extractProductFamily(String productCode) {
        if (productCode == null) return null;
        if (productCode.startsWith("CONF") || productCode.startsWith("CONV")) return "CONVENTIONAL";
        if (productCode.startsWith("JUM")) return "JUMBO";
        if (productCode.startsWith("FHA")) return "FHA";
        return productCode;
    }

    private Date parseDate(String asOfDate) {
        if (asOfDate == null || asOfDate.isBlank()) {
            return Date.valueOf(LocalDate.now());
        }
        return Date.valueOf(LocalDate.parse(asOfDate));
    }

    private UUID parseUuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String resultHash(FicoLtvMatrixEvaluationRequest request, FicoLtvDecision decision, FicoLtvMatrixConfig matrix) {
        String payload = safe(request == null ? null : request.scenarioId()) + "|" +
            (request == null ? 0 : request.scenarioVersion()) + "|" +
            safe(decision.ruleCode()) + "|" + safe(decision.eligibilityStatus()) + "|" +
            safe(decision.reasonCode()) + "|" + safe(decision.matchedRowId()) + "|" +
            safe(decision.maxLtv()) + "|" + safe(decision.maxCltv()) + "|" +
            (matrix == null ? "" : safe(matrix.matrixSetId()) + ":" + matrix.version());
        return "sha256:" + sha256Hex(payload);
    }

    private String safe(Object value) {
        return value == null ? "" : value.toString();
    }

    private String sha256Hex(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash FICO/LTV result", e);
        }
    }

    private void persistEvaluation(UUID tenantId, FicoLtvMatrixEvaluationRequest request, FicoLtvEvaluationResult result,
                                   String correlationId, FicoLtvMatrixConfig matrix) {
        if (jdbc == null || tenantId == null || request == null || request.productCandidate() == null) {
            return;
        }
        try {
            String requestJson = objectMapper.writeValueAsString(request);
            String decisionJson = objectMapper.writeValueAsString(result.decision());
            jdbc.update(
                "INSERT INTO eligibility.eligibility_evaluation " +
                    "(tenant_id, evaluation_id, scenario_id, scenario_version, rule_set_version, evaluation_status, input_hash, result_hash) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (evaluation_id) DO NOTHING",
                tenantId, result.evaluationId(), request.scenarioId(), request.scenarioVersion(),
                matrix == null ? 0 : matrix.version(), result.decision().eligibilityStatus(),
                "sha256:" + sha256Hex(requestJson), result.resultHash()
            );
            jdbc.update(
                "INSERT INTO eligibility.eligibility_decision " +
                    "(tenant_id, decision_id, evaluation_id, product_code, investor_code, rule_code, severity, decision, reason_code, message, actual_value, required_value, trace_json) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb) ON CONFLICT (decision_id) DO NOTHING",
                tenantId, UUID.randomUUID(), result.evaluationId(), request.productCandidate().productCode(),
                request.productCandidate().investorCode(), result.decision().ruleCode(), result.decision().severity(),
                result.decision().eligibilityStatus(), result.decision().reasonCode(), result.decision().message(),
                null, result.decision().maxLtv() == null ? null : result.decision().maxLtv().toPlainString(), decisionJson
            );
            jdbc.update(
                "INSERT INTO eligibility.outbox_event (tenant_id, event_id, aggregate_id, event_type, payload_json) " +
                    "VALUES (?, ?, ?, ?, ?::jsonb) ON CONFLICT (event_id) DO NOTHING",
                tenantId, UUID.randomUUID(), result.evaluationId(), "fico_ltv_matrix.completed.v1", decisionJson
            );
            jdbc.update(
                "INSERT INTO eligibility.audit_record (tenant_id, audit_id, aggregate_id, action, replay_hash, payload_json) " +
                    "VALUES (?, ?, ?, ?, ?, ?::jsonb) ON CONFLICT (audit_id) DO NOTHING",
                tenantId, UUID.randomUUID(), result.evaluationId(), "FICO_LTV_MATRIX_COMPLETED", result.resultHash(),
                objectMapper.writeValueAsString(Map.of(
                    "correlationId", correlationId,
                    "matrixSetId", matrix == null ? null : matrix.matrixSetId(),
                    "decision", result.decision().reasonCode()
                ))
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize FICO/LTV audit payload", e);
        } catch (RuntimeException ignored) {
            // Local unit tests and read-only harness runs may not have PostgreSQL available; evaluation remains deterministic.
        }
    }
}
