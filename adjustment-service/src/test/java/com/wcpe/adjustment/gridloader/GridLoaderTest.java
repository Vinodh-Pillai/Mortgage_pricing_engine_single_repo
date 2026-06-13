package com.wcpe.adjustment.gridloader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class GridLoaderTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void fnmaParsing() {
        ParsedGseGrid parsed = new FnmaLlpaCsvParser().parse(sampleCsv());

        assertThat(parsed.investorCode()).isEqualTo("FNMA");
        assertThat(parsed.rows()).hasSize(4);
        assertThat(parsed.rows().get(0).versionLabel()).isEqualTo("FNMA_2026_01_01");
    }

    @Test
    void fhlmcParsing() {
        ParsedGseGrid parsed = new FhlmcLlpaCsvParser().parse(sampleCsv().replace("FNMA", "FHLMC"));

        assertThat(parsed.investorCode()).isEqualTo("FHLMC");
        assertThat(parsed.rows()).extracting(GseGridRow::investorCode).containsOnly("FHLMC");
    }

    @Test
    void cashOutMapping() {
        GseGridMappedRules mapped = new GseGridMapper().map(new FnmaLlpaCsvParser().parse(sampleCsv()), TENANT);

        assertThat(mapped.cashOutRules()).hasSize(1);
        assertThat(mapped.cashOutRules().get(0).classificationCode()).isEqualTo("CASH_OUT_REFI");
        assertThat(mapped.cashOutRules().get(0).pointsDelta()).isEqualByComparingTo("1.000000");
    }

    @Test
    void propertyOccupancyMapping() {
        GseGridMappedRules mapped = new GseGridMapper().map(new FnmaLlpaCsvParser().parse(sampleCsv()), TENANT);

        assertThat(mapped.propertyOccupancyRules()).hasSize(1);
        assertThat(mapped.propertyOccupancyRules().get(0).propertyTypeCode()).isEqualTo("CONDO");
        assertThat(mapped.propertyOccupancyRules().get(0).unitMin()).isEqualTo(1);
    }

    @Test
    void overlapDetection() {
        String overlapping = """
            # FNMA LLPA Grid - Effective 2026-01-01
            FICO_BAND,LTV_BAND,LOAN_PURPOSE,PROPERTY_TYPE,OCCUPANCY,UNITS,LLPA_BPS
            740+,<=60,PURCHASE,SFR,PRIMARY,1,0
            740+,<=60,PURCHASE,SFR,PRIMARY,1,25
            """;
        GseGridMappedRules mapped = new GseGridMapper().map(new FnmaLlpaCsvParser().parse(overlapping), TENANT);

        assertThatThrownBy(() -> new GseGridOverlapValidator().validate(mapped))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("overlapping FICO/LTV grid cells");
    }

    @Test
    void versioningSupersession() {
        GseGridMappedRules mapped = new GseGridMapper().map(new FnmaLlpaCsvParser().parse(sampleCsv()), TENANT);

        assertThat(mapped.ruleBookVersion()).isEqualTo("FNMA_2026_01_01");
        assertThat(mapped.ruleBookHash()).hasSize(64);
        assertThat(mapped.ficoLtvCells()).allSatisfy(cell -> {
            assertThat(cell.effectiveStart().toString()).startsWith("2026-01-01T00:00:00Z");
            assertThat(cell.ruleBookVersion()).isEqualTo(mapped.ruleBookVersion());
            assertThat(cell.ruleBookHash()).isEqualTo(mapped.ruleBookHash());
        });
    }

    private static String sampleCsv() {
        return """
            # FNMA LLPA Grid - Effective 2026-01-01
            FICO_BAND,LTV_BAND,LOAN_PURPOSE,PROPERTY_TYPE,OCCUPANCY,UNITS,LLPA_BPS
            740+,<=60,PURCHASE,SFR,PRIMARY,1,0
            720-739,70.01-75,PURCHASE,SFR,PRIMARY,1,25
            740+,<=60,CASH_OUT_REFI,SFR,PRIMARY,1,100
            740+,75.01-80,PURCHASE,CONDO,PRIMARY,1,75
            """;
    }
}
