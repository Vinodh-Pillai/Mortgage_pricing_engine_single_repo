package com.wcpe.eligibility.config;

import com.wcpe.eligibility.domain.models.OccupancyPurposeRuleRow;
import com.wcpe.eligibility.domain.models.OccupancyPurposeRuleSetConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Configuration
@ConfigurationProperties(prefix = "eligibility.occupancy-purpose")
public class OccupancyPurposeRuleProperties {

    private boolean enabled = false;
    private List<RuleSetConfig> ruleSets = new ArrayList<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public List<RuleSetConfig> getRuleSets() { return ruleSets; }
    public void setRuleSets(List<RuleSetConfig> ruleSets) { this.ruleSets = ruleSets; }

    public List<OccupancyPurposeRuleSetConfig> resolveConfigs(String productCode, String investorCode, String channel) {
        return ruleSets.stream()
            .filter(r -> r.productCode == null || productCode == null || r.productCode.equalsIgnoreCase(productCode))
            .filter(r -> r.investorCode == null || investorCode == null || r.investorCode.equalsIgnoreCase(investorCode))
            .filter(r -> r.channel == null || channel == null || r.channel.equalsIgnoreCase(channel))
            .map(r -> new OccupancyPurposeRuleSetConfig(
                UUID.fromString(r.ruleSetId),
                r.productFamily,
                r.investorCode,
                r.channel,
                r.precedence,
                r.rows.stream().map(row -> new OccupancyPurposeRuleRow(
                    UUID.fromString(row.ruleId),
                    UUID.fromString(r.ruleSetId),
                    row.loanPurpose,
                    row.occupancyType,
                    row.propertyType,
                    row.unitsMin,
                    row.unitsMax,
                    row.decision,
                    row.severity,
                    row.reasonCode,
                    row.messageTemplate,
                    row.priority
                )).toList()
            )).toList();
    }

    public static class RuleSetConfig {
        String ruleSetId;
        String productFamily;
        String productCode;
        String investorCode;
        String channel;
        int precedence;
        List<RuleRowConfig> rows = new ArrayList<>();

        public String getRuleSetId() { return ruleSetId; }
        public void setRuleSetId(String ruleSetId) { this.ruleSetId = ruleSetId; }
        public String getProductFamily() { return productFamily; }
        public void setProductFamily(String productFamily) { this.productFamily = productFamily; }
        public String getProductCode() { return productCode; }
        public void setProductCode(String productCode) { this.productCode = productCode; }
        public String getInvestorCode() { return investorCode; }
        public void setInvestorCode(String investorCode) { this.investorCode = investorCode; }
        public String getChannel() { return channel; }
        public void setChannel(String channel) { this.channel = channel; }
        public int getPrecedence() { return precedence; }
        public void setPrecedence(int precedence) { this.precedence = precedence; }
        public List<RuleRowConfig> getRows() { return rows; }
        public void setRows(List<RuleRowConfig> rows) { this.rows = rows; }
    }

    public static class RuleRowConfig {
        String ruleId;
        String loanPurpose;
        String occupancyType;
        String propertyType;
        int unitsMin = 1;
        int unitsMax = 4;
        String decision;
        String severity;
        String reasonCode;
        String messageTemplate;
        int priority;

        public String getRuleId() { return ruleId; }
        public void setRuleId(String ruleId) { this.ruleId = ruleId; }
        public String getLoanPurpose() { return loanPurpose; }
        public void setLoanPurpose(String loanPurpose) { this.loanPurpose = loanPurpose; }
        public String getOccupancyType() { return occupancyType; }
        public void setOccupancyType(String occupancyType) { this.occupancyType = occupancyType; }
        public String getPropertyType() { return propertyType; }
        public void setPropertyType(String propertyType) { this.propertyType = propertyType; }
        public int getUnitsMin() { return unitsMin; }
        public void setUnitsMin(int unitsMin) { this.unitsMin = unitsMin; }
        public int getUnitsMax() { return unitsMax; }
        public void setUnitsMax(int unitsMax) { this.unitsMax = unitsMax; }
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
