package com.wcpe.eligibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Pii03S10ContractRegressionPackTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final Path WORKSPACE_ROOT = Path.of(System.getProperty("user.dir")).resolve("..").resolve("..").normalize();
    private static final Path API_DIR = WORKSPACE_ROOT.resolve("contracts/pii03/api");
    private static final Path EVENTS_DIR = WORKSPACE_ROOT.resolve("contracts/pii03/events");
    private static final Path FIXTURES_DIR = WORKSPACE_ROOT.resolve("test-fixtures/pii03");
    private static final Path UI_DIR = WORKSPACE_ROOT.resolve("ui/e2e/pii03");

    @Test
    void validatesAllApiFixtures() throws Exception {
        List<String> requiredFixtures = List.of(
            "post-quotes-conventional-purchase-success.v1.json",
            "post-quotes-validation-failed.v1.json",
            "post-quotes-idempotency-conflict.v1.json",
            "get-eligibility-explanation-success.v1.json",
            "rule-evaluation-fixtures.v1.json"
        );

        for (String fixture : requiredFixtures) {
            Path fixturePath = API_DIR.resolve(fixture);
            assertTrue(Files.exists(fixturePath), "Missing API contract fixture: " + fixture);
            JsonNode root = readJson(fixturePath);
            assertNotNull(root.get("name"), "Fixture must identify itself: " + fixture);
        }

        JsonNode success = readJson(API_DIR.resolve("post-quotes-conventional-purchase-success.v1.json"));
        JsonNode validation = readJson(API_DIR.resolve("post-quotes-validation-failed.v1.json"));
        JsonNode explanation = readJson(API_DIR.resolve("get-eligibility-explanation-success.v1.json"));

        assertAll(
            () -> assertEquals("POST", success.path("method").asText(), "Quote success fixture must assert POST"),
            () -> assertTrue(success.path("path").asText().contains("{tenantId}"), "Quote path must be tenant scoped"),
            () -> assertContainsText(success.path("requiredHeaders"), "Authorization", "Quote success fixture must require Authorization"),
            () -> assertContainsText(success.path("requiredHeaders"), "Idempotency-Key", "Quote command fixture must require Idempotency-Key"),
            () -> assertTrue(success.at("/response/body/quoteOptions/0/loanAmount").isTextual(), "Money values must be strings"),
            () -> assertTrue(success.at("/response/body/quoteOptions/0/ltvRatio").isTextual(), "Percentage ratios must be strings"),
            () -> assertEquals("VALIDATION_FAILED", validation.at("/response/body/code").asText(), "Validation fixture must use problem-details code"),
            () -> assertTrue(validation.at("/response/body/type").asText().startsWith("https://pricing/errors/"), "Validation fixture must use problem-details type"),
            () -> assertEquals("GET", explanation.path("method").asText(), "Explanation fixture must assert GET"),
            () -> assertTrue(explanation.at("/response/body/audit/resultHash").asText().startsWith("sha256:"), "Explanation fixture must carry replay result hash")
        );
    }

    @Test
    void validatesAllEventSchemas() throws Exception {
        JsonNode contractPack = readJson(EVENTS_DIR.resolve("pii03-event-contract-pack.v1.json"));
        List<String> requiredEvents = List.of(
            "ScenarioSubmitted.v1",
            "ScenarioValidated.v1",
            "QuoteEligibilityRequested.v1",
            "QuoteEligibilityShellCreated.v1",
            "LoanLimitSetPublished.v1",
            "FicoLtvMatrixPublished.v1",
            "OccupancyPurposeRuleSetPublished.v1",
            "PropertyTypeRuleSetPublished.v1",
            "InvestorOverlaySetPublished.v1",
            "EligibilityCacheInvalidated.v1",
            "EligibilityExplanationViewed.v1",
            "EligibilityRuleModuleRegistered.v1"
        );
        List<String> requiredEnvelopeFields = List.of(
            "eventId",
            "eventType",
            "eventVersion",
            "tenantId",
            "aggregateId",
            "aggregateVersion",
            "correlationId",
            "causationId",
            "schemaUri",
            "occurredAtUtc"
        );

        requiredEvents.forEach(event -> assertContainsText(contractPack.path("events"), event, "Missing event schema contract: " + event));
        requiredEnvelopeFields.forEach(field -> assertContainsText(contractPack.path("requiredEnvelopeFields"), field, "Missing event envelope field: " + field));
        JsonNode example = contractPack.at("/compatibilityExamples/0");
        assertAll(
            () -> assertEquals("QuoteEligibilityShellCreated", example.path("eventType").asText(), "Compatibility example must name the event type without version suffix"),
            () -> assertTrue(example.path("schemaUri").asText().endsWith("QuoteEligibilityShellCreated.v1.json"), "Compatibility example must pin schema URI"),
            () -> assertTrue(example.at("/payload/resultHash").asText().startsWith("sha256:"), "Event payload must carry result hash evidence")
        );
    }

    @Test
    void validatesSyntheticReplayManifestAndFixtures() throws Exception {
        List<String> requiredFixtures = List.of(
            "tenant-active-retail.sql",
            "product-catalog-conventional-purchase.sql",
            "loan-limit-2026.sql",
            "fico-ltv-matrix-fnma.sql",
            "occupancy-purpose-rules.sql",
            "property-type-rules.sql",
            "investor-overlays.sql",
            "scenario-conventional-purchase-happy.json",
            "scenario-loan-limit-fail.json",
            "scenario-missing-fico.json",
            "scenario-condo-warning.json",
            "scenario-investor-overlay-fail.json",
            "replay-manifest.json"
        );

        requiredFixtures.forEach(fixture -> assertTrue(Files.exists(FIXTURES_DIR.resolve(fixture)), "Missing PII-03 fixture: " + fixture));
        JsonNode manifest = readJson(FIXTURES_DIR.resolve("replay-manifest.json"));
        assertFalse(manifest.path("entries").isEmpty(), "Replay manifest must contain entries");

        for (JsonNode entry : manifest.path("entries")) {
            assertAll(
                () -> assertTrue(entry.path("canonicalInputHash").asText().startsWith("sha256:"), "Manifest entry must pin canonical input hash"),
                () -> assertTrue(entry.path("configVersionGraphHash").asText().startsWith("sha256:"), "Manifest entry must pin config graph hash"),
                () -> assertTrue(entry.path("expectedResultHash").asText().startsWith("sha256:"), "Manifest entry must pin result hash"),
                () -> assertContainsText(entry.path("expectedEventSequence"), "QuoteEligibilityShellCreated", "Manifest entry must assert quote shell event")
            );
        }
    }

    @Test
    void noRealPiiOrForbiddenFixtureFields() throws Exception {
        Set<String> forbiddenFragments = Set.of(
            "ssn",
            "socialsecurity",
            "dateofbirth",
            "dob",
            "firstname",
            "lastname",
            "creditreport"
        );

        try (Stream<Path> paths = Files.walk(FIXTURES_DIR)) {
            List<Path> fixtureFiles = paths.filter(Files::isRegularFile).collect(Collectors.toList());
            assertFalse(fixtureFiles.isEmpty(), "PII-03 fixtures must exist before redaction scan runs");
            for (Path fixtureFile : fixtureFiles) {
                String normalized = Files.readString(fixtureFile).toLowerCase().replace("_", "").replace("-", "");
                for (String forbidden : forbiddenFragments) {
                    assertFalse(normalized.contains(forbidden), "Fixture must not contain real-PII-shaped field: " + fixtureFile + " -> " + forbidden);
                }
            }
        }
    }

    @Test
    void uiContractSpecsDeclareRequiredTestIds() throws Exception {
        List<String> requiredSpecs = List.of(
            "pricing-workbench-new-conventional-purchase.spec.ts",
            "pricing-workbench-validation.spec.ts",
            "quote-result-option-details.spec.ts",
            "quote-eligibility-explanation.spec.ts"
        );
        List<String> requiredTestIds = List.of(
            "new-quote-form",
            "representative-fico-input",
            "property-type-select",
            "occupancy-type-select",
            "loan-amount-input",
            "submit-quote-button",
            "quote-option-card",
            "eligibility-explain-button",
            "eligibility-explanation-panel"
        );

        StringBuilder combinedSpecs = new StringBuilder();
        for (String spec : requiredSpecs) {
            Path specPath = UI_DIR.resolve(spec);
            assertTrue(Files.exists(specPath), "Missing PII-03 UI contract spec: " + spec);
            combinedSpecs.append(Files.readString(specPath)).append('\n');
        }

        String allSpecText = combinedSpecs.toString();
        requiredTestIds.forEach(testId -> assertTrue(allSpecText.contains(testId), "UI contract specs must assert test id: " + testId));
    }

    private static JsonNode readJson(Path path) throws IOException {
        return OBJECT_MAPPER.readTree(Files.readString(path));
    }

    private static void assertContainsText(JsonNode arrayNode, String expected, String message) {
        assertTrue(arrayNode.isArray(), message + " (not an array)");
        for (JsonNode element : arrayNode) {
            if (expected.equals(element.asText())) {
                return;
            }
        }
        assertTrue(false, message);
    }
}
