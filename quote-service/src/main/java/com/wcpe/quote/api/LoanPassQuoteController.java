package com.wcpe.quote.api;

import com.wcpe.quote.LoanPassQuoteModels.ExecuteProductResponse;
import com.wcpe.quote.LoanPassQuoteModels.ExecuteSummaryResponse;
import com.wcpe.quote.LoanPassQuoteService;
import com.wcpe.quote.QuoteCreateException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/loanpass")
public class LoanPassQuoteController {
    private final LoanPassQuoteService service;

    public LoanPassQuoteController(LoanPassQuoteService service) {
        this.service = service;
    }

    @PostMapping({"/execute-summary", "/executeSummary"})
    public ExecuteSummaryResponse executeSummary(
        @RequestBody(required = false) Map<String, Object> body,
        @RequestHeader(value = "X-Tenant-ID", required = false) UUID tenantId,
        @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId
    ) {
        return service.executeSummary(body, tenantId, correlationId);
    }

    @PostMapping({"/execute-product", "/executeProduct"})
    public ExecuteProductResponse executeProduct(
        @RequestBody(required = false) Map<String, Object> body,
        @RequestHeader(value = "X-Tenant-ID", required = false) UUID tenantId,
        @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId
    ) {
        return service.executeProduct(body == null ? Map.of() : body, tenantId, correlationId);
    }

    @ExceptionHandler(QuoteCreateException.class)
    public ResponseEntity<Map<String, String>> quoteError(QuoteCreateException ex, HttpServletRequest request) {
        HttpStatus status = "LOANPASS_PRODUCT_NOT_FOUND".equals(ex.code()) ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(errorBody(ex.code(), ex.getMessage(), request));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> invalidHeader(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        if (UUID.class.equals(ex.getRequiredType())) {
            return ResponseEntity.badRequest().body(errorBody(
                "LOANPASS_TENANT_INVALID",
                "X-Tenant-ID header must be a UUID when supplied",
                request
            ));
        }
        return ResponseEntity.badRequest().body(errorBody("LOANPASS_REQUEST_INVALID", ex.getMessage(), request));
    }

    private Map<String, String> errorBody(String code, String message, HttpServletRequest request) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        String correlationId = request.getHeader("X-Correlation-ID");
        if (correlationId != null && !correlationId.isBlank()) {
            body.put("correlationId", correlationId);
        }
        return body;
    }
}
