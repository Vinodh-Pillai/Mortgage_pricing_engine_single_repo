package com.wcpe.pricingbff.ui;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ProductCatalogUiController {
  private final PricingBffUiFallbackAdapter adapter;

  ProductCatalogUiController(PricingBffUiFallbackAdapter adapter) {
    this.adapter = adapter;
  }

  @PostMapping("/api/v1/tenants/workspaces")
  ResponseEntity<?> createTenantWorkspace(@RequestBody(required = false) Map<String, Object> setup) {
    return adapter.createTenantWorkspace(setup);
  }

  @PostMapping("/api/v1/products/catalog")
  ResponseEntity<?> createProductCatalogEntry(@RequestBody(required = false) Map<String, Object> product) {
    return adapter.createProductCatalogEntry(product);
  }

  @GetMapping("/api/v1/products/catalog/manager")
  Object productCatalogManager(
      @RequestHeader(value = "X-Tenant-Context", required = false) String tenantContext,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.productCatalogManager(tenantContext, uiTraceId);
  }

  @GetMapping("/api/v1/tenants/{tenantId}/products")
  Object tenantProducts(@PathVariable String tenantId,
      @RequestParam(value = "page", required = false) Integer page,
      @RequestParam(value = "pageSize", required = false) Integer pageSize,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.tenantProducts(tenantId, page, pageSize, uiTraceId);
  }

  @GetMapping("/api/v1/tenants/{tenantId}/product-catalog/products")
  Object productCatalogProducts(@PathVariable String tenantId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.productCatalogProducts(tenantId, uiTraceId);
  }

  @GetMapping("/api/v1/tenants/{tenantId}/product-catalog/investors")
  Object productCatalogInvestors(@PathVariable String tenantId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.productCatalogInvestors(tenantId, uiTraceId);
  }

  @GetMapping("/api/v1/tenants/{tenantId}/product-catalog/channels")
  Object productCatalogChannels(@PathVariable String tenantId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.productCatalogChannels(tenantId, uiTraceId);
  }
}
