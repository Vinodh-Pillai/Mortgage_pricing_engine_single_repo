package com.wcpe.eligibility.config;

import com.wcpe.eligibility.domain.models.InvestorOverlayRuleRow;
import com.wcpe.eligibility.domain.models.InvestorOverlayRuleSetConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Configuration
@ConfigurationProperties(prefix = "eligibility.investor-overlay")
public class InvestorOverlayRuleProperties {
    private boolean enabled = false;
    private List<RuleSetConfig> ruleSets = new ArrayList<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public List<RuleSetConfig> getRuleSets() { return ruleSets; }
    public void setRuleSets(List<RuleSetConfig> ruleSets) { this.ruleSets = ruleSets; }

    public List<InvestorOverlayRuleSetConfig> resolveConfigs(String investorId, String productVersionId, String channel) {
        return ruleSets.stream()
            .filter(r -> matches(r.investorId, investorId))
            .filter(r -> matches(r.productVersionId, productVersionId))
            .filter(r -> matches(r.channel, channel))
            .map(r -> new InvestorOverlayRuleSetConfig(
                UUID.fromString(r.overlaySetId),
                r.investorId,
                r.productVersionId,
                r.channel,
                r.version,
                computePrecedence(r.productVersionId, r.channel, r.investorId),
                r.rows.stream().map(row -> new InvestorOverlayRuleRow(
                    UUID.fromString(row.overlayRuleId),
                    UUID.fromString(r.overlaySetId),
                    row.ruleCode,
                    row.factPath,
                    row.operator,
                    row.comparisonValue,
                    row.secondaryValue,
                    row.valueType,
                    row.conditions.stream().map(c -> new InvestorOverlayRuleRow.Condition(
                        c.factPath, c.operator, c.comparisonValue, c.secondaryValue
                    )).toList(),
                    row.severity,
                    row.reasonCode,
                    row.messageTemplate,
                    row.priority,
                    r.version
                )).toList()
            )).toList();
    }

    private boolean matches(String configured, String actual) {
        return configured == null || configured.isBlank() || configured.equalsIgnoreCase(actual == null ? "" : actual);
    }

    private int computePrecedence(String productVersionId, String channel, String investorId) {
        if (productVersionId != null && !productVersionId.isBlank()) return 3;
        if (investorId != null && !investorId.isBlank() && channel != null && !channel.isBlank()) return 2;
        if (investorId != null && !investorId.isBlank()) return 1;
        return 0;
    }

    public static class RuleSetConfig {
        String overlaySetId;
        String investorId;
        String productVersionId;
        String channel;
        int version = 1;
        List<RuleRowConfig> rows = new ArrayList<>();

        public String getOverlaySetId() { return overlaySetId; }
        public void setOverlaySetId(String overlaySetId) { this.overlaySetId = overlaySetId; }
        public String getInvestorId() { return investorId; }
        public void setInvestorId(String investorId) { this.investorId = investorId; }
        public String getProductVersionId() { return productVersionId; }
        public void setProductVersionId(String productVersionId) { this.productVersionId = productVersionId; }
        public String getChannel() { return channel; }
        public void setChannel(String channel) { this.channel = channel; }
        public int getVersion() { return version; }
        public void setVersion(int version) { this.version = version; }
        public List<RuleRowConfig> getRows() { return rows; }
        public void setRows(List<RuleRowConfig> rows) { this.rows = rows; }
    }

    public static class RuleRowConfig {
        String overlayRuleId;
        String ruleCode;
        String factPath;
        String operator;
        String comparisonValue;
        String secondaryValue;
        String valueType;
        List<ConditionConfig> conditions = new ArrayList<>();
        String severity;
        String reasonCode;
        String messageTemplate;
        int priority = 100;

        public String getOverlayRuleId() { return overlayRuleId; }
        public void setOverlayRuleId(String overlayRuleId) { this.overlayRuleId = overlayRuleId; }
        public String getRuleCode() { return ruleCode; }
        public void setRuleCode(String ruleCode) { this.ruleCode = ruleCode; }
        public String getFactPath() { return factPath; }
        public void setFactPath(String factPath) { this.factPath = factPath; }
        public String getOperator() { return operator; }
        public void setOperator(String operator) { this.operator = operator; }
        public String getComparisonValue() { return comparisonValue; }
        public void setComparisonValue(String comparisonValue) { this.comparisonValue = comparisonValue; }
        public String getSecondaryValue() { return secondaryValue; }
        public void setSecondaryValue(String secondaryValue) { this.secondaryValue = secondaryValue; }
        public String getValueType() { return valueType; }
        public void setValueType(String valueType) { this.valueType = valueType; }
        public List<ConditionConfig> getConditions() { return conditions; }
        public void setConditions(List<ConditionConfig> conditions) { this.conditions = conditions; }
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        public String getReasonCode() { return reasonCode; }
        public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
        public String getMessageTemplate() { return messageTemplate; }
        public void setMessageTemplate(String messageTemplate) { this.messageTemplate = messageTemplate; }
        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }
    }

    public static class ConditionConfig {
        String factPath;
        String operator;
        String comparisonValue;
        String secondaryValue;

        public String getFactPath() { return factPath; }
        public void setFactPath(String factPath) { this.factPath = factPath; }
        public String getOperator() { return operator; }
        public void setOperator(String operator) { this.operator = operator; }
        public String getComparisonValue() { return comparisonValue; }
        public void setComparisonValue(String comparisonValue) { this.comparisonValue = comparisonValue; }
        public String getSecondaryValue() { return secondaryValue; }
        public void setSecondaryValue(String secondaryValue) { this.secondaryValue = secondaryValue; }
    }
}
