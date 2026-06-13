package com.wcpe.adjustment.gridloader;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

abstract class GseLlpaCsvParser {
    private static final List<String> REQUIRED = List.of("FICO_BAND", "LTV_BAND", "LOAN_PURPOSE", "PROPERTY_TYPE", "OCCUPANCY", "UNITS", "LLPA_BPS");
    private static final Pattern EFFECTIVE_DATE = Pattern.compile("Effective\\s+(\\d{4}-\\d{2}-\\d{2})", Pattern.CASE_INSENSITIVE);

    ParsedGseGrid parse(String csvContent) {
        if (csvContent == null || csvContent.isBlank()) throw new IllegalArgumentException(investorCode() + " LLPA CSV content is required");
        List<String> rawLines = csvContent.lines().toList();
        Instant effectiveStart = effectiveStart(rawLines);
        String version = investorCode() + "_" + effectiveStart.atZone(ZoneOffset.UTC).toLocalDate().toString().replace('-', '_');
        List<String> dataLines = rawLines.stream().map(String::trim).filter(line -> !line.isBlank()).filter(line -> !line.startsWith("#")).toList();
        if (dataLines.size() < 2) throw new IllegalArgumentException(investorCode() + " LLPA CSV must include a header and at least one row");
        String[] headers = splitCsvLine(dataLines.get(0));
        Map<String, Integer> headerIndex = new LinkedHashMap<>();
        for (int i = 0; i < headers.length; i++) headerIndex.put(normalizeHeader(headers[i]), i);
        for (String required : REQUIRED) {
            if (!headerIndex.containsKey(required)) throw new IllegalArgumentException(investorCode() + " LLPA CSV missing required column " + required);
        }

        List<GseGridRow> rows = new ArrayList<>();
        List<GridParseWarning> warnings = new ArrayList<>();
        for (int i = 1; i < dataLines.size(); i++) {
            String[] tokens = splitCsvLine(dataLines.get(i));
            try {
                Map<String, String> row = new LinkedHashMap<>();
                for (Map.Entry<String, Integer> entry : headerIndex.entrySet()) {
                    row.put(entry.getKey(), entry.getValue() < tokens.length ? tokens[entry.getValue()].trim() : "");
                }
                rows.add(toRow(row, i + 1, effectiveStart, version));
            } catch (RuntimeException ex) {
                warnings.add(new GridParseWarning(i + 1, "ROW_SKIPPED", ex.getMessage()));
            }
        }
        return new ParsedGseGrid(investorCode(), rows, warnings);
    }

    abstract String investorCode();

    private GseGridRow toRow(Map<String, String> row, int sourceRow, Instant effectiveStart, String version) {
        return new GseGridRow(sourceRow, investorCode(), required(row, "FICO_BAND"), required(row, "LTV_BAND"),
            required(row, "LOAN_PURPOSE"), required(row, "PROPERTY_TYPE"), required(row, "OCCUPANCY"),
            Integer.parseInt(required(row, "UNITS")), new BigDecimal(required(row, "LLPA_BPS")), effectiveStart, null, version);
    }

    private static Instant effectiveStart(List<String> lines) {
        for (String line : lines) {
            Matcher matcher = EFFECTIVE_DATE.matcher(line);
            if (matcher.find()) return LocalDate.parse(matcher.group(1)).atStartOfDay().toInstant(ZoneOffset.UTC);
        }
        return Instant.now();
    }

    private static String required(Map<String, String> row, String column) {
        String value = row.get(column);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(column + " is required");
        return value.trim();
    }

    private static String normalizeHeader(String header) {
        return header == null ? "" : header.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    private static String[] splitCsvLine(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') quoted = !quoted;
            else if (ch == ',' && !quoted) {
                tokens.add(token.toString());
                token.setLength(0);
            } else token.append(ch);
        }
        tokens.add(token.toString());
        return tokens.toArray(String[]::new);
    }
}
