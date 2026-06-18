package com.wcpe.tenantcontext;

import static org.assertj.core.api.Assertions.*;

import com.wcpe.tenantcontext.TenantInvestorChannelMappingService.TenantInvestorChannelMapping;
import com.wcpe.tenantcontext.TenantInvestorChannelMappingService.TenantInvestorChannelMappingRequest;
import com.wcpe.tenantcontext.TenantInvestorChannelMappingService.TenantMappingException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TenantInvestorChannelMappingServiceTest {
    private static final Instant JAN = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant MAR = Instant.parse("2026-03-01T12:00:00Z");
    private static final Instant JUN = Instant.parse("2026-06-01T00:00:00Z");

    @Test
    void resolvesClientIdToTenantChannelInvestorContextWithAuditRefs() {
        TenantInvestorChannelMappingService service = new TenantInvestorChannelMappingService(List.of(
            mapping("map-retail", "tenant-alpha", "lp-client-001", null, "pg-retail", "FNMA", "RETAIL", JAN, JUN)
        ));

        var context = service.resolve(new TenantInvestorChannelMappingRequest(null, "lp-client-001", null, null, null, null, MAR));

        assertThat(context.tenantId()).isEqualTo("tenant-alpha");
        assertThat(context.channelCode()).isEqualTo("RETAIL");
        assertThat(context.investorCode()).isEqualTo("FNMA");
        assertThat(context.auditRef()).isEqualTo("tenant-map-audit:map-retail");
        assertThat(context.sourceRefs()).containsEntry("clientId", "lp-client-001");
    }

    @Test
    void savesConfiguredMappingsAndListsByTenantWithoutDefaults() {
        TenantInvestorChannelMappingService service = new TenantInvestorChannelMappingService();
        service.save(mapping("map-retail", "tenant-alpha", "lp-client-001", null, "pg-retail", "FNMA", "RETAIL", JAN, JUN));
        service.save(mapping("map-wholesale", "tenant-beta", "lp-client-002", null, "pg-wholesale", "FHLMC", "WHOLESALE", JAN, null));

        assertThat(service.list("tenant-alpha")).extracting(TenantInvestorChannelMapping::mappingId).containsExactly("map-retail");
        assertThat(service.resolve(new TenantInvestorChannelMappingRequest("tenant-alpha", "lp-client-001", null, null, null, null, MAR)).auditRef())
            .isEqualTo("tenant-map-audit:map-retail");
        assertThatThrownBy(() -> service.resolve(new TenantInvestorChannelMappingRequest("tenant-alpha", "lp-client-002", null, null, null, null, MAR)))
            .isInstanceOf(TenantMappingException.class)
            .extracting(Throwable::getMessage)
            .isEqualTo("TENANT_MAPPING_NOT_FOUND");
    }

    @Test
    void selectsEffectiveVersionForPriceGroupAsOfTimestamp() {
        TenantInvestorChannelMappingService service = new TenantInvestorChannelMappingService(List.of(
            mapping("map-retail", "tenant-alpha", "lp-client-001", null, "pg-001", "FNMA", "RETAIL", JAN, JUN),
            mapping("map-correspondent", "tenant-alpha", "lp-client-001", null, "pg-001", "FHLMC", "CORRESPONDENT", JUN, null)
        ));

        var beforeChange = service.resolve(new TenantInvestorChannelMappingRequest("tenant-alpha", null, null, "pg-001", null, null, MAR));
        var afterChange = service.resolve(new TenantInvestorChannelMappingRequest("tenant-alpha", null, null, "pg-001", null, null, Instant.parse("2026-07-01T00:00:00Z")));

        assertThat(beforeChange.channelCode()).isEqualTo("RETAIL");
        assertThat(beforeChange.investorCode()).isEqualTo("FNMA");
        assertThat(afterChange.channelCode()).isEqualTo("CORRESPONDENT");
        assertThat(afterChange.investorCode()).isEqualTo("FHLMC");
    }

    @Test
    void failsClosedWithFieldErrorsForMissingAndAmbiguousMappings() {
        TenantInvestorChannelMappingService missingService = new TenantInvestorChannelMappingService(List.of());
        assertThatThrownBy(() -> missingService.resolve(new TenantInvestorChannelMappingRequest(null, "missing-client", null, null, null, null, MAR)))
            .isInstanceOf(TenantMappingException.class)
            .satisfies(error -> assertThat(((TenantMappingException) error).fieldErrors())
                .singleElement()
                .satisfies(fieldError -> assertThat(fieldError.field()).isEqualTo("clientId")));

        TenantInvestorChannelMappingService ambiguousService = new TenantInvestorChannelMappingService(List.of(
            mapping("map-one", "tenant-alpha", "lp-client-amb", null, null, "FNMA", "RETAIL", JAN, null),
            mapping("map-two", "tenant-alpha", "lp-client-amb", null, null, "FHLMC", "RETAIL", JAN, null)
        ));

        assertThatThrownBy(() -> ambiguousService.resolve(new TenantInvestorChannelMappingRequest(null, "lp-client-amb", null, null, null, null, MAR)))
            .isInstanceOf(TenantMappingException.class)
            .extracting(Throwable::getMessage)
            .isEqualTo("TENANT_MAPPING_AMBIGUOUS");
    }

    private static TenantInvestorChannelMapping mapping(String id, String tenantId, String clientId, String loId, String priceGroupId, String investorCode, String channelCode, Instant effectiveStart, Instant effectiveEnd) {
        return new TenantInvestorChannelMapping(id, tenantId, clientId, loId, priceGroupId, investorCode, channelCode, effectiveStart, effectiveEnd, "tenant-map-audit:" + id, "ACTIVE");
    }
}
