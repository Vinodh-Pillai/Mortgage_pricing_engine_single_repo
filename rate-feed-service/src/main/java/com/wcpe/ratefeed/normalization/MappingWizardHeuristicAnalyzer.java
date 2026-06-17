package com.wcpe.ratefeed.normalization;

import com.wcpe.ratefeed.normalization.MappingWizardModels.AnalysisMode;
import com.wcpe.ratefeed.normalization.MappingWizardModels.Confidence;
import com.wcpe.ratefeed.normalization.MappingWizardModels.LlpaSection;
import com.wcpe.ratefeed.normalization.MappingWizardModels.MappingField;
import com.wcpe.ratefeed.normalization.MappingWizardModels.MappingProposal;
import com.wcpe.ratefeed.normalization.MappingWizardModels.MatrixMapping;
import com.wcpe.ratefeed.normalization.MappingWizardModels.ProposalAudit;
import com.wcpe.ratefeed.normalization.MappingWizardModels.PromptMetrics;
import com.wcpe.ratefeed.normalization.MappingWizardModels.SchemaValidation;
import com.wcpe.ratefeed.normalization.MappingWizardModels.SourcePreview;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class MappingWizardHeuristicAnalyzer {
  private static final Map<String, String> ALIASES = Map.ofEntries(
      Map.entry("rate", "note_rate"),
      Map.entry("note_rate", "note_rate"),
      Map.entry("interest_rate", "note_rate"),
      Map.entry("coupon", "note_rate"),
      Map.entry("price", "base_price"),
      Map.entry("base_price", "base_price"),
      Map.entry("base_price_bp", "base_price"),
      Map.entry("lock_period_days", "lock_period"),
      Map.entry("lock_period", "lock_period"),
      Map.entry("lock", "lock_period"),
      Map.entry("product", "canonical_product_key"),
      Map.entry("product_code", "canonical_product_key"),
      Map.entry("loan_program", "canonical_product_key"),
      Map.entry("adjustment", "adjustment_value"),
      Map.entry("fee", "adjustment_value"),
      Map.entry("bps", "adjustment_value"),
      Map.entry("llpa", "adjustment_type"),
      Map.entry("dscr", "adjustment_type"),
      Map.entry("credit", "adjustment_type"));

  private static final Set<String> REQUIRED = Set.of("note_rate", "base_price", "lock_period");
  private static final Set<String> FORMULA_PREFIXES = Set.of("=", "+", "-", "@");
  private static final double HEURISTIC_CONFIDENCE_SCORE = 0.60;

  public MappingProposal analyze(String fileName, byte[] content) throws IOException {
    String formatType = formatType(fileName);
    if ("PDF".equals(formatType)) {
      SourcePreview preview = new SourcePreview(fileName, formatType, List.of("pdf"), List.of(), List.of());
      return proposal(preview, List.of(), null, List.of(), List.of("PDF structure detection is available; PDF table extraction requires a document parser outside this local slice."));
    }
    if (formatType.startsWith("EXCEL")) return analyzeExcel(fileName, formatType, content);
    return analyzeCsv(fileName, formatType, content);
  }

  public MappingProposal proposeFromPreview(MappingWizardModels.ProposeRequest request) {
    SourcePreview preview = new SourcePreview(request.fileName(), request.formatType(), request.sheetNames(), request.headers(), firstTen(request.rows()));
    return proposal(preview, request.headers(), detectMatrix(request.rows()), detectLlpaSections(request.rows()), List.of());
  }

  private MappingProposal analyzeCsv(String fileName, String formatType, byte[] content) {
    String text = new String(content, StandardCharsets.UTF_8);
    List<List<String>> rows = text.lines().filter(line -> !line.isBlank()).limit(15).map(this::splitDelimited).toList();
    List<String> headers = rows.isEmpty() ? List.of() : rows.get(0);
    SourcePreview preview = new SourcePreview(fileName, formatType, List.of("csv"), headers, firstTen(rows));
    return proposal(preview, headers, detectMatrix(rows), detectLlpaSections(rows), formulaWarnings(rows));
  }

  private MappingProposal analyzeExcel(String fileName, String formatType, byte[] content) throws IOException {
    DataFormatter formatter = new DataFormatter();
    List<String> sheetNames = new ArrayList<>();
    List<List<String>> previewRows = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
      for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
        Sheet sheet = workbook.getSheetAt(i);
        sheetNames.add(sheet.getSheetName());
        if (previewRows.isEmpty()) {
          for (Row row : sheet) {
            if (previewRows.size() >= 15) break;
            List<String> cells = new ArrayList<>();
            for (Cell cell : row) {
              if (cell.getCellType() == CellType.FORMULA) warnings.add("Formula cell rejected from proposal preview at row " + (row.getRowNum() + 1));
              cells.add(cell.getCellType() == CellType.FORMULA ? "[FORMULA_REJECTED]" : formatter.formatCellValue(cell));
            }
            previewRows.add(cells);
          }
        }
      }
    }
    if ("EXCEL_XLSM".equals(formatType)) warnings.add("Macro-enabled workbook extension detected; macro content is not used in mapping proposals.");
    List<String> headers = inferHeaders(previewRows);
    SourcePreview preview = new SourcePreview(fileName, formatType, sheetNames, headers, firstTen(previewRows));
    return proposal(preview, headers, detectMatrix(previewRows), detectLlpaSections(previewRows), warnings);
  }

  private MappingProposal proposal(SourcePreview preview, List<String> headers, MatrixMapping matrix, List<LlpaSection> llpaSections, List<String> warnings) {
    List<MappingField> mappings = new ArrayList<>();
    List<String> unmapped = new ArrayList<>();
    Set<String> mappedCanonical = new LinkedHashSet<>();
    for (String header : headers == null ? List.<String>of() : headers) {
      String canonical = canonical(header);
      if (canonical == null) {
        if (header != null && !header.isBlank()) unmapped.add(header);
        continue;
      }
      mappedCanonical.add(canonical);
      mappings.add(new MappingField(
          header,
          canonical,
          Confidence.MEDIUM,
          HEURISTIC_CONFIDENCE_SCORE,
          REQUIRED.contains(canonical),
          coercion(canonical),
          "PROPOSED",
          "Header alias '" + header + "' matched canonical field '" + canonical + "' by local structure-only rules.",
          alternatives(canonical),
          false));
    }
    for (String required : REQUIRED) if (!mappedCanonical.contains(required)) unmapped.add(required);
    Map<String, Object> fingerprint = fingerprint(preview, headers, matrix);
    String promptHash = sha256(fingerprint.toString());
    String responseHash = sha256(mappings.toString() + unmapped);
    ProposalAudit audit = new ProposalAudit(promptHash, responseHash, false, List.of("structure_only", "formula_prefix_rejected", "external_llm_not_called"));
    SchemaValidation validation = validateProposal(mappings, unmapped, warnings);
    PromptMetrics metrics = new PromptMetrics("heuristic-v1", "fallback", List.of("human_acceptance_rate", "edit_distance", "time_to_approve"));
    return new MappingProposal(AnalysisMode.HEURISTIC, preview.formatType(), Confidence.MEDIUM, preview, mappings, unmapped, matrix, llpaSections, fingerprint, warnings, audit, List.of(), validation, metrics);
  }

  private SchemaValidation validateProposal(List<MappingField> mappings, List<String> unmapped, List<String> warnings) {
    List<String> errors = new ArrayList<>();
    if (mappings.isEmpty()) errors.add("No field mappings were proposed.");
    List<String> missingRequired = REQUIRED.stream().filter(unmapped::contains).toList();
    if (!missingRequired.isEmpty()) errors.add("Missing required canonical fields: " + missingRequired);
    if (warnings.stream().anyMatch(w -> w.toLowerCase(Locale.ROOT).contains("formula"))) errors.add("Formula-like content requires human review before use.");
    return new SchemaValidation(errors.isEmpty(), 0, true, errors);
  }

  private List<MappingWizardModels.Alternative> alternatives(String canonical) {
    return REQUIRED.stream()
        .filter(candidate -> !candidate.equals(canonical))
        .limit(2)
        .map(candidate -> new MappingWizardModels.Alternative(candidate, 0.30, "Lower-ranked canonical field from the required mapping set."))
        .toList();
  }

  private MatrixMapping detectMatrix(List<List<String>> rows) {
    if (rows == null) return null;
    for (int r = 0; r < rows.size(); r++) {
      List<String> row = rows.get(r);
      int numericBandCount = 0;
      int priceStart = -1;
      for (int c = 0; c < row.size(); c++) {
        String cell = row.get(c) == null ? "" : row.get(c).trim();
        if (cell.matches("\\d{1,3}(\\.\\d+)?%?")) {
          numericBandCount++;
          if (priceStart < 0) priceStart = c;
        }
      }
      if (numericBandCount >= 3) return new MatrixMapping(0, r, Math.max(priceStart, 1), r + 1, "decimal_or_percent", "decimal_or_mortgage_price");
    }
    return null;
  }

  private List<LlpaSection> detectLlpaSections(List<List<String>> rows) {
    List<LlpaSection> sections = new ArrayList<>();
    if (rows == null) return sections;
    for (int r = 0; r < rows.size(); r++) {
      String joined = String.join(" ", rows.get(r)).toLowerCase(Locale.ROOT);
      if (joined.contains("llpa") || joined.contains("adjustment") || joined.contains("fee") || joined.contains("prepay") || joined.contains("credit") || joined.contains("dscr")) {
        sections.add(new LlpaSection(rows.get(r).isEmpty() ? "LLPA" : rows.get(r).get(0), classifyLlpa(joined), r));
      }
    }
    return sections;
  }

  private Map<String, Object> fingerprint(SourcePreview preview, List<String> headers, MatrixMapping matrix) {
    Map<String, Object> fp = new LinkedHashMap<>();
    fp.put("formatType", preview.formatType());
    fp.put("fileExtension", extension(preview.fileName()));
    fp.put("sheetNames", preview.sheetNames());
    fp.put("headerSignatures", (headers == null ? List.<String>of() : headers).stream().map(this::normalize).filter(v -> !v.isBlank()).distinct().limit(20).toList());
    fp.put("matrixDetected", matrix != null);
    return fp;
  }

  private List<String> formulaWarnings(List<List<String>> rows) {
    List<String> warnings = new ArrayList<>();
    for (List<String> row : rows) {
      for (String cell : row) if (hasFormulaPrefix(cell)) warnings.add("Formula-like cell rejected from proposal preview.");
    }
    return warnings.stream().distinct().toList();
  }

  private List<String> inferHeaders(List<List<String>> rows) {
    for (List<String> row : rows) {
      long mapped = row.stream().map(this::canonical).filter(v -> v != null).count();
      if (mapped > 0) return row;
    }
    return rows.isEmpty() ? List.of() : rows.get(0);
  }

  private List<String> splitDelimited(String line) {
    String delimiter = line.contains("\t") ? "\\t" : line.contains("|") ? "\\|" : line.contains(";") ? ";" : ",";
    return List.of(line.split(delimiter, -1));
  }

  private static <T> List<T> firstTen(List<T> rows) { return rows == null ? List.of() : rows.stream().limit(10).toList(); }

  private String canonical(String value) { return ALIASES.get(normalize(value)); }

  private String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", ""); }

  private String coercion(String canonical) {
    return switch (canonical) {
      case "note_rate" -> "percent";
      case "adjustment_value" -> "bps";
      case "base_price" -> "mortgage_price_notation";
      default -> "string";
    };
  }

  private String classifyLlpa(String text) {
    if (text.contains("credit") || text.contains("fico")) return "CREDIT_SCORE";
    if (text.contains("dscr")) return "DSCR_RATIO";
    if (text.contains("prepay")) return "PREPAYMENT_PENALTY";
    return "ADJUSTMENT";
  }

  private boolean hasFormulaPrefix(String value) { return value != null && FORMULA_PREFIXES.stream().anyMatch(prefix -> value.trim().startsWith(prefix)); }

  private String formatType(String fileName) {
    String ext = extension(fileName);
    return switch (ext) {
      case ".xlsx" -> "EXCEL_XLSX";
      case ".xlsm" -> "EXCEL_XLSM";
      case ".pdf" -> "PDF";
      default -> "CSV";
    };
  }

  private String extension(String fileName) {
    if (fileName == null) return "";
    int idx = fileName.lastIndexOf('.');
    return idx >= 0 ? fileName.substring(idx).toLowerCase(Locale.ROOT) : "";
  }

  private String sha256(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("Unable to hash mapping proposal audit payload", e);
    }
  }
}
