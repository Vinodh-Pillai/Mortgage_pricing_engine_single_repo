package com.wcpe.ratefeed.normalization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.*;

@Component
public class OnslowBayExcelAdapter {

    private static final Logger log = LoggerFactory.getLogger(OnslowBayExcelAdapter.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private static final MathContext MC = new MathContext(18);

    public record AdapterResult(
            List<NormalizedEntry> entries,
            List<String> warnings,
            JsonNode sampleOutput
    ) {}

    public record NormalizedEntry(
            String canonicalProductKey,
            String programKey,
            int lockPeriodDays,
            BigDecimal ratePercent,
            BigDecimal pricePoints,
            String adjustmentType,
            BigDecimal adjustmentValue,
            String adjustmentUnit,
            Instant effectiveAt,
            Map<String, Object> dimensions,
            Map<String, Object> rawAttributes,
            Map<String, Object> mappingRefs,
            String severity,
            String message
    ) {}

    public AdapterResult parse(InputStream inputStream, NormalizationProfile profile, Instant effectiveAt) {
        List<NormalizedEntry> entries = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        try (Workbook wb = new XSSFWorkbook(inputStream)) {
            JsonNode config = profile.getMappingConfig();
            String sheetPattern = config.has("sheetSelector") && config.get("sheetSelector").has("pattern")
                    ? config.get("sheetSelector").get("pattern").asText() : profile.getProductCode();

            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                Sheet sheet = wb.getSheetAt(i);
                String sheetName = sheet.getSheetName();

                if (!matchesPattern(sheetName, sheetPattern)) {
                    if (!sheetName.equalsIgnoreCase("ComboBoxes")) {
                        warnings.add("Skipping sheet: " + sheetName + " (pattern: " + sheetPattern + ")");
                    }
                    continue;
                }

                log.info("Parsing sheet: {}", sheetName);
                List<NormalizedEntry> sheetEntries = parseProductSheet(sheet, profile, effectiveAt, config);
                entries.addAll(sheetEntries);
            }

            JsonNode sampleOutput = buildSampleOutput(entries);
            return new AdapterResult(entries, warnings, sampleOutput);

        } catch (Exception e) {
            log.error("Onslow Bay Excel parsing failed", e);
            throw new RuntimeException("Failed to parse Onslow Bay Excel: " + e.getMessage(), e);
        }
    }

    private boolean matchesPattern(String sheetName, String pattern) {
        if (pattern == null || pattern.isBlank() || pattern.equals("*")) return true;
        if (pattern.endsWith("*")) {
            String prefix = pattern.substring(0, pattern.length() - 1);
            return sheetName.startsWith(prefix);
        }
        return sheetName.equals(pattern);
    }

    private List<NormalizedEntry> parseProductSheet(Sheet sheet, NormalizationProfile profile,
                                                     Instant effectiveAt, JsonNode config) {
        List<NormalizedEntry> entries = new ArrayList<>();

        int ltvHeaderRow = config.has("matrix") && config.get("matrix").has("ltvHeaderRow")
                ? config.get("matrix").get("ltvHeaderRow").asInt() : 7;
        int rateColumn = config.has("matrix") && config.get("matrix").has("rateColumn")
                ? config.get("matrix").get("rateColumn").asInt() : 2;
        int priceStartCol = config.has("matrix") && config.get("matrix").has("priceStartCol")
                ? config.get("matrix").get("priceStartCol").asInt() : 3;
        int dataStartRow = config.has("matrix") && config.get("matrix").has("dataStartRow")
                ? config.get("matrix").get("dataStartRow").asInt() : 9;

        Row ltvRow = sheet.getRow(ltvHeaderRow);
        if (ltvRow == null) {
            throw new IllegalStateException("LTV header row " + (ltvHeaderRow + 1) + " is empty");
        }

        List<Integer> ltvColumns = new ArrayList<>();
        List<Integer> ltvValues = new ArrayList<>();

        for (int c = priceStartCol; c <= ltvRow.getLastCellNum(); c++) {
            Cell cell = ltvRow.getCell(c);
            if (cell != null) {
                String val = getCellString(cell).trim();
                if (!val.isEmpty()) {
                    try {
                        int ltv = Integer.parseInt(val.replaceAll("[^0-9]", ""));
                        ltvColumns.add(c);
                        ltvValues.add(ltv);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        if (ltvColumns.isEmpty()) {
            throw new IllegalStateException("No valid LTV bands found in header row");
        }

        log.info("Found LTV bands: {} at columns: {}", ltvValues, ltvColumns);

        for (int r = dataStartRow; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            Cell rateCell = row.getCell(rateColumn);
            if (rateCell == null) continue;

            String rateStr = getCellString(rateCell).trim();
            if (rateStr.isEmpty()) continue;

            BigDecimal rate;
            try {
                rate = new BigDecimal(rateStr, MC);
            } catch (NumberFormatException e) {
                continue;
            }

            for (int i = 0; i < ltvColumns.size(); i++) {
                int col = ltvColumns.get(i);
                int ltv = ltvValues.get(i);
                Cell priceCell = row.getCell(col);
                if (priceCell == null) continue;

                String priceStr = getCellString(priceCell).trim();
                if (priceStr.isEmpty()) continue;

                BigDecimal price;
                try {
                    price = new BigDecimal(priceStr, MC);
                } catch (NumberFormatException e) {
                    continue;
                }

                NormalizedEntry entry = new NormalizedEntry(
                        profile.getProductCode(),
                        profile.getProductCode(),
                        30,
                        rate,
                        price,
                        null,
                        null,
                        null,
                        effectiveAt,
                        Map.of("ltv", ltv, "source_sheet", sheet.getSheetName()),
                        Map.of("source_row", r, "source_col", col, "rate_cell", rateStr, "price_cell", priceStr),
                        Map.of("profile", profile.getProfileId().toString(), "profile_version", profile.getVersion()),
                        "INFO",
                        "Base price: rate=" + rate + ", ltv=" + ltv + ", price=" + price
                );
                entries.add(entry);
            }
        }

        parseLlpaSections(sheet, profile, effectiveAt, config, entries);

        return entries;
    }

    private void parseLlpaSections(Sheet sheet, NormalizationProfile profile,
                                   Instant effectiveAt, JsonNode config,
                                   List<NormalizedEntry> entries) {
        if (!config.has("llpaSections")) return;

        for (JsonNode section : config.get("llpaSections")) {
            String label = section.get("label").asText();
            String type = section.get("type").asText();
            int startRow = section.get("startRow").asInt();

            log.debug("Parsing LLPA section: {} at row {}", label, startRow);

            for (int r = startRow; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) break;

                Cell labelCell = row.getCell(0);
                if (labelCell == null) continue;

                String rowLabel = getCellString(labelCell).trim();
                if (rowLabel.isEmpty()) continue;

                if (isSectionHeader(rowLabel) || isFooterLabel(rowLabel)) {
                    break;
                }

                int priceStartCol = config.has("matrix") && config.get("matrix").has("priceStartCol")
                        ? config.get("matrix").get("priceStartCol").asInt() : 3;

                for (int c = priceStartCol; c <= row.getLastCellNum(); c++) {
                    Cell cell = row.getCell(c);
                    if (cell == null) continue;

                    String valStr = getCellString(cell).trim();
                    if (valStr.isEmpty()) continue;

                    BigDecimal adjValue;
                    try {
                        adjValue = new BigDecimal(valStr, MC);
                    } catch (NumberFormatException e) {
                        continue;
                    }

                    int ltvBand = c - priceStartCol + 50;

                    NormalizedEntry entry = new NormalizedEntry(
                            profile.getProductCode(),
                            profile.getProductCode(),
                            30,
                            null,
                            null,
                            type,
                            adjValue,
                            "BPS_ADJUSTMENT",
                            effectiveAt,
                            Map.of("ltv_band", ltvBand, "llpa_label", rowLabel, "llpa_section", label, "source_sheet", sheet.getSheetName()),
                            Map.of("source_row", r, "source_col", c, "raw_value", valStr),
                            Map.of("profile", profile.getProfileId().toString(), "profile_version", profile.getVersion()),
                            "INFO",
                            "LLPA: " + label + " -> " + rowLabel + " = " + adjValue + " bps"
                    );
                    entries.add(entry);
                }
            }
        }
    }

    private boolean isSectionHeader(String label) {
        String lower = label.toLowerCase();
        return lower.contains("llpa") || lower.contains("prepay") || lower.contains("delivery")
                || lower.contains("lock period") || lower.contains("extension") || lower.contains("fee")
                || lower.contains("contact") || lower.contains("program notes") || lower.contains("tier");
    }

    private boolean isFooterLabel(String label) {
        String lower = label.toLowerCase();
        return lower.contains("max lock") || lower.contains("delivery fee") || lower.contains("funding fee")
                || lower.contains("scenario fee") || lower.contains("tax service") || lower.contains("non del")
                || lower.contains("pre-close") || lower.contains("secondary valuation") || lower.contains("recording")
                || lower.contains("contact") || lower.contains("phone") || lower.contains("email") || lower.contains("hours");
    }

    private JsonNode buildSampleOutput(List<NormalizedEntry> entries) {
        ObjectNode sample = mapper.createObjectNode();
        sample.put("total_entries", entries.size());
        sample.put("base_price_count", (int) entries.stream().filter(e -> e.adjustmentType() == null).count());
        sample.put("llpa_count", (int) entries.stream().filter(e -> e.adjustmentType() != null).count());

        ObjectNode byProduct = mapper.createObjectNode();
        for (NormalizedEntry e : entries) {
            String key = e.canonicalProductKey();
            if (!byProduct.has(key)) {
                byProduct.putObject(key).put("count", 0);
            }
            byProduct.get(key).put("count", byProduct.get(key).get("count").asInt() + 1);
        }
        sample.set("by_product", byProduct);

        return sample;
    }

    private String getCellString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getDateCellValue().toString();
                }
                double val = cell.getNumericCellValue();
                if (val == (long) val) yield String.valueOf((long) val);
                yield String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }
}