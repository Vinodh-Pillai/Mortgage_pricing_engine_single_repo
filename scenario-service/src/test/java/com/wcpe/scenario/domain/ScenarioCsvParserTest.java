package com.wcpe.scenario.domain;

import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;

// S09 unit tests: CSV parsing and import processing
class ScenarioCsvParserTest {

  @Test
  void rejectsUnknownColumns() {
    BatchImportService svc = new BatchImportService(null, null, null);
    ScenarioException ex = assertThrows(ScenarioException.class,
        () -> invokeValidateHeaders(svc, new String[] {"scenario_name", "external_loan_id", "unexpected"}));
    assertEquals("INVALID_CSV_HEADER", ex.code());
    assertTrue(ex.fieldErrors().stream().anyMatch(issue -> issue.code().equals("UNKNOWN_COLUMN")));
  }

  @Test
  void neutralizesCsvInjection() {
    // CSV injection characters (= + - @ `) at start of values are neutralized
    String[] line = parseLine("=CMD('calc')!,loan1,SYSTEM,TX,75001");
    assertEquals("CMD('calc')!", line[0]); // leading = removed
  }

  @Test
  void parsesQuotedFields() {
    String[] line = parseLine("\"name with, comma\",loan1,SYSTEM,TX,75001");
    assertEquals("name with, comma", line[0]);
  }

  @Test
  void parsesEmptyFields() {
    String[] line = parseLine(",,,TX,75001");
    assertEquals(5, line.length);
    assertEquals("", line[0]);
  }

  @Test
  void rejectsNonCsvFileExtension() {
    BatchImportService svc = new BatchImportService(null, null, null);
    MockMultipartFile file = new MockMultipartFile("file", "scenario.txt", "text/plain", "bad".getBytes());
    ScenarioException ex = assertThrows(ScenarioException.class, () -> invokeValidateFileUpload(svc, file));
    assertEquals("UNSUPPORTED_FILE_TYPE", ex.code());
  }

  private String[] parseLine(String line) {
    BatchImportService svc = new BatchImportService(null, null, null);
    return svc.parseLine(line);
  }

  private void invokeValidateHeaders(BatchImportService svc, String[] headers) {
    try {
      java.lang.reflect.Method method = BatchImportService.class.getDeclaredMethod("validateHeaders", String[].class);
      method.setAccessible(true);
      method.invoke(svc, (Object) headers);
    } catch (java.lang.reflect.InvocationTargetException ex) {
      if (ex.getCause() instanceof RuntimeException runtime) throw runtime;
      throw new RuntimeException(ex.getCause());
    } catch (ReflectiveOperationException ex) {
      throw new RuntimeException(ex);
    }
  }

  private void invokeValidateFileUpload(BatchImportService svc, MockMultipartFile file) {
    try {
      java.lang.reflect.Method method = BatchImportService.class.getDeclaredMethod("validateFileUpload", org.springframework.web.multipart.MultipartFile.class);
      method.setAccessible(true);
      method.invoke(svc, file);
    } catch (java.lang.reflect.InvocationTargetException ex) {
      if (ex.getCause() instanceof RuntimeException runtime) throw runtime;
      throw new RuntimeException(ex.getCause());
    } catch (ReflectiveOperationException ex) {
      throw new RuntimeException(ex);
    }
  }
}
