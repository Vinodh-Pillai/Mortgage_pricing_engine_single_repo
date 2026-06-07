package com.wcpe.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class NoHardCodedRegulatoryThresholdsTest {
  private static final Pattern FORBIDDEN_REGULATORY_LITERAL =
      Pattern.compile("(BigDecimal\\.valueOf\\(|new BigDecimal\\()\\s*\\\"?(0\\.125|0\\.0125|80|43|1)\\\"?");

  @Test
  void rejectsBusinessThresholdConstantsOutsideVersionedConfig() throws IOException {
    List<String> defects = new ArrayList<>();
    Path sourceRoot = Path.of("src", "main", "java", "com", "wcpe", "compliance");
    try (var paths = Files.walk(sourceRoot)) {
      for (Path path : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
        if (isEvaluatorOrContractCatalog(path)) {
          continue;
        }
        List<String> lines = Files.readAllLines(path);
        for (int index = 0; index < lines.size(); index++) {
          String line = lines.get(index);
          if (FORBIDDEN_REGULATORY_LITERAL.matcher(line).find()) {
            defects.add(path + ":" + (index + 1) + " contains forbidden regulatory numeric literal");
          }
        }
      }
    }

    assertEquals(List.of(), defects);
  }

  private static boolean isEvaluatorOrContractCatalog(Path path) {
    String fileName = path.getFileName().toString();
    return fileName.equals("HighCostThresholdEvaluator.java")
        || fileName.equals("AprAdvisoryPolicyEvaluator.java")
        || fileName.equals("FairLendingMonitoringService.java")
        || fileName.equals("ComplianceContractTestCatalog.java");
  }
}
