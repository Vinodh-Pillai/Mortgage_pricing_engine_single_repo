package com.wcpe.quote.api;

import com.wcpe.quote.LoanPassQuoteModels.ExecuteProductResponse;
import com.wcpe.quote.LoanPassQuoteModels.ExecuteSummaryResponse;
import com.wcpe.quote.LoanPassQuoteService;
import com.wcpe.quote.QuoteCreateException;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<Map<String, String>> quoteError(QuoteCreateException ex) {
        HttpStatus status = "LOANPASS_PRODUCT_NOT_FOUND".equals(ex.code()) ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(Map.of("code", ex.code(), "message", ex.getMessage()));
    }
}
