package com.wcpe.eligibility.config;

import com.wcpe.eligibility.domain.models.PropertyTypeRuleSetConfig;
import com.wcpe.eligibility.domain.models.PropertyTypeRuleRow;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Configuration
@ConfigurationProperties(prefix = "eligibility.property-type")
public class PropertyTypeRuleProperties {

    private boolean enabled = false;
    private List<RuleSetConfig> ruleSets = new ArrayList<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public List<RuleSetConfig> getRuleSets() { return ruleSets; }
    public void setRuleSets(List<RuleSetConfig> ruleSets) { this.ruleSets = ruleSets; }

    public List<PropertyTypeRuleSetConfig> resolveConfigs(String productCode, String investorCode, String channel) {
        return ruleSets.stream()
            .filter(r -> r.productCode == null || productCode == null || r.productCode.equalsIgnoreCase(productCode))
            .filter(r -> r.investorCode == null || investorCode == null || r.investorCode.equalsIgnoreCase(investorCode))
            .filter(r -> r.channel == null || channel == null || r.channel.equalsIgnoreCase(channel))
            .map(r -> {
                int precedence = computePrecedence(r.investorCode, r.channel, r.productCode);
                return new PropertyTypeRuleSetConfig(
                    UUID.fromString(r.ruleSetId),
                    r.tenantId != null ? r.tenantId : "",
                    r.productFamily,
                    r.productCode,
                    r.investorCode,
                    r.channel,
                    r.version,
                    precedence,
                    r.rows.stream().map(row -> new PropertyTypeRuleRow(
                        UUID.fromString(row.ruleId),
                        UUID.fromString(r.ruleSetId),
                        row.propertyType,
                        row.unitsMin,
                        row.unitsMax,
                        row.occupancyType,
                        row.loanPurpose,
                        row.projectReviewRequirement,
                        row.decision,
                        row.severity,
                        row.reasonCode,
                        row.messageTemplate,
                        row.priority
                    )).toList()
                );
            }).toList();
    }

    private int computePrecedence(String investorCode, String channel, String productCode) {
        int p = 0;
        if (investorCode != null && !investorCode.isEmpty()) p += 4;
        if (channel != null && !channel.isEmpty()) p += 2;
        if (productCode != null && !productCode.isEmpty()) p += 1;
        return p;
    }

    public static class RuleSetConfig {
        String ruleSetId;
        String tenantId;
        String productFamily;
        String productCode;
        String investorCode;
        String channel;
        int version = 1;
        List<RuleRowConfig> rows = new ArrayList<>();

        public String getRuleSetId() { return ruleSetId; }
        public void setRuleSetId(String ruleSetId) { this.ruleSetId = ruleSetId; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getProductFamily() { return productFamily; }
        public void setProductFamily(String productFamily) { this.productFamily = productFamily; }
        public String getProductCode() { return productCode; }
        public void setProductCode(String productCode) { this.productCode = productCode; }
        public String getInvestorCode() { return investorCode; }
        public void setInvestorCode(String investorCode) { this.investorCode = investorCode; }
        public String getChannel() { return channel; }
        public void setChannel(String channel) { this.channel = channel; }
        public int getVersion() { return version; }
        public void setVersion(int version) { this.version = version; }
        public List<RuleRowConfig> getRows() { return rows; }
        public void setRows(List<RuleRowConfig> rows) { this.rows = rows; }
    }

    public static class RuleRowConfig {
        String ruleId;
        String propertyType;
        int unitsMin = 1;
        int unitsMax = 4;
        String occupancyType;
        String loanPurpose = "PURCHASE";
        String projectReviewRequirement = "NONE";
        String decision;
        String severity;
        String reasonCode;
        String messageTemplate;
        int priority = 100;

        public String getRuleId() { return ruleId; }
        public void setRuleId(String ruleId) { this.ruleId = ruleId; }
        public String getPropertyType() { return propertyType; }
        public void setPropertyType(String propertyType) { this.propertyType = propertyType; }
        public int getUnitsMin() { return unitsMin; }
        public void setUnitsMin(int unitsMin) { this.unitsMin = unitsMin; }
        public int getUnitsMax() { return unitsMax; }
        public void setUnitsMax(int unitsMax) { this.unitsMax = unitsMax; }
        public String getOccupancyType() { return occupancyType; }
        public void setOccupancyType(String occupancyType) { this.occupancyType = occupancyType; }
        public String getLoanPurpose() { return loanPurpose; }
        public void setLoanPurpose(String loanPurpose) { this.loanPurpose = loanPurpose; }
        public String getProjectReviewRequirement() { return projectReviewRequirement; }
        public void setProjectReviewRequirement(String projectReviewRequirement) { this.projectReviewRequirement = projectReviewRequirement; }
        public String getDecision() { return decision; }
        public void setDecision(String decision) { this.decision = decision; }
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        public String getReasonCode() { return reasonCode; }
        public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
        public String getMessageTemplate() { return messageTemplate; }
        public void setMessageTemplate(String messageTemplate) { this.messageTemplate = messageTemplate; }
        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }
    }
}
