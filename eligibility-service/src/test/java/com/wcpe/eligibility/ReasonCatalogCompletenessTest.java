package com.wcpe.eligibility;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.eligibility.domain.models.ReasonCode;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ReasonCatalogCompletenessTest {
    private List<ReasonCode> reasonCodes;

    @BeforeEach
    void loadReasonCodes() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (var is = new ClassPathResource("reason-codes.json").getInputStream()) {
            Map<String, Object> data = mapper.readValue(is, new TypeReference<>() {});
            this.reasonCodes = mapper.convertValue(
                data.get("reasonCodes"), new TypeReference<List<ReasonCode>>() {}
            );
        }
    }

    @Test void catalog_contains_31_reason_codes() {
        assertEquals(31, reasonCodes.size(), "Expected exactly 31 reason codes");
    }

    @Test void all_codes_are_unique() {
        Set<String> codes = reasonCodes.stream()
            .map(ReasonCode::code)
            .toList().stream()
            .map(String::toString)
            .collect(java.util.stream.Collectors.toSet());
        assertEquals(reasonCodes.size(), codes.size(), "All reason code IDs must be unique");
    }

    @Test void all_codes_have_required_fields() {
        for (ReasonCode rc : reasonCodes) {
            assertNotNull(rc.code(), "reason code must not be null");
            assertNotNull(rc.ruleCode(), "ruleCode must not be null for " + rc.code());
            assertNotNull(rc.severity(), "severity must not be null for " + rc.code());
            assertNotNull(rc.category(), "category must not be null for " + rc.code());
            assertNotNull(rc.message(), "message must not be null for " + rc.code());
            assertNotNull(rc.description(), "description must not be null for " + rc.code());
            assertTrue(Set.of("HARD_STOP", "WARNING", "INFO").contains(rc.severity()),
                "Invalid severity for " + rc.code());
        }
    }

    @Test void r01_has_correct_codes() {
        List<ReasonCode> r01 = reasonCodes.stream()
            .filter(rc -> "R01".equals(rc.ruleCode()))
            .toList();
        assertTrue(r01.size() >= 2, "R01 should have at least 2 reason codes");
        assertTrue(r01.stream().anyMatch(rc -> "FC01".equals(rc.code())));
    }

    @Test void r02_has_correct_codes() {
        List<ReasonCode> r02 = reasonCodes.stream()
            .filter(rc -> "R02".equals(rc.ruleCode()))
            .toList();
        assertTrue(r02.size() >= 2, "R02 should have at least 2 reason codes");
        assertTrue(r02.stream().anyMatch(rc -> "LT01".equals(rc.code())));
    }

    @Test void r03_has_correct_codes() {
        List<ReasonCode> r03 = reasonCodes.stream()
            .filter(rc -> "R03".equals(rc.ruleCode()))
            .toList();
        assertTrue(r03.size() >= 2, "R03 should have at least 2 reason codes");
        assertTrue(r03.stream().anyMatch(rc -> "DT01".equals(rc.code())));
    }

    @Test void r04_r05_r06_have_codes() {
        for (String ruleCode : List.of("R04", "R05", "R06")) {
            List<ReasonCode> codes = reasonCodes.stream()
                .filter(rc -> ruleCode.equals(rc.ruleCode()))
                .toList();
            assertTrue(codes.size() >= 2, ruleCode + " should have at least 2 reason codes");
        }
    }

    @Test void r07_r08_r09_r10_have_codes() {
        for (String ruleCode : List.of("R07", "R08", "R09", "R10")) {
            List<ReasonCode> codes = reasonCodes.stream()
                .filter(rc -> ruleCode.equals(rc.ruleCode()))
                .toList();
            assertTrue(codes.size() >= 2, ruleCode + " should have at least 2 reason codes");
        }
    }

    @Test void r11_r12_have_codes() {
        List<ReasonCode> r11 = reasonCodes.stream()
            .filter(rc -> "R11".equals(rc.ruleCode()))
            .toList();
        assertTrue(r11.size() >= 3, "R11 should have at least 3 reason codes");

        List<ReasonCode> r12 = reasonCodes.stream()
            .filter(rc -> "R12".equals(rc.ruleCode()))
            .toList();
        assertTrue(r12.size() >= 2, "R12 should have at least 2 reason codes");
    }

    @Test void r00_general_has_codes() {
        List<ReasonCode> r00 = reasonCodes.stream()
            .filter(rc -> "R00".equals(rc.ruleCode()))
            .toList();
        assertTrue(r00.size() >= 4, "R00 should have at least 4 general reason codes");
    }

    @Test void all_12_rules_covered() {
        Set<String> coveredRules = reasonCodes.stream()
            .map(ReasonCode::ruleCode)
            .collect(java.util.stream.Collectors.toSet());
        for (int i = 0; i <= 12; i++) {
            String ruleCode = "R" + String.format("%02d", i);
            assertTrue(coveredRules.contains(ruleCode), ruleCode + " should have reason codes");
        }
    }
}
