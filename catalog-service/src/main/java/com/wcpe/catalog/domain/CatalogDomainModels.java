package com.wcpe.catalog.domain;

import java.time.Instant;
import java.util.UUID;

record InvestorRecord(UUID investorId, UUID tenantId, String code, String name, String status, Instant createdAt, Instant updatedAt) {}
record InvestorCreateRequest(UUID investorId, UUID tenantId, String code, String name, String status) {}
record InvestorResponse(UUID investorId, String code, String name, String status, Instant createdAt) {}
record InvestorListResponse(java.util.List<InvestorResponse> investors, int count) {}

record ProductRecord(UUID productId, UUID tenantId, String code, String name, String type, String status, Instant createdAt, Instant updatedAt) {}
record ProductResponse(UUID productId, String code, String name, String type, String status, Instant createdAt) {}
record ProductListResponse(java.util.List<ProductResponse> products, int count) {}

record ChannelRecord(UUID channelId, UUID tenantId, String code, String name, String type, String status, Instant createdAt, Instant updatedAt) {}
record ChannelResponse(UUID channelId, String code, String name, String type, String status, Instant createdAt) {}
record ChannelListResponse(java.util.List<ChannelResponse> channels, int count) {}
