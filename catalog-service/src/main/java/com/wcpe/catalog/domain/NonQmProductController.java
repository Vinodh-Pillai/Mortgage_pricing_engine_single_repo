package com.wcpe.catalog.domain;

import com.wcpe.catalog.auth.AuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/product-catalog/non-qm-products")
class NonQmProductController {
  private final NonQmProductRepository repository;
  private final NonQmProductSchemaRegistry schemas;
  private final AuthorizationService authorizationService;

  NonQmProductController(NonQmProductRepository repository, NonQmProductSchemaRegistry schemas, AuthorizationService authorizationService) {
    this.repository = repository;
    this.schemas = schemas;
    this.authorizationService = authorizationService;
  }

  @GetMapping
  NonQmProductListResponse list(@PathVariable UUID tenantId, @RequestParam(required = false) String status,
                                @RequestParam(required = false) String productType, @RequestParam(required = false) String investorCode,
                                @RequestParam(required = false) String channelCode, HttpServletRequest http) {
    authorizationService.authorize("READ_CATALOG", http.getHeader("X-Roles"));
    List<NonQmProductResponse> products = repository.list(tenantId, status, productType, investorCode, channelCode);
    return new NonQmProductListResponse(products, products.size());
  }

  @GetMapping("/{productCode}")
  NonQmProductResponse get(@PathVariable UUID tenantId, @PathVariable String productCode, HttpServletRequest http) {
    authorizationService.authorize("READ_CATALOG", http.getHeader("X-Roles"));
    return repository.find(tenantId, productCode).orElseThrow(() -> new CatalogException("NON_QM_PRODUCT_NOT_FOUND"));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  NonQmProductResponse create(@PathVariable UUID tenantId, @RequestBody NonQmProductRequest request, HttpServletRequest http) {
    authorizationService.authorize("WRITE_CATALOG", http.getHeader("X-Roles"));
    return repository.create(tenantId, request);
  }

  @PutMapping("/{productCode}")
  NonQmProductResponse update(@PathVariable UUID tenantId, @PathVariable String productCode, @RequestBody NonQmProductRequest request, HttpServletRequest http) {
    authorizationService.authorize("WRITE_CATALOG", http.getHeader("X-Roles"));
    return repository.update(tenantId, productCode, request);
  }

  @DeleteMapping("/{productCode}")
  NonQmProductResponse retire(@PathVariable UUID tenantId, @PathVariable String productCode, HttpServletRequest http) {
    authorizationService.authorize("WRITE_CATALOG", http.getHeader("X-Roles"));
    return repository.retire(tenantId, productCode);
  }

  @GetMapping("/schemas")
  Map<String, Object> schemas(HttpServletRequest http) {
    authorizationService.authorize("READ_CATALOG", http.getHeader("X-Roles"));
    return Map.of("productFamily", "NON_QM", "productTypes", schemas.supportedProductTypes());
  }

  @GetMapping("/schemas/{productType}")
  NonQmProductSchema schemaByType(@PathVariable String productType, HttpServletRequest http) {
    authorizationService.authorize("READ_CATALOG", http.getHeader("X-Roles"));
    return schemas.schema(productType);
  }

  @GetMapping("/{productCode}/schema")
  NonQmProductSchema schemaForProduct(@PathVariable UUID tenantId, @PathVariable String productCode, HttpServletRequest http) {
    authorizationService.authorize("READ_CATALOG", http.getHeader("X-Roles"));
    NonQmProductResponse product = repository.find(tenantId, productCode).orElseThrow(() -> new CatalogException("NON_QM_PRODUCT_NOT_FOUND"));
    return schemas.schema(product.productType());
  }

  @PostMapping("/{productCode}/validate")
  NonQmValidationResult validate(@PathVariable UUID tenantId, @PathVariable String productCode, @RequestBody Map<String, Object> attributes, HttpServletRequest http) {
    authorizationService.authorize("READ_CATALOG", http.getHeader("X-Roles"));
    NonQmProductResponse product = repository.find(tenantId, productCode).orElseThrow(() -> new CatalogException("NON_QM_PRODUCT_NOT_FOUND"));
    return schemas.validate(product.productType(), attributes);
  }

  @GetMapping("/export")
  List<NonQmProductExport> export(@PathVariable UUID tenantId, @RequestParam(required = false) String productType, HttpServletRequest http) {
    authorizationService.authorize("READ_CATALOG", http.getHeader("X-Roles"));
    return repository.export(tenantId, productType);
  }

  @PostMapping("/import")
  NonQmImportResult importProducts(@PathVariable UUID tenantId, @RequestBody NonQmImportRequest request, HttpServletRequest http) {
    authorizationService.authorize("WRITE_CATALOG", http.getHeader("X-Roles"));
    return repository.importProducts(tenantId, request);
  }

  @ExceptionHandler(CatalogException.class)
  ResponseEntity<Map<String, Object>> error(CatalogException ex, HttpServletRequest request) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("errorCode", ex.getMessage());
    body.put("code", ex.getMessage());
    body.put("message", ex.getMessage());
    body.put("correlationId", request.getHeader("X-Correlation-Id"));
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
  }
}
