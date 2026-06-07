package com.wcpe.scenario.domain;

import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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

  @Test
  void rejectAllPrevalidatesCreateDraftFailuresBeforeCreatingAnyScenario() throws Exception {
    BatchImportRepository imports = mock(BatchImportRepository.class);
    ScenarioRepository scenarios = mock(ScenarioRepository.class);
    ScenarioService scenarioService = mock(ScenarioService.class);
    BatchImportService svc = new BatchImportService(imports, scenarios, scenarioService);
    UUID tenantId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    UUID failedRowId = UUID.randomUUID();
    String[] headers = {"scenario_name", "external_loan_id", "source_system"};
    List<String[]> rows = List.of(
        new String[] {"first", "loan-1", "LOS"},
        new String[] {"second", "loan-2", "LOS"});
    when(imports.addRow(eq(tenantId), eq(jobId), eq(3), anyString(), eq(ImportRowStatus.FAILED_VALIDATION), isNull(), eq(jobId + ":row:3")))
        .thenReturn(failedRowId);
    doReturn(List.of()).doThrow(new ScenarioException(HttpStatus.UNPROCESSABLE_ENTITY, "SUBMISSION_PROFILE_NOT_FOUND",
        "No active submission profile exists.", List.of(new ValidationIssue("SUBMISSION_PROFILE_NOT_FOUND", "channel", Severity.BLOCKING,
        "No active submission profile exists."))))
        .when(scenarioService).validateCreateDraft(eq(tenantId), anyString(), any(CreateScenarioRequest.class));

    invokeProcessRows(svc, tenantId, jobId, headers, rows, "RETAIL", "PURCHASE", PartialSuccessPolicy.REJECT_ALL_ON_ANY_ERROR);

    verify(scenarioService, never()).createDraft(any(), anyString(), anyString(), any(CreateScenarioRequest.class));
    verify(imports).addRow(eq(tenantId), eq(jobId), eq(3), anyString(), eq(ImportRowStatus.FAILED_VALIDATION), isNull(), eq(jobId + ":row:3"));
    verify(imports).addError(eq(tenantId), eq(failedRowId), eq("channel"), eq("SUBMISSION_PROFILE_NOT_FOUND"), anyString(), eq(""));
    verify(imports).markJobComplete(eq(tenantId), eq(jobId), eq(ImportJobStatus.FAILED), any(), eq(0), eq(1));
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

  private void invokeProcessRows(BatchImportService svc, UUID tenantId, UUID jobId, String[] headers, List<String[]> rows,
      String channel, String quoteIntent, PartialSuccessPolicy policy) throws Exception {
    java.lang.reflect.Method method = BatchImportService.class.getDeclaredMethod("processRows", UUID.class, UUID.class,
        String[].class, List.class, String.class, String.class, PartialSuccessPolicy.class, String.class, String.class, String.class);
    method.setAccessible(true);
    method.invoke(svc, tenantId, jobId, headers, rows, channel, quoteIntent, policy, "corr-1", "file-hash", "scenario-import-v1");
  }
}
