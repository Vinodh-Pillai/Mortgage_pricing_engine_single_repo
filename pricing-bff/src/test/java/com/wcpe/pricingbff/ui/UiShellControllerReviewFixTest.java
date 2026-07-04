package com.wcpe.pricingbff.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.wcpe.pricingbff.los.LosApiModels.CreditApplicationField;
import com.wcpe.pricingbff.los.LosApiModels.CreditApplicationValue;
import com.wcpe.pricingbff.los.LosApiModels.LoanPassExecutionProductSummary;
import com.wcpe.pricingbff.los.LosApiModels.LoanPassExecutionSummaryResponse;
import com.wcpe.pricingbff.los.LosApiModels.LoanPassExecutionSummaryTotals;
import com.wcpe.pricingbff.los.LosApiModels.LoanPassProductExecutionResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class UiShellControllerReviewFixTest {

  @Test
  void pricingWaterfallUsesExecuteProductWhenSelectedProductEvidenceExists() {
    PricingBffUiFallbackAdapter adapter = adapterWithSummaryAndProduct(summary(
        product("product-1", List.of(field("rate.noteRate", "5.875"))),
        product("product-2", List.of(field("rate.noteRate", "5.875")))),
        productDetail("product-2", List.of(field("rate.noteRate", "6.125"), field("quote-service-price", "101.25"))));

    PricingBffUiFallbackAdapter.PricingWaterfallView view =
        adapter.pricingWaterfall("tenant-a", "run-1", "product-2", "trace-review-fix");

    assertThat(view.status()).isEqualTo("QUOTE_SERVICE_LOANPASS_PRODUCT_VISIBLE");
    assertThat(view.baseSelection().selectionId()).isEqualTo("quote-service-product:product-2");
    assertThat(view.baseSelection().selectedNoteRate().value()).isEqualTo("6.125");
    assertThat(view.finalPrice().roundedFinalPrice().value()).isEqualTo("101.25");
    assertThat(view.blockers()).isEmpty();
  }

  @Test
  void pricingWaterfallFailsClosedWhenExecuteProductReturnsDifferentProductForSelectedOffer() {
    PricingBffUiFallbackAdapter adapter = adapterWithSummaryAndProduct(summary(
        product("product-1", List.of(field("rate.noteRate", "5.875"))),
        product("product-2", List.of(field("rate.noteRate", "5.875")))),
        productDetail("product-1", List.of(field("rate.noteRate", "6.125"), field("quote-service-price", "101.25"))));

    PricingBffUiFallbackAdapter.PricingWaterfallView view =
        adapter.pricingWaterfall("tenant-a", "run-1", "product-2", "trace-review-fix");

    assertThat(view.status()).isEqualTo("BLOCKED_SELECTED_PRODUCT_MISMATCH");
    assertThat(view.restrictedValuesVisible()).isFalse();
    assertThat(view.blockers()).extracting(PricingBffUiFallbackAdapter.WaterfallBlocker::code)
        .containsExactly("SELECTED_PRODUCT_MISMATCH");
    assertThat(view.fallbackReason()).contains("requested selected offer/product product-2");
  }

  @Test
  void quoteDetailFailsClosedWhenExecuteProductReturnsDifferentProductForSelectedOffer() {
    PricingBffUiFallbackAdapter adapter = adapterWithSummaryAndProduct(summary(),
        productDetail("product-1", List.of(field("rate.noteRate", "6.125"), field("quote-service-price", "101.25"))));

    PricingBffUiFallbackAdapter.QuoteDetailView view =
        adapter.quoteDetail("tenant-a", "run-1", "product-2", "trace-review-fix");

    assertThat(view.status()).isEqualTo("BLOCKED_SELECTED_PRODUCT_MISMATCH");
    assertThat(view.waterfall().blockers()).extracting(PricingBffUiFallbackAdapter.WaterfallBlocker::code)
        .containsExactly("SELECTED_PRODUCT_MISMATCH");
    assertThat(view.fallbackReason()).contains("requested selected offer/product product-2");
  }

  @Test
  void pricingWaterfallFailsClosedWhenMultipleProductsHaveNoSelectedProductEvidence() {
    PricingBffUiFallbackAdapter adapter = adapterWithSummary(summary(
        product("product-1", List.of(field("rate.noteRate", "5.875"))),
        product("product-2", List.of(field("rate.noteRate", "6.125")))));

    PricingBffUiFallbackAdapter.PricingWaterfallView view =
        adapter.pricingWaterfall("tenant-a", "run-1", null, "trace-review-fix");

    assertThat(view.status()).isEqualTo("BLOCKED_AMBIGUOUS_PRODUCT_SELECTION");
    assertThat(view.restrictedValuesVisible()).isFalse();
    assertThat(view.blockers()).extracting(PricingBffUiFallbackAdapter.WaterfallBlocker::code)
        .containsExactly("AMBIGUOUS_PRODUCT_SELECTION");
    assertThat(view.fallbackReason()).contains("did not choose the first product");
  }

  @Test
  void pricingWaterfallFailsClosedForNullOrSparseCalculatedFields() {
    PricingBffUiFallbackAdapter adapter = adapterWithSummary(summary(
        product("product-sparse", List.of(new CreditApplicationField("rate.noteRate", null)))));

    PricingBffUiFallbackAdapter.PricingWaterfallView view =
        adapter.pricingWaterfall("tenant-a", "run-1", null, "trace-review-fix");

    assertThat(view.status()).isEqualTo("BLOCKED_MISSING_LIVE_PRICING_FIELDS");
    assertThat(view.blockers()).extracting(PricingBffUiFallbackAdapter.WaterfallBlocker::code)
        .containsExactly("MISSING_LIVE_PRICING_FIELDS");
    assertThat(view.fallbackReason()).contains("sparse calculatedFields");
  }

  @Test
  void executeProductWaterfallFailsClosedForSparseCalculatedFields() {
    LoanPassProductExecutionResult product = new LoanPassProductExecutionResult("product-sparse", "Sparse product",
        "SPARSE", "Investor", null, true, List.of(), List.of(new CreditApplicationField("rate.noteRate", null)),
        Map.of("type", "approved"), "v-review-fix", Map.of("source", "quote-service.execute-product"));

    PricingBffUiFallbackAdapter.PricingWaterfallView view =
        PricingBffUiFallbackAdapter.waterfallFromLoanPassProduct("tenant-a", "run-1", "trace-review-fix", product);

    assertThat(view.status()).isEqualTo("BLOCKED_MISSING_LIVE_PRICING_FIELDS");
    assertThat(view.blockers()).extracting(PricingBffUiFallbackAdapter.WaterfallBlocker::code)
        .containsExactly("MISSING_LIVE_PRICING_FIELDS");
    assertThat(view.fallbackReason()).contains("sparse calculatedFields");
  }

  @Test
  void scenarioAnalysisHandlesSparseLiveSummaryWithoutThrowing() {
    PricingBffUiFallbackAdapter adapter = adapterWithSummary(summary(
        product("product-sparse", null)));

    PricingBffUiFallbackAdapter.ScenarioAnalysisWorkspaceView view =
        adapter.scenarioAnalysisWorkspace("tenant-a", "run-1", "trace-review-fix");

    assertThat(view.dependencyStatus()).isEqualTo("QUOTE_SERVICE_LIVE_RECORDS_AVAILABLE");
    assertThat(view.variants()).hasSize(1);
    assertThat(view.variants().get(0).status()).isEqualTo("BLOCKED");
    assertThat(view.blockers()).extracting(PricingBffUiFallbackAdapter.ScenarioAnalysisBlocker::blockerCode)
        .containsExactly("MISSING_LIVE_PRICE_FIELDS");
  }

  @Test
  void scenarioAnalysisBlocksRateLikeFieldsWhenValuesAreNullOrBlank() {
    PricingBffUiFallbackAdapter adapter = adapterWithSummary(summary(
        product("product-sparse", List.of(
            new CreditApplicationField("rate.noteRate", null),
            field("quote-service-price", " ")))));

    PricingBffUiFallbackAdapter.ScenarioAnalysisWorkspaceView view =
        adapter.scenarioAnalysisWorkspace("tenant-a", "run-1", "trace-review-fix");

    assertThat(view.variants()).hasSize(1);
    assertThat(view.variants().get(0).status()).isEqualTo("BLOCKED");
    assertThat(view.variants().get(0).resultRefs()).isEmpty();
    assertThat(view.batchGrid().get(0).status()).isEqualTo("BLOCKED");
    assertThat(view.blockers()).extracting(PricingBffUiFallbackAdapter.ScenarioAnalysisBlocker::blockerCode)
        .containsExactly("MISSING_LIVE_PRICE_FIELDS");
  }

  @Test
  void lockWorkflowDoesNotExposePseudoReadyStatesWithoutLiveLockServiceContract() {
    PricingBffUiFallbackAdapter adapter = adapterWithSummary(summary());

    PricingBffUiFallbackAdapter.LockWorkflowView view = adapter.lockWorkflow("run-1", "offer-1", "trace-lock");

    assertThat(view.status()).isEqualTo("BLOCKED_LIVE_CONTRACT_REQUIRED");
    assertThat(view.stateTransitions()).extracting(PricingBffUiFallbackAdapter.LockStateTransition::toState)
        .doesNotContain("READY_FOR_LOCK_REQUEST", "SUBMISSION_PENDING_BACKEND")
        .containsOnly("BLOCKED_LIVE_CONTRACT_REQUIRED");
  }

  private static PricingBffUiFallbackAdapter adapterWithSummary(LoanPassExecutionSummaryResponse summary) {
    return new PricingBffUiFallbackAdapter(new StubQuoteServiceClient(summary, null));
  }

  private static PricingBffUiFallbackAdapter adapterWithSummaryAndProduct(LoanPassExecutionSummaryResponse summary,
      LoanPassProductExecutionResult product) {
    return new PricingBffUiFallbackAdapter(new StubQuoteServiceClient(summary, product));
  }

  private static LoanPassExecutionSummaryResponse summary(LoanPassExecutionProductSummary... products) {
    return new LoanPassExecutionSummaryResponse(new LoanPassExecutionSummaryTotals(products.length, 0, products.length, 0, 0),
        List.of(products), "v-review-fix", Map.of("warnings", List.of()));
  }

  private static LoanPassExecutionProductSummary product(String productId, List<CreditApplicationField> calculatedFields) {
    return new LoanPassExecutionProductSummary(productId, "Product " + productId, "CODE-" + productId, "Investor", null,
        List.of(), calculatedFields, true, Map.of("type", "approved"), "v-review-fix");
  }

  private static LoanPassProductExecutionResult productDetail(String productId, List<CreditApplicationField> calculatedFields) {
    return new LoanPassProductExecutionResult(productId, "Product " + productId, "CODE-" + productId, "Investor", null,
        true, List.of(), calculatedFields, Map.of("type", "approved"), "v-review-fix",
        Map.of("source", "quote-service.execute-product"));
  }

  private static CreditApplicationField field(String fieldId, String value) {
    return new CreditApplicationField(fieldId, new CreditApplicationValue("string", value, null, null));
  }

  private static class StubQuoteServiceClient extends PricingBffQuoteServiceLoanPassClient {
    private final LoanPassExecutionSummaryResponse summary;
    private final LoanPassProductExecutionResult product;

    StubQuoteServiceClient(LoanPassExecutionSummaryResponse summary, LoanPassProductExecutionResult product) {
      super(RestClient.builder(), "https://quote-service.test", null, null);
      this.summary = summary;
      this.product = product;
    }

    @Override
    LoanPassExecutionSummaryResponse executeSummary(String tenantId, String runId, String traceId,
        List<CreditApplicationField> creditApplicationFields) {
      return summary;
    }

    @Override
    LoanPassProductExecutionResult executeProduct(String tenantId, String runId, String productId, String traceId,
        List<CreditApplicationField> creditApplicationFields) {
      return product;
    }
  }
}
