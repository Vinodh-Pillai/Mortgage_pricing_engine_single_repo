package com.wcpe.pricing.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.*;
import org.junit.jupiter.api.*;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests for PII-05 pricing API using synthetic grid fixtures only.
 * No real mortgage rates, thresholds, fees, or business constants.
 */
@DisplayName("PII-05 Pricing Contract")
class PricingContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static Path contractsDir;
    private static JsonSchema requestSchema;
    private static JsonSchema responseSchema;

    @BeforeAll
    static void loadSchemas() throws Exception {
        contractsDir = Paths.get("contracts").toAbsolutePath();
        
        requestSchema = loadSchema("pricing-request-schema.json");
        responseSchema = loadSchema("pricing-response-schema.json");
    }

    private static JsonSchema loadSchema(String filename) throws Exception {
        InputStream is = Files.newInputStream(contractsDir.resolve(filename));
        SchemaValidatorsConfig config = new SchemaValidatorsConfig();
        config.setDefaultJsonSchemaVersions(JsonSchemaVersion.JSON_SCHEMA_SPEC_CURRENT);
        return JsonSchemaFactory.getInstance(SchemaVersion.DRAFT_2019_09)
                .getSchema(is, config);
    }

    private static void assertValid(JsonSchema schema, String json) throws Exception {
        Set<ValidationMessage> messages = schema.validate(MAPPER.readTree(json));
        assertTrue(messages.isEmpty(), () -> "Expected valid contract payload, got: " + messages);
    }

    private static void assertInvalid(JsonSchema schema, String json) throws Exception {
        Set<ValidationMessage> messages = schema.validate(MAPPER.readTree(json));
        assertFalse(messages.isEmpty(), "Expected contract validation to reject payload");
    }

    @Test
    @DisplayName("synthetic grid fixture request maps to stable contract response shape")
    void validFixtureRequestProducesStableResponse() throws Exception {
        String requestJson = "{\"fixture_id\": \"SYNTH_GRID_FIXTURE_A\"}";
        assertValid(requestSchema, requestJson);

        String responseJson = """
            {
              "fixture_id": "SYNTH_GRID_FIXTURE_A",
              "synthetic_base_rate": "SYNTH_BASE_RATE_TOKEN_A",
              "synthetic_adjustment": "SYNTH_ADJUSTMENT_TOKEN_A",
              "synthetic_quote_rate": "SYNTH_QUOTE_RATE_TOKEN_A"
            }
            """;
        assertValid(responseSchema, responseJson);
    }

    @Test
    @DisplayName("missing fixture_id is rejected by request contract")
    void missingFixtureIdIsRejected() throws Exception {
        String invalidRequest = "{}";
        assertInvalid(requestSchema, invalidRequest);
    }

    @Test
    @DisplayName("unsupported fixture_id produces a contract-defined error outcome")
    void unsupportedFixtureIdProducesError() throws Exception {
        String requestJson = "{\"fixture_id\": \"SYNTH_GRID_UNSUPPORTED\"}";
        assertValid(requestSchema, requestJson);

        String openApiContract = Files.readString(contractsDir.resolve("pricing-contract.yml"));
        assertTrue(openApiContract.contains("\"400\":"),
                "Contract must define a validation/error outcome for missing or unsupported fixture_id");
    }

    @Test
    @DisplayName("response is missing synthetic_base_rate field fails validation")
    void responseMissingBaseRateFails() throws Exception {
        String incompleteResponse = """
            {
              "fixture_id": "SYNTH_GRID_FIXTURE_A",
              "synthetic_adjustment": "SYNTH_ADJUSTMENT_TOKEN_A",
              "synthetic_quote_rate": "SYNTH_QUOTE_RATE_TOKEN_A"
            }
            """;
        assertInvalid(responseSchema, incompleteResponse);
    }

    @Test
    @DisplayName("response is missing synthetic_adjustment field fails validation")
    void responseMissingAdjustmentFails() throws Exception {
        String incompleteResponse = """
            {
              "fixture_id": "SYNTH_GRID_FIXTURE_A",
              "synthetic_base_rate": "SYNTH_BASE_RATE_TOKEN_A",
              "synthetic_quote_rate": "SYNTH_QUOTE_RATE_TOKEN_A"
            }
            """;
        assertInvalid(responseSchema, incompleteResponse);
    }

    @Test
    @DisplayName("response is missing synthetic_quote_rate field fails validation")
    void responseMissingQuoteRateFails() throws Exception {
        String incompleteResponse = """
            {
              "fixture_id": "SYNTH_GRID_FIXTURE_A",
              "synthetic_base_rate": "SYNTH_BASE_RATE_TOKEN_A",
              "synthetic_adjustment": "SYNTH_ADJUSTMENT_TOKEN_A"
            }
            """;
        assertInvalid(responseSchema, incompleteResponse);
    }
}
