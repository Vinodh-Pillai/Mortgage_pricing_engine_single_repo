package com.wcpe.catalog.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

record InvestorRecord(UUID investorId, UUID tenantId, String code, String name, String status, Instant createdAt, Instant updatedAt) {}
record InvestorCreateRequest(UUID investorId, UUID tenantId, String code, String name, String status) {}
record InvestorResponse(UUID investorId, String code, String name, String status, Instant createdAt,
                        String investorCode, String type, String deliveryType, Integer settlementDays, String apiEndpoint) {
    InvestorResponse(UUID investorId, String code, String name, String status, Instant createdAt) {
        this(investorId, code, name, status, createdAt, code, null, null, null, null);
    }
}
record InvestorListResponse(java.util.List<InvestorResponse> investors, int count) {}
record InvestorUpsertRequest(String code, String name, String investorCode, String type, String deliveryType,
                             Integer settlementDays, String apiEndpoint, String status) {}
record InvestorEligibilityMatrixRow(UUID id, UUID investorId, String loanPurpose, String propertyType,
                                    String occupancyType, Integer minFico, Integer maxFico,
                                    BigDecimal maxLtv, BigDecimal maxCltv, BigDecimal maxDti,
                                    BigDecimal minLoanAmount, BigDecimal maxLoanAmount,
                                    List<String> allowedStates, List<String> excludedCounties,
                                    Map<String, Object> overlays, LocalDate effectiveDate,
                                    LocalDate expirationDate, boolean active) {}
record InvestorEligibilityMatrixRequest(List<InvestorEligibilityMatrixRow> rows) {}
record InvestorEligibilityMatrixResponse(UUID investorId, List<InvestorEligibilityMatrixRow> rows) {}

record ProductRecord(UUID productId, UUID tenantId, String code, String name, String type, String status, Instant createdAt, Instant updatedAt) {}
record ProductResponse(UUID productId, String code, String name, String type, String status, Instant createdAt) {}
record ProductListResponse(java.util.List<ProductResponse> products, int count) {}

record ChannelRecord(UUID channelId, UUID tenantId, String code, String name, String type, String status, Instant createdAt, Instant updatedAt) {}
record ChannelResponse(UUID channelId, String code, String name, String type, String status, Instant createdAt) {}
record ChannelListResponse(java.util.List<ChannelResponse> channels, int count) {}
