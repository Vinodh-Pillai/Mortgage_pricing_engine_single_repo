package com.wcpe.adjustment.gridloader;

import com.wcpe.adjustment.CashOutLlpaEvaluator.CashOutLlpaRule;
import com.wcpe.adjustment.FicoLtvLlpaEvaluator.GridCell;
import com.wcpe.adjustment.FicoLtvLlpaEvaluator.LtvMetric;
import com.wcpe.adjustment.PropertyOccupancyLlpaEvaluator.PropertyOccupancyRule;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class GseGridMapper {
    private static final String DEFAULT_PRODUCT = "CONVENTIONAL";
    private static final String DEFAULT_CHANNEL = "RETAIL";
    private static final BigDecimal MAX_LOAN_AMOUNT = new BigDecimal("999999999.99");

    public GseGridMappedRules map(ParsedGseGrid grid, UUID tenantId) {
        UUID ruleBookId = UUID.nameUUIDFromBytes((tenantId + grid.investorCode() + grid.rows().hashCode()).getBytes(StandardCharsets.UTF_8));
        String version = grid.rows().isEmpty() ? grid.investorCode() + "_EMPTY" : grid.rows().get(0).versionLabel();
        String ruleBookHash = sha256(grid.rows().stream().map(GseGridRow::cellKey).toList().toString());
        List<GridCell> fico = grid.rows().stream().filter(GseGridRow::isCoreFicoLtv).map(row -> mapFico(row, tenantId, ruleBookId, version, ruleBookHash)).toList();
        List<CashOutLlpaRule> cashOut = dedupeCashOut(grid.rows()).stream().map(row -> mapCashOut(row, tenantId, ruleBookId, version)).toList();
        List<PropertyOccupancyRule> property = dedupeProperty(grid.rows()).stream().map(row -> mapProperty(row, tenantId, ruleBookId, version)).toList();
        return new GseGridMappedRules(grid.investorCode(), version, ruleBookHash, fico, cashOut, property);
    }

    private GridCell mapFico(GseGridRow row, UUID tenantId, UUID ruleBookId, String version, String ruleBookHash) {
        GridBandParser.FicoRange fico = GridBandParser.parseFico(row.ficoBand());
        GridBandParser.LtvRange ltv = GridBandParser.parseLtv(row.ltvBand());
        String reason = reason(row, "FICO_LTV");
        UUID ruleId = deterministicId(tenantId + row.cellKey() + reason);
        return new GridCell(tenantId, ruleBookId, ruleId, version, ruleBookHash, DEFAULT_PRODUCT, row.investorCode(), DEFAULT_CHANNEL,
            row.ficoBand(), fico.min(), fico.max(), LtvMetric.LTV, row.ltvBand(), ltv.min(), ltv.max(), ltv.boundaryPolicy(), points(row.llpaBps()),
            reason, 10, row.effectiveStart(), row.effectiveEnd(), sha256(row.cellKey() + row.llpaBps()), true);
    }

    private CashOutLlpaRule mapCashOut(GseGridRow row, UUID tenantId, UUID ruleBookId, String version) {
        GridBandParser.LtvRange ltv = GridBandParser.parseLtv(row.ltvBand());
        String reason = reason(row, "CASH_OUT");
        UUID ruleId = deterministicId(tenantId + row.investorCode() + row.ltvBand() + row.propertyType() + row.occupancy() + row.units() + reason);
        return new CashOutLlpaRule(tenantId, ruleBookId, ruleId, version, DEFAULT_PRODUCT, row.investorCode(), DEFAULT_CHANNEL, "CASH_OUT_REFI",
            com.wcpe.adjustment.CashOutLlpaEvaluator.LtvMetric.LTV, row.ltvBand(), ltv.min(), ltv.max(),
            com.wcpe.adjustment.CashOutLlpaEvaluator.BoundaryPolicy.valueOf(ltv.boundaryPolicy().name()), "ALL_LOAN_AMOUNTS",
            BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), MAX_LOAN_AMOUNT, row.occupancy(), row.propertyType(), null, points(row.llpaBps()), reason,
            20, row.effectiveStart(), row.effectiveEnd(), sha256(row.cellKey() + row.llpaBps()), true);
    }

    private PropertyOccupancyRule mapProperty(GseGridRow row, UUID tenantId, UUID ruleBookId, String version) {
        GridBandParser.LtvRange ltv = GridBandParser.parseLtv(row.ltvBand());
        String reason = reason(row, "PROPERTY_OCCUPANCY");
        UUID ruleId = deterministicId(tenantId + row.cellKey() + reason);
        return new PropertyOccupancyRule(tenantId, ruleBookId, ruleId, version, DEFAULT_PRODUCT, row.investorCode(), DEFAULT_CHANNEL,
            row.occupancy(), row.propertyType(), row.units(), row.units(), null, null, null, null,
            com.wcpe.adjustment.PropertyOccupancyLlpaEvaluator.LtvMetric.LTV, row.ltvBand(), ltv.min(), ltv.max(),
            com.wcpe.adjustment.PropertyOccupancyLlpaEvaluator.BoundaryPolicy.valueOf(ltv.boundaryPolicy().name()), "ALL_LOAN_AMOUNTS",
            BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), MAX_LOAN_AMOUNT, null, List.of(), points(row.llpaBps()), reason, 30,
            row.investorCode() + "_PROPERTY_OCCUPANCY", row.effectiveStart(), row.effectiveEnd(), sha256(row.cellKey() + row.llpaBps()), true);
    }

    private static List<GseGridRow> dedupeCashOut(List<GseGridRow> rows) {
        Map<String, GseGridRow> latest = new LinkedHashMap<>();
        rows.stream().filter(GseGridRow::isCashOut).forEach(row -> latest.put(String.join("|", row.investorCode(), row.ltvBand(), row.loanPurpose(), row.propertyType(), row.occupancy(), Integer.toString(row.units())), row));
        return latest.values().stream().toList();
    }

    private static List<GseGridRow> dedupeProperty(List<GseGridRow> rows) {
        Map<String, GseGridRow> latest = new LinkedHashMap<>();
        rows.stream().filter(GseGridRow::isPropertyOccupancyAddOn).forEach(row -> latest.put(row.cellKey(), row));
        return latest.values().stream().toList();
    }

    private static BigDecimal points(BigDecimal bps) {
        return bps.movePointLeft(2).setScale(6, RoundingMode.HALF_UP);
    }

    private static String reason(GseGridRow row, String category) {
        return (row.investorCode() + "_LLPA_" + category + "_" + row.ficoBand() + "_" + row.ltvBand()).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
    }

    private static UUID deterministicId(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
