package com.wcpe.eligibility.domain.ruleset;

import com.wcpe.eligibility.domain.models.EligibilityRequest;
import com.wcpe.eligibility.domain.models.EligibilityResult;
import com.wcpe.eligibility.domain.models.RuleDecision;
import com.wcpe.eligibility.domain.hashing.Hashing;
import com.wcpe.eligibility.domain.rules.EligibilityRule;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class RuleEngine {
    private final List<EligibilityRule> rules;

    public RuleEngine(List<EligibilityRule> rules) {
        this.rules = rules.stream()
            .sorted((a, b) -> a.getRuleType().getCode().compareTo(b.getRuleType().getCode()))
            .toList();
    }

    public EligibilityResult evaluate(EligibilityRequest request, UUID tenantId) {
        String productCode = request.productCandidate().productCode();
        String investorCode = request.productCandidate().investorCode();

        List<RuleDecision> decisions = new ArrayList<>();
        boolean hasHardStop = false;
        boolean hasWarning = false;

        for (EligibilityRule rule : rules) {
            RuleDecision decision = rule.evaluate(request, productCode, investorCode);
            decisions.add(decision);

            if ("HARD_STOP".equals(decision.severity())) {
                hasHardStop = true;
            }
            if ("WARNING".equals(decision.severity()) && !hasHardStop) {
                hasWarning = true;
            }
        }

        String status;
        if (hasHardStop) {
            status = "INELIGIBLE";
        } else if (hasWarning) {
            status = "WARNING";
        } else {
            status = "ELIGIBLE";
        }

        String resultHash = Hashing.sha256(decisionsToString(decisions));
        String requestHash = Hashing.sha256(toString(request));

        return new EligibilityResult(
            UUID.randomUUID(),
            tenantId.toString(),
            status,
            decisions,
            resultHash,
            requestHash,
            Instant.now()
        );
    }

    public List<EligibilityRule> getRules() {
        return rules;
    }

    private String decisionsToString(List<RuleDecision> decisions) {
        StringBuilder sb = new StringBuilder();
        for (RuleDecision d : decisions) {
            sb.append(d.ruleCode()).append(":").append(d.status()).append(":").append(d.severity()).append("|");
        }
        return sb.toString();
    }

    private String toString(EligibilityRequest request) {
        StringBuilder sb = new StringBuilder();
        if (request.borrowerProfile() != null) {
            sb.append("fico:").append(request.borrowerProfile().representativeFico()).append("|");
        }
        if (request.propertyProfile() != null) {
            sb.append("state:").append(request.propertyProfile().state()).append("|");
            sb.append("price:").append(request.propertyProfile().purchasePrice()).append("|");
        }
        if (request.loanProfile() != null) {
            sb.append("loanAmt:").append(request.loanProfile().loanAmount()).append("|");
        }
        return sb.toString();
    }
}
