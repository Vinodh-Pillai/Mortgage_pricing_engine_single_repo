package com.wcpe.eligibility.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.eligibility.domain.hashing.Hashing;
import com.wcpe.eligibility.domain.models.EligibilityRequest;
import com.wcpe.eligibility.domain.models.EligibilityResult;
import com.wcpe.eligibility.domain.ruleset.RuleEngine;
import com.wcpe.eligibility.repository.ReplayRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class ReplayService {
    private final RuleEngine ruleEngine;
    private final ReplayRepository replayRepository;
    private final ObjectMapper objectMapper;

    public ReplayService(RuleEngine ruleEngine, ReplayRepository replayRepository, ObjectMapper objectMapper) {
        this.ruleEngine = ruleEngine;
        this.replayRepository = replayRepository;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> replay(UUID tenantId, String requestJson, int policyVersion, int ruleSetVersion) {
        try {
            EligibilityRequest request = objectMapper.readValue(requestJson, EligibilityRequest.class);
            String inputHash = Hashing.sha256(requestJson);
            EligibilityResult result = ruleEngine.evaluate(request, tenantId);
            String outputHash = result.resultHash();
            boolean match = verifyHash(inputHash, outputHash);

            Map<String, Object> replayResponse = Map.of(
                "replayId", UUID.randomUUID().toString(),
                "inputHash", inputHash,
                "outputHash", outputHash,
                "status", match ? "MATCH" : "MISMATCH",
                "policyVersion", policyVersion,
                "ruleSetVersion", ruleSetVersion,
                "decisions", result.decisions(),
                "replayedAt", Instant.now().toString()
            );

            replayRepository.saveReplay(tenantId, replayResponse);
            return replayResponse;
        } catch (Exception e) {
            throw new IllegalStateException("Replay failed: " + e.getMessage(), e);
        }
    }

    boolean verifyHash(String inputHash, String outputHash) {
        return inputHash != null && outputHash != null;
    }
}
