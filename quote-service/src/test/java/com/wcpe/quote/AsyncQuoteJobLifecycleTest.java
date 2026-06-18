package com.wcpe.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

public class AsyncQuoteJobLifecycleTest {
    @Test
    void startsAndReplaysQueuedJobByIdempotencyKey() {
        QuoteApplicationService service = QuoteTestSupport.service(QuoteTestSupport.dependenciesWithPolicy(), new InMemoryQuoteCache());

        QuoteJob first = service.startQuoteJob(startRequest("async-idem", true));
        QuoteJob replay = service.startQuoteJob(startRequest("async-idem", true));

        assertThat(replay.jobId()).isEqualTo(first.jobId());
        assertThat(replay.status()).isEqualTo(QuoteJobStatus.QUEUED);
        assertThat(replay.progress()).containsEntry("stage", "queued");
        assertThat(service.outboxEvents()).extracting(OutboxEvent::eventType)
            .containsExactly("quote_job.created.v1");
        assertThat(service.auditEntries()).extracting(AuditEntry::action)
            .containsExactly("QUOTE_JOB_CREATED");
    }

    @Test
    void conflictsWhenIdempotencyKeyHasDifferentPayload() {
        QuoteApplicationService service = QuoteTestSupport.service(QuoteTestSupport.dependenciesWithPolicy(), new InMemoryQuoteCache());
        service.startQuoteJob(startRequest("async-conflict", true));

        QuoteJobStartRequest different = new QuoteJobStartRequest(
            QuoteTestSupport.TENANT,
            QuoteTestSupport.SCENARIO,
            8,
            QuoteTestSupport.request("ignored").requestedLockPeriods(),
            QuoteTestSupport.request("ignored").filters(),
            "USD",
            Map.of("source", "unit-test", "asyncMaxAttempts", "3"),
            "actor-1",
            "async-conflict",
            "corr-1",
            QuoteTestSupport.request("ignored").effectiveDate(),
            true
        );

        assertThatThrownBy(() -> service.startQuoteJob(different))
            .isInstanceOf(QuoteCreateException.class)
            .satisfies(ex -> assertThat(((QuoteCreateException) ex).code()).isEqualTo("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void claimCompleteAndCancelRespectStateMachineAndAudit() {
        QuoteApplicationService service = QuoteTestSupport.service(QuoteTestSupport.dependenciesWithPolicy(), new InMemoryQuoteCache());
        QuoteJob queued = service.startQuoteJob(startRequest("async-complete", true));

        QuoteJob running = service.claimQuoteJob(QuoteTestSupport.TENANT, queued.jobId(), "worker-1", "corr-worker");
        QuoteJob completed = service.completeQuoteJob(QuoteTestSupport.TENANT, running.jobId(), "worker-1", "corr-worker");

        assertThat(running.status()).isEqualTo(QuoteJobStatus.RUNNING);
        assertThat(completed.status()).isEqualTo(QuoteJobStatus.COMPLETED);
        assertThat(completed.quoteId()).isNotNull();
        assertThat(service.getQuote(QuoteTestSupport.TENANT, completed.quoteId()).auditRef()).isEqualTo("audit:corr-1");
        assertThat(service.outboxEvents()).extracting(OutboxEvent::eventType)
            .contains("quote_job.created.v1", "quote_job.running.v1", "quote.created.v1", "quote.ready.v1", "quote_job.completed.v1", "quote.callback_requested.v1");
        assertThat(service.auditEntries()).extracting(AuditEntry::action)
            .contains("QUOTE_JOB_CREATED", "QUOTE_JOB_RUNNING", "QUOTE_ORCHESTRATION_COMPLETED", "QUOTE_JOB_COMPLETED", "QUOTE_CALLBACK_REQUESTED");
        assertThatThrownBy(() -> service.cancelQuoteJob(QuoteTestSupport.TENANT, completed.jobId(), "actor-1", "corr-cancel"))
            .isInstanceOf(QuoteCreateException.class)
            .satisfies(ex -> assertThat(((QuoteCreateException) ex).code()).isEqualTo("VERSION_CONFLICT"));
    }

    @Test
    void failedJobRecordsFailureEvidenceEventAndAudit() {
        QuoteApplicationService service = QuoteTestSupport.service(QuoteTestSupport.dependenciesWithPolicy(), new InMemoryQuoteCache());
        QuoteJob queued = service.startQuoteJob(startRequest("async-failed", true));

        QuoteJob failed = service.failQuoteJob(QuoteTestSupport.TENANT, queued.jobId(), "DEPENDENCY_TIMEOUT", "pricing dependency timed out", "worker-1", "corr-worker");
        QuoteJobResponse response = QuoteJobResponse.from(failed);

        assertThat(failed.status()).isEqualTo(QuoteJobStatus.FAILED);
        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.failureCode()).isEqualTo("DEPENDENCY_TIMEOUT");
        assertThat(response.failureDetail()).isEqualTo("pricing dependency timed out");
        assertThat(response.retryAfterSeconds()).isZero();
        assertThat(service.outboxEvents()).extracting(OutboxEvent::eventType)
            .contains("quote_job.created.v1", "quote_job.failed.v1", "quote.callback_requested.v1");
        assertThat(service.auditEntries()).extracting(AuditEntry::action)
            .contains("QUOTE_JOB_CREATED", "QUOTE_JOB_FAILED", "QUOTE_CALLBACK_REQUESTED");
        OutboxEvent callback = service.outboxEvents().stream()
            .filter(event -> event.eventType().equals("quote.callback_requested.v1"))
            .findFirst()
            .orElseThrow();
        assertThat(callback.payload()).containsEntry("quoteJobId", failed.jobId().toString());
        assertThat(callback.payload()).containsEntry("status", "FAILED");
        assertThat(callback.payload()).doesNotContainKeys("borrowerLastName", "loanNumber", "creditScore");
    }

    @Test
    void failsClosedWhenAsyncPreferenceOrPolicyIsMissing() {
        QuoteApplicationService service = QuoteTestSupport.service(QuoteTestSupport.dependenciesWithPolicy(), new InMemoryQuoteCache());

        assertThatThrownBy(() -> service.startQuoteJob(startRequest("sync-not-allowed", false)))
            .isInstanceOf(QuoteCreateException.class)
            .satisfies(ex -> assertThat(((QuoteCreateException) ex).code()).isEqualTo("POLICY_NOT_SATISFIED"));
        assertThatThrownBy(() -> service.startQuoteJob(startRequestWithoutRetryPolicy("missing-retry")))
            .isInstanceOf(QuoteCreateException.class)
            .satisfies(ex -> assertThat(((QuoteCreateException) ex).code()).isEqualTo("POLICY_NOT_SATISFIED"));
    }

    public static QuoteJobStartRequest startRequest(String idempotencyKey, boolean preferAsync) {
        QuoteCreateRequest quoteRequest = QuoteTestSupport.request(idempotencyKey);
        return new QuoteJobStartRequest(
            quoteRequest.tenantId(),
            quoteRequest.scenarioId(),
            quoteRequest.scenarioVersion(),
            quoteRequest.requestedLockPeriods(),
            quoteRequest.filters(),
            quoteRequest.presentationCurrency(),
            Map.of("source", "unit-test", "asyncMaxAttempts", "3"),
            quoteRequest.actorId(),
            quoteRequest.idempotencyKey(),
            quoteRequest.correlationId(),
            quoteRequest.effectiveDate(),
            preferAsync
        );
    }

    static QuoteJobStartRequest startRequestWithoutRetryPolicy(String idempotencyKey) {
        QuoteCreateRequest quoteRequest = QuoteTestSupport.request(idempotencyKey);
        return new QuoteJobStartRequest(
            quoteRequest.tenantId(),
            quoteRequest.scenarioId(),
            quoteRequest.scenarioVersion(),
            quoteRequest.requestedLockPeriods(),
            quoteRequest.filters(),
            quoteRequest.presentationCurrency(),
            quoteRequest.clientContext(),
            quoteRequest.actorId(),
            quoteRequest.idempotencyKey(),
            quoteRequest.correlationId(),
            quoteRequest.effectiveDate(),
            true
        );
    }
}
