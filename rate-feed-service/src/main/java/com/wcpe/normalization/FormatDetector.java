package com.wcpe.ratefeed.normalization;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.*;

@Service
public class FormatDetector {

    private static final Logger log = LoggerFactory.getLogger(FormatDetector.class);
    private final ObjectMapper mapper = new ObjectMapper();

    public record DetectionResult(
            FormatFingerprint fingerprint,
            List<String> sheetNames,
            Map<String, List<List<String>>> sheetPreviews
    ) {}

    public DetectionResult detect(MultipartFile file) {
        String filename = file.getOriginalFilename();
        String ext = getExtension(filename);

        if (ext.equals(".csv")) {
            return detectCsv(file);
        } else if (ext.equals(".xlsx") || ext.equals(".xlsm")) {
            return detectExcel(file);
        } else {
            throw new IllegalArgumentException("Unsupported file type: " + ext);
        }
    }

    private DetectionResult detectCsv(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            String firstLine = new String(is.readNBytes(4096)).split("\n")[0];
            String[] headers = firstLine.split("[,;\\t|]");
            FormatFingerprint fp = FormatFingerprint.fromCanonicalCsv(headers);
            return new DetectionResult(fp, List.of("csv"), Map.of());
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to detect CSV format", e);
        }
    }

    private DetectionResult detectExcel(MultipartFile file) {
        try (InputStream is = file.getInputStream();
             Workbook wb = new XSSFWorkbook(is)) {

            List<String> sheetNames = new ArrayList<>();
            Map<String, List<List<String>>> previews = new LinkedHashMap<>();

            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                Sheet sheet = wb.getSheetAt(i);
                String name = sheet.getSheetName();
                sheetNames.add(name);

                // Preview first 15 rows
                List<List<String>> preview = new ArrayList<>();
                for (Row row : sheet) {
                    if (row.getRowNum() >= 15) break;
                    List<String> rowData = new ArrayList<>();
                    for (Cell cell : row) {
                        rowData.add(getCellString(cell));
                    }
                    preview.add(rowData);
                }
                previews.put(name, preview);
            }

            // Find the best product sheet (skip ComboBoxes, metadata sheets)
            String productSheet = sheetNames.stream()
                    .filter(s -> !s.equalsIgnoreCase("ComboBoxes")
                            && !s.equalsIgnoreCase("Combo Boxes")
                            && !s.isBlank())
                    .findFirst()
                    .orElse(sheetNames.get(0));

            List<List<String>> headerRows = previews.get(productSheet);
            FormatFingerprint fp = FormatFingerprint.fromOnslowBayExcel(productSheet, headerRows);

            return new DetectionResult(fp, sheetNames, previews);
        } catch (Exception e) {
            log.error("Excel detection failed", e);
            throw new IllegalArgumentException("Failed to detect Excel format: " + e.getMessage(), e);
        }
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

    private String getExtension(String filename) {
        if (filename == null) return "";
        int idx = filename.lastIndexOf('.');
        return idx > 0 ? filename.substring(idx).toLowerCase() : "";
    }
}