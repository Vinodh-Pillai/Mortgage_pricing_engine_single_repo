package com.wcpe.quote.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.wcpe.quote.AsyncQuoteJobLifecycleTest;
import com.wcpe.quote.InMemoryQuoteCache;
import com.wcpe.quote.QuoteApplicationService;
import com.wcpe.quote.QuoteJobResponse;
import com.wcpe.quote.QuoteTestSupport;
import org.junit.jupiter.api.Test;

class AsyncQuoteApiPollingContractTest {
    @Test
    void postPollAndCancelQuoteJobReturnContractFields() {
        QuoteApplicationService service = QuoteTestSupport.service(QuoteTestSupport.dependenciesWithPolicy(), new InMemoryQuoteCache());
        QuoteController controller = new QuoteController(service);

        QuoteJobResponse created = controller.postTenantQuoteJob(
            QuoteTestSupport.TENANT,
            "async-api",
            "corr-api",
            true,
            AsyncQuoteJobLifecycleTest.startRequest("ignored", true)
        );
        QuoteJobResponse polled = controller.getTenantQuoteJob(QuoteTestSupport.TENANT, created.jobId());
        QuoteJobResponse cancelled = controller.postTenantQuoteJobCancel(QuoteTestSupport.TENANT, created.jobId(), "actor-1", "corr-api");

        assertThat(created.status()).isEqualTo("QUEUED");
        assertThat(created.statusUrl()).isEqualTo("/api/v1/tenants/" + QuoteTestSupport.TENANT + "/quote-jobs/" + created.jobId());
        assertThat(created.retryAfterSeconds()).isEqualTo(5);
        assertThat(polled.jobId()).isEqualTo(created.jobId());
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(cancelled.retryAfterSeconds()).isZero();
        assertThat(cancelled.auditRef()).contains("corr-api");
    }
}
