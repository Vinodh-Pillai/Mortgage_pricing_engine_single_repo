package com.wcpe.ratefeed.normalization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record FormatFingerprint(
        String formatType,
        String investorCode,
        String productCode,
        String sheetNamePattern,
        List<String> headerSignatures,
        String fileExtension,
        int headerRowIndex,
        int dataStartRowIndex
) {

    public static FormatFingerprint fromOnslowBayExcel(String sheetName, List<List<String>> headerRows) {
        List<String> signatures = new ArrayList<>();
        for (List<String> row : headerRows) {
            for (String cell : row) {
                if (cell != null && !cell.isBlank()) {
                    String sig = cell.trim().toLowerCase();
                    if (sig.length() > 2) {
                        signatures.add(sig);
                    }
                }
            }
        }
        // Keep top 20 unique signatures
        List<String> unique = signatures.stream().distinct().limit(20).toList();

        return new FormatFingerprint(
                "ONSLOW_BAY_EXCEL",
                "ONSLOW_BAY",
                inferProductCode(sheetName),
                sheetName,
                unique,
                ".xlsm",
                findHeaderRow(headerRows),
                findDataStartRow(headerRows)
        );
    }

    public static FormatFingerprint fromCanonicalCsv(String[] headers) {
        List<String> signatures = new ArrayList<>();
        for (String h : headers) {
            if (h != null && !h.isBlank()) {
                signatures.add(h.trim().toLowerCase());
            }
        }
        return new FormatFingerprint(
                "CANONICAL_CSV",
                "UNKNOWN",
                "UNKNOWN",
                "single_sheet",
                signatures,
                ".csv",
                0,
                1
        );
    }

    public JsonNode toJson(ObjectMapper mapper) {
        ObjectNode node = mapper.createObjectNode();
        node.put("formatType", formatType);
        node.put("investorCode", investorCode);
        node.put("productCode", productCode);
        node.put("sheetNamePattern", sheetNamePattern);
        node.put("fileExtension", fileExtension);
        node.put("headerRowIndex", headerRowIndex);
        node.put("dataStartRowIndex", dataStartRowIndex);
        ArrayNode sigArray = mapper.createArrayNode();
        for (String s : headerSignatures) {
            sigArray.add(s);
        }
        node.set("headerSignatures", sigArray);
        return node;
    }

    public static FormatFingerprint fromJson(JsonNode node) {
        List<String> sigs = new ArrayList<>();
        if (node.has("headerSignatures")) {
            for (JsonNode n : node.get("headerSignatures")) {
                sigs.add(n.asText());
            }
        }
        return new FormatFingerprint(
                node.get("formatType").asText(),
                node.get("investorCode").asText(),
                node.get("productCode").asText(),
                node.get("sheetNamePattern").asText(),
                sigs,
                node.get("fileExtension").asText(),
                node.get("headerRowIndex").asInt(),
                node.get("dataStartRowIndex").asInt()
        );
    }

    /**
     * Match score 0-100. Higher = better match.
     * Exact format + investor + product = 100
     * Format + investor = 80
     * Format + signatures overlap = 60
     * Format only = 40
     */
    public int matchScore(FormatFingerprint other) {
        if (other == null) return 0;

        int score = 0;
        if (Objects.equals(this.formatType, other.formatType)) score += 40;
        if (Objects.equals(this.investorCode, other.investorCode)) score += 20;
        if (Objects.equals(this.productCode, other.productCode)) score += 20;

        // Signature overlap
        if (!this.headerSignatures.isEmpty() && !other.headerSignatures.isEmpty()) {
            long overlap = this.headerSignatures.stream()
                    .filter(other.headerSignatures::contains)
                    .count();
            double ratio = (double) overlap / Math.max(this.headerSignatures.size(), other.headerSignatures.size());
            score += (int) (ratio * 20);
        }
        return Math.min(score, 100);
    }

    public boolean strongMatch(FormatFingerprint other) {
        return matchScore(other) >= 80;
    }

    private static String inferProductCode(String sheetName) {
        String lower = sheetName.toLowerCase();
        if (lower.contains("dscr") && lower.contains("cross")) return "DSCR_CROSS_COLLATERALIZED";
        if (lower.contains("dscr")) return "DSCR_PLUS";
        if (lower.contains("foreign")) return "FOREIGN_NATIONAL_PLUS";
        if (lower.contains("agency")) return "AGENCY_INVESTOR_PLUS";
        if (lower.contains("2nd") || lower.contains("second")) return "SECOND_LIEN";
        if (lower.contains("heloc")) return "HELOC";
        return "UNKNOWN";
    }

    private static int findHeaderRow(List<List<String>> headerRows) {
        for (int i = 0; i < headerRows.size(); i++) {
            List<String> row = headerRows.get(i);
            int nonEmpty = (int) row.stream().filter(c -> c != null && !c.isBlank()).count();
            // Header row typically has LTV band labels like "50", "55", "60"
            if (nonEmpty >= 5) {
                boolean hasLtvBand = row.stream()
                        .anyMatch(c -> c != null && c.matches("\\d+(\\.\\d+)?"));
                if (hasLtvBand) return i;
            }
        }
        return 7; // default row 8 (0-indexed)
    }

    private static int findDataStartRow(List<List<String>> headerRows) {
        for (int i = 0; i < headerRows.size(); i++) {
            List<String> row = headerRows.get(i);
            // Data row starts with a rate/coupon value
            String first = row.size() > 0 ? row.get(0) : null;
            if (first != null && first.matches("\\d+(\\.\\d+)?")) {
                return i;
            }
        }
        return 9; // default row 10
    }
}