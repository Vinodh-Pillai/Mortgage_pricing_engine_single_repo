package com.wcpe.eligibility;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.eligibility.client.CatalogClient;
import com.wcpe.eligibility.domain.models.EligibilityRequest;
import com.wcpe.eligibility.domain.models.EligibilityResult;
import com.wcpe.eligibility.domain.rules.*;
import com.wcpe.eligibility.domain.ruleset.RuleEngine;
import com.wcpe.eligibility.evaluation.RequiredFactValidator;
import com.wcpe.eligibility.evaluation.ScopeValidator;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Disabled("PropertyTypeRule now requires PropertyTypeRuleService - PII-03-S05 integration incomplete")
class ReplayDeterminismTest {

    private static final String GOLDEN_DIR = "golden/PII-01-eligibility-rules";
    private static final String GF01_PATH = GOLDEN_DIR + "/GF01_happy_path.json";
    private static final String GF04_PATH = GOLDEN_DIR + "/GF04_fico_below_min.json";
    private static final String GF09_PATH = GOLDEN_DIR + "/GF09_investor_suspended.json";

    private static ObjectMapper objectMapper;
    private static RuleEngine ruleEngine;
    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeAll
    static void setup() throws Exception {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        CatalogClient catalogClient = new CatalogClient();
        ScopeValidator scopeValidator = new ScopeValidator(catalogClient);
        RequiredFactValidator factValidator = new RequiredFactValidator();
        List<EligibilityRule> rules = List.of(
            new FicoMinimumRule(),
            new LtvRule(),
            new DtiRule(),
            // new PropertyTypeRule(), // requires PropertyTypeRuleService
            new OccupancyRule(),
            new LoanPurposeRule(),
            new InvestorRule(catalogClient),
            new ProductRule(catalogClient),
            new ChannelRule(catalogClient),
            new StateRule(catalogClient),
            new LoanAmountRule(catalogClient),
            new DocumentationTypeRule()
        );
        ruleEngine = new RuleEngine(rules, scopeValidator, factValidator, catalogClient);
    }

    /* ---- Same EligibilityRequest + same policy_version_id produces identical resultHash ---- */

    @Test
    void sameRequestProducesIdenticalResultHash() throws Exception {
        EligibilityRequest request = loadRequest(GF01_PATH);

        EligibilityResult result1 = ruleEngine.evaluate(request, TENANT);
        EligibilityResult result2 = ruleEngine.evaluate(request, TENANT);

        assertEquals(result1.resultHash(), result2.resultHash(),
            "Same request should produce identical resultHash for deterministic rules");
    }

    @Test
    void sameRequestMultipleIterationsProducesIdenticalResultHash() throws Exception {
        EligibilityRequest request = loadRequest(GF01_PATH);

        String firstHash = null;
        for (int i = 0; i < 5; i++) {
            EligibilityResult result = ruleEngine.evaluate(request, TENANT);
            if (firstHash == null) {
                firstHash = result.resultHash();
            } else {
                assertEquals(firstHash, result.resultHash(),
                    "Iteration " + i + " produced different resultHash");
            }
        }
    }

    @Test
    void sameRequestAcrossTenantIdProducesIdenticalResultHash() throws Exception {
        EligibilityRequest request = loadRequest(GF01_PATH);

        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        EligibilityResult resultA = ruleEngine.evaluate(request, tenantA);
        EligibilityResult resultB = ruleEngine.evaluate(request, tenantB);

        assertEquals(resultA.resultHash(), resultB.resultHash(),
            "Result hash is content-based and should be identical across tenants");
    }

    /* ---- POST /evaluate hash matches replay result ---- */

    @Test
    void evaluateHashMatchesReplayResult() throws Exception {
        String fixtureJson = Files.readString(Path.of(GF01_PATH));
        EligibilityRequest request = loadRequest(GF01_PATH);

        // Simulate POST /evaluate
        EligibilityResult evaluateResult = ruleEngine.evaluate(request, TENANT);
        String evaluateHash = evaluateResult.resultHash();

        // Simulate POST /replay with same deserialized request
        EligibilityResult replayResult = ruleEngine.evaluate(request, TENANT);
        String replayHash = replayResult.resultHash();

        assertEquals(evaluateHash, replayHash,
            "Evaluate resultHash must match replay resultHash for the same request");
    }

    /* ---- Different policy_version_id (different input producing different decisions) produces different resultHash ---- */

    @Test
    void differentFicoProducesDifferentResultHash() throws Exception {
        EligibilityRequest happyRequest = loadRequest(GF01_PATH);
        EligibilityRequest failRequest = loadRequest(GF04_PATH);

        EligibilityResult happyResult = ruleEngine.evaluate(happyRequest, TENANT);
        EligibilityResult failResult = ruleEngine.evaluate(failRequest, TENANT);

        assertNotEquals(happyResult.resultHash(), failResult.resultHash(),
            "Different input profiles must produce different resultHash");
    }

    @Test
    void differentInvestorProducesDifferentResultHash() throws Exception {
        EligibilityRequest fnmaRequest = loadRequest(GF01_PATH);
        EligibilityRequest suspendedRequest = loadRequest(GF09_PATH);

        EligibilityResult fnmaResult = ruleEngine.evaluate(fnmaRequest, TENANT);
        EligibilityResult suspendedResult = ruleEngine.evaluate(suspendedRequest, TENANT);

        assertNotEquals(fnmaResult.resultHash(), suspendedResult.resultHash(),
            "Different investor status must produce different resultHash");
    }

    /* ---- helper ---- */

    private EligibilityRequest loadRequest(String path) throws Exception {
        return objectMapper.readValue(
            Files.readString(Path.of(path)),
            RequestWrapper.class
        ).getRequest();
    }

    /** Minimal wrapper to extract the "request" field from golden fixture JSON. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RequestWrapper {
        private EligibilityRequest request;

        public EligibilityRequest getRequest() { return request; }
        public void setRequest(EligibilityRequest request) { this.request = request; }
    }
}
