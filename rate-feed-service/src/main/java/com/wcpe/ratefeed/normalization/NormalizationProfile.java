package com.wcpe.ratefeed.normalization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "rate_feed", name = "normalization_profile")
public class NormalizationProfile {

    @Id
    @Column(name = "profile_id", nullable = false, updatable = false)
    private UUID profileId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false, length = 256)
    private String name;

    @Column(name = "format_type", nullable = false, length = 80)
    private String formatType;

    @Column(name = "investor_code", nullable = false, length = 80)
    private String investorCode;

    @Column(name = "product_code", nullable = false, length = 80)
    private String productCode;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "status", nullable = false, length = 40)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mapping_config", columnDefinition = "jsonb")
    private JsonNode mappingConfig;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sample_output", columnDefinition = "jsonb")
    private JsonNode sampleOutput;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "format_fingerprint", columnDefinition = "jsonb")
    private JsonNode formatFingerprint;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "approved_by", length = 128)
    private String approvedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long versionLock;

    protected NormalizationProfile() {}

    public NormalizationProfile(UUID tenantId, String name, String formatType,
                                String investorCode, String productCode,
                                JsonNode mappingConfig, JsonNode formatFingerprint,
                                String createdBy) {
        this.profileId = UUID.randomUUID();
        this.tenantId = tenantId;
        this.name = name;
        this.formatType = formatType;
        this.investorCode = investorCode;
        this.productCode = productCode;
        this.version = 1;
        this.status = "DRAFT";
        this.mappingConfig = mappingConfig;
        this.formatFingerprint = formatFingerprint;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void approve(String approvedBy) {
        this.status = "APPROVED";
        this.approvedBy = approvedBy;
        this.approvedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void simulate() {
        this.status = "SIMULATE";
        this.updatedAt = Instant.now();
    }

    public void publish() {
        this.status = "PUBLISHED";
        this.updatedAt = Instant.now();
    }

    public void newVersion(JsonNode newConfig, String editor) {
        this.version++;
        this.status = "DRAFT";
        this.mappingConfig = newConfig;
        this.approvedBy = null;
        this.approvedAt = null;
        this.updatedAt = Instant.now();
    }

    public void setSampleOutput(JsonNode sampleOutput) {
        this.sampleOutput = sampleOutput;
        this.updatedAt = Instant.now();
    }

    // Getters
    public UUID getProfileId() { return profileId; }
    public UUID getTenantId() { return tenantId; }
    public String getName() { return name; }
    public String getFormatType() { return formatType; }
    public String getInvestorCode() { return investorCode; }
    public String getProductCode() { return productCode; }
    public int getVersion() { return version; }
    public String getStatus() { return status; }
    public JsonNode getMappingConfig() { return mappingConfig; }
    public JsonNode getSampleOutput() { return sampleOutput; }
    public JsonNode getFormatFingerprint() { return formatFingerprint; }
    public String getCreatedBy() { return createdBy; }
    public String getApprovedBy() { return approvedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getApprovedAt() { return approvedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
