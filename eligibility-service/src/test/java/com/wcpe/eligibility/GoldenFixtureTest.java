package com.wcpe.eligibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.eligibility.client.CatalogClient;
import com.wcpe.eligibility.domain.hashing.Hashing;
import com.wcpe.eligibility.domain.models.*;
import com.wcpe.eligibility.domain.rules.*;
import com.wcpe.eligibility.domain.ruleset.RuleEngine;
import com.wcpe.eligibility.evaluation.RequiredFactValidator;
import com.wcpe.eligibility.evaluation.ScopeValidator;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class GoldenFixtureTest {
    private static RuleEngine ruleEngine;
    private static ObjectMapper objectMapper;
    private static Path goldenDir;

    @BeforeAll
    static void setup() throws Exception {
        CatalogClient catalogClient = new CatalogClient();
        List<EligibilityRule> rules = List.of(
            new FicoMinimumRule(),
            new LtvRule(),
            new DtiRule(),
            new PropertyTypeRule(),
            new OccupancyRule(),
            new LoanPurposeRule(),
            new InvestorRule(catalogClient),
            new ProductRule(catalogClient),
            new ChannelRule(catalogClient),
            new StateRule(catalogClient),
            new LoanAmountRule(catalogClient),
            new DocumentationTypeRule()
        );
        ScopeValidator scopeValidator = new ScopeValidator(catalogClient);
        RequiredFactValidator factValidator = new RequiredFactValidator();
        ruleEngine = new RuleEngine(rules, scopeValidator, factValidator, catalogClient);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        goldenDir = Path.of(System.getProperty("user.dir"), "src", "test", "resources", "golden", "PII-01-eligibility-rules");
        if (!Files.exists(goldenDir)) {
            goldenDir = Path.of(System.getProperty("user.dir"), "golden", "PII-01-eligibility-rules");
        }
    }

    @Test void GF01_happy_path() { runFixture("GF01_happy_path.json", "ELIGIBLE", expectedSeveritiesP("PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS")); }
    @Test void GF02_high_fico_ltv_boundary() { runFixture("GF02_high_fico_ltv_boundary.json", "ELIGIBLE", expectedSeveritiesP("PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS")); }
    @Test void GF03_fico_at_min() { runFixture("GF03_fico_at_min.json", "ELIGIBLE", expectedSeveritiesP("PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS")); }
    @Test void GF04_fico_below_min() { runFixture("GF04_fico_below_min.json", "INELIGIBLE", expectedSeveritiesP("HARD_STOP", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS")); }
    @Test void GF05_high_dti() { runFixture("GF05_high_dti.json", "INELIGIBLE", expectedSeveritiesP("PASS", "PASS", "HARD_STOP", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS")); }
    @Test void GF06_investment_property() { runFixture("GF06_investment_property.json", "ELIGIBLE", expectedSeveritiesP("PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS")); }
    @Test void GF07_cash_out_refi_eligible() { runFixture("GF07_cash_out_refi_eligible.json", "ELIGIBLE", expectedSeveritiesP("PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS")); }
    @Test void GF08_cash_out_exceeds_limits() { runFixture("GF08_cash_out_exceeds_limits.json", "INELIGIBLE", expectedSeveritiesP("PASS", "HARD_STOP", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS")); }
    @Test void GF09_investor_suspended() { runFixture("GF09_investor_suspended.json", "INELIGIBLE", expectedSeveritiesP("PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "HARD_STOP", "PASS", "PASS", "PASS", "PASS", "PASS")); }
    @Test void GF10_product_allows_channel() { runFixture("GF10_product_not_allowing_channel.json", "ELIGIBLE", expectedSeveritiesP("PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS")); }
    @Test void GF11_state_not_allowed() { runFixture("GF11_state_not_allowed.json", "INELIGIBLE", expectedSeveritiesP("PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "HARD_STOP", "WARNING", "PASS")); }
    @Test void GF12_loan_amount_exceeds() { runFixture("GF12_loan_amount_exceeds.json", "INELIGIBLE", expectedSeveritiesP("PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "HARD_STOP", "PASS")); }
    @Test void GF13_low_doc_not_allowed() { runFixture("GF13_low_doc_not_allowed.json", "INELIGIBLE", expectedSeveritiesP("PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "HARD_STOP")); }
    @Test void GF14_condo_eligible() { runFixture("GF14_condo_eligible.json", "ELIGIBLE", expectedSeveritiesP("PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS")); }
    @Test void GF15_missing_fico_warning() { runFixture("GF15_missing_fico_warning.json", "WARNING", expectedSeveritiesP("WARNING", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS", "PASS")); }

    private Map<String, String> expectedSeveritiesP(String r01, String r02, String r03, String r04, String r05, String r06,
            String r07, String r08, String r09, String r10, String r11, String r12) {
        return Map.ofEntries(
            Map.entry("R01", r01), Map.entry("R02", r02), Map.entry("R03", r03), Map.entry("R04", r04),
            Map.entry("R05", r05), Map.entry("R06", r06), Map.entry("R07", r07), Map.entry("R08", r08),
            Map.entry("R09", r09), Map.entry("R10", r10), Map.entry("R11", r11), Map.entry("R12", r12)
        );
    }

    private void runFixture(String filename, String expectedStatus, Map<String, String> expectedSeverities) {
        Path fixturePath = goldenDir.resolve(filename);
        String fixtureId = filename.replace(".json", "");

        if (!Files.exists(fixturePath)) {
            Path altPath = Path.of(System.getProperty("user.dir"), "src", "test", "resources", "golden", "PII-01-eligibility-rules", filename);
            if (Files.exists(altPath)) fixturePath = altPath;
        }

        assertTrue(Files.exists(fixturePath), "Fixture file must exist: " + fixturePath);

        JsonNode fixture;
        try {
            String json = Files.readString(fixturePath);
            fixture = objectMapper.readTree(json);
        } catch (Exception e) {
            fail("Failed to read fixture " + filename + ": " + e.getMessage());
            return;
        }

        JsonNode requestNode = fixture.get("request");
        EligibilityRequest request = null;
        try {
            request = objectMapper.treeToValue(requestNode, EligibilityRequest.class);
        } catch (Exception e) {
            fail("Failed to parse golden fixture " + filename + ": " + e.getMessage());
            return;
        }

        EligibilityResult result = ruleEngine.evaluate(request, UUID.randomUUID());

        assertEquals(expectedStatus, result.status(),
            "Fixture " + fixtureId + " status mismatch");

        Map<String, String> actualSeverities = new HashMap<>();
        for (RuleDecision d : result.decisions()) {
            actualSeverities.put(d.ruleCode(), d.severity());
        }

        for (Map.Entry<String, String> entry : expectedSeverities.entrySet()) {
            assertEquals(entry.getValue(), actualSeverities.get(entry.getKey()),
                "Fixture " + fixtureId + " severity mismatch for " + entry.getKey());
        }

        assertNotNull(result.resultHash());
        assertTrue(result.resultHash().startsWith("sha256:"));
        assertEquals(result.decisions().size(), expectedSeverities.size(),
            "All rules should produce a decision");
    }
}
