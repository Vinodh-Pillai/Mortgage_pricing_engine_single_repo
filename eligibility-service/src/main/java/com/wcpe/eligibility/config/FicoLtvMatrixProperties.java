package com.wcpe.eligibility.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wcpe.eligibility.domain.models.FicoLtvMatrixConfig;
import com.wcpe.eligibility.domain.models.FicoLtvMatrixRow;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Configuration
@ConfigurationProperties(prefix = "eligibility.fico-ltv-matrix")
public class FicoLtvMatrixProperties {

    private boolean enabled = false;
    private List<MatrixSetConfig> matrixSets = new ArrayList<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public List<MatrixSetConfig> getMatrixSets() { return matrixSets; }
    public void setMatrixSets(List<MatrixSetConfig> matrixSets) { this.matrixSets = matrixSets; }

    public List<FicoLtvMatrixConfig> resolveConfigs(String productFamily, String investorCode, String channel) {
        return matrixSets.stream()
            .filter(m -> productFamily == null || m.productFamily == null || productFamily.equalsIgnoreCase(m.productFamily))
            .filter(m -> investorCode == null || m.investorCode == null || investorCode.equalsIgnoreCase(m.investorCode))
            .filter(m -> channel == null || m.channel == null || channel.equalsIgnoreCase(m.channel))
            .map(m -> new FicoLtvMatrixConfig(
                m.matrixSetId,
                m.productFamily,
                m.investorCode,
                m.channel,
                m.status,
                m.version,
                m.rows.stream().map(r -> new FicoLtvMatrixRow(
                    UUID.fromString(r.matrixRowId),
                    UUID.fromString(m.matrixSetId),
                    r.ficoMin,
                    r.ficoMax,
                    new BigDecimal(r.maxLtv),
                    r.maxCltv != null ? new BigDecimal(r.maxCltv) : null,
                    r.loanPurpose,
                    r.occupancyType,
                    r.propertyType,
                    r.unitsMin,
                    r.unitsMax,
                    r.documentationType,
                    r.ausType,
                    r.severityIfMissingFico,
                    r.reasonCode,
                    null
                )).toList()
            )).toList();
    }

    public static class MatrixSetConfig {
        String matrixSetId;
        String productFamily;
        String investorCode;
        String channel;
        String status;
        int version;
        List<MatrixRowConfig> rows = new ArrayList<>();

        public String getMatrixSetId() { return matrixSetId; }
        public void setMatrixSetId(String matrixSetId) { this.matrixSetId = matrixSetId; }
        public String getProductFamily() { return productFamily; }
        public void setProductFamily(String productFamily) { this.productFamily = productFamily; }
        public String getInvestorCode() { return investorCode; }
        public void setInvestorCode(String investorCode) { this.investorCode = investorCode; }
        public String getChannel() { return channel; }
        public void setChannel(String channel) { this.channel = channel; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public int getVersion() { return version; }
        public void setVersion(int version) { this.version = version; }
        public List<MatrixRowConfig> getRows() { return rows; }
        public void setRows(List<MatrixRowConfig> rows) { this.rows = rows; }
    }

    public static class MatrixRowConfig {
        String matrixRowId;
        int ficoMin;
        int ficoMax;
        String maxLtv;
        String maxCltv;
        String loanPurpose;
        String occupancyType;
        String propertyType;
        int unitsMin = 1;
        int unitsMax = 4;
        String documentationType;
        String ausType;
        String severityIfMissingFico = "WARNING";
        String reasonCode;

        public String getMatrixRowId() { return matrixRowId; }
        public void setMatrixRowId(String matrixRowId) { this.matrixRowId = matrixRowId; }
        public int getFicoMin() { return ficoMin; }
        public void setFicoMin(int ficoMin) { this.ficoMin = ficoMin; }
        public int getFicoMax() { return ficoMax; }
        public void setFicoMax(int ficoMax) { this.ficoMax = ficoMax; }
        public String getMaxLtv() { return maxLtv; }
        public void setMaxLtv(String maxLtv) { this.maxLtv = maxLtv; }
        public String getMaxCltv() { return maxCltv; }
        public void setMaxCltv(String maxCltv) { this.maxCltv = maxCltv; }
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
        public String getDocumentationType() { return documentationType; }
        public void setDocumentationType(String documentationType) { this.documentationType = documentationType; }
        public String getAusType() { return ausType; }
        public void setAusType(String ausType) { this.ausType = ausType; }
        public String getSeverityIfMissingFico() { return severityIfMissingFico; }
        public void setSeverityIfMissingFico(String severityIfMissingFico) { this.severityIfMissingFico = severityIfMissingFico; }
        public String getReasonCode() { return reasonCode; }
        public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
    }
}
