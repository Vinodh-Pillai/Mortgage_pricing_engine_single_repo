package com.wcpe.auditreplay.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wcpe.auditreplay.domain.AuditRecord;
import com.wcpe.auditreplay.repository.AuditRecordRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

class AuditSearchFilterValidatorTest {

    private final AuditRecordRepository repository = mock(AuditRecordRepository.class);
    private final AuditSearchService service = new AuditSearchService(repository);

    @Test
    void rejectsUnboundedOrUnsupportedFilters() {
        MultiValueMap<String, String> unbounded = new LinkedMultiValueMap<>();

        IllegalArgumentException missingRange = assertThrows(
                IllegalArgumentException.class,
                () -> service.search(UUID.randomUUID(), unbounded));
        assertEquals("Bounded date range is required: from and to", missingRange.getMessage());

        MultiValueMap<String, String> unsupported = boundedFilters();
        unsupported.add("borrowerName", "Alice Applicant");

        IllegalArgumentException unsupportedFilter = assertThrows(
                IllegalArgumentException.class,
                () -> service.search(UUID.randomUUID(), unsupported));
        assertEquals("Unsupported audit search filter: borrowerName", unsupportedFilter.getMessage());

        MultiValueMap<String, String> unsupportedPurpose = boundedFilters();
        unsupportedPurpose.add("purpose", "compliance-review");

        IllegalArgumentException unsupportedPurposeFilter = assertThrows(
                IllegalArgumentException.class,
                () -> service.search(UUID.randomUUID(), unsupportedPurpose));
        assertEquals("Unsupported audit search filter: purpose", unsupportedPurposeFilter.getMessage());

        MultiValueMap<String, String> unsupportedEventType = boundedFilters();
        unsupportedEventType.add("eventType", "AuditIntegrityVerified.v1");

        IllegalArgumentException unsupportedEventTypeFilter = assertThrows(
                IllegalArgumentException.class,
                () -> service.search(UUID.randomUUID(), unsupportedEventType));
        assertEquals("Unsupported audit search filter: eventType", unsupportedEventTypeFilter.getMessage());
    }

    @Test
    void acceptsUnknownIntegrityStatusAndRejectsUnknownStatusValues() {
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        MultiValueMap<String, String> unknownStatus = boundedFilters();
        unknownStatus.set("integrityStatus", "UNKNOWN");

        AuditSearchService.AuditSearchPage page = service.search(UUID.randomUUID(), unknownStatus);
        assertEquals(0, page.count());

        MultiValueMap<String, String> unsupportedStatus = boundedFilters();
        unsupportedStatus.set("integrityStatus", "UNSIGNED");

        IllegalArgumentException unsupportedIntegrityStatus = assertThrows(
                IllegalArgumentException.class,
                () -> service.search(UUID.randomUUID(), unsupportedStatus));
        assertEquals("integrityStatus supports VERIFIED or UNKNOWN", unsupportedIntegrityStatus.getMessage());
    }

    @Test
    void returnsRedactedPageSchemaWithHashedActorAndCursorMetadata() {
        AuditRecord first = record(Instant.parse("2026-06-07T10:00:00Z"), "quote-1");
        AuditRecord second = record(Instant.parse("2026-06-07T09:00:00Z"), "quote-2");
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second)));

        MultiValueMap<String, String> filters = boundedFilters();
        filters.add("limit", "1");
        AuditSearchService.AuditSearchPage page = service.search(first.getTenantId(), filters);

        assertEquals(1, page.count());
        assertEquals(true, page.hasMore());
        assertEquals(first.getId(), page.results().get(0).auditRecordId());
        assertEquals("VERIFIED", page.results().get(0).integrityStatus());
        assertEquals(64, page.results().get(0).actorIdHash().length());
        verify(repository).findAll(any(Specification.class), any(Pageable.class));
    }

    private static MultiValueMap<String, String> boundedFilters() {
        MultiValueMap<String, String> filters = new LinkedMultiValueMap<>();
        filters.add("from", "2026-06-07T00:00:00Z");
        filters.add("to", "2026-06-08T00:00:00Z");
        filters.add("subjectType", "quote");
        filters.add("integrityStatus", "VERIFIED");
        return filters;
    }

    private static AuditRecord record(Instant occurredAt, String subjectId) {
        return AuditRecord.create(
                UUID.fromString("00000000-0000-0000-0000-000000000013"),
                UUID.randomUUID(),
                "QUOTE_CREATED",
                "quote",
                subjectId,
                1L,
                "SERVICE",
                "quote-service",
                "Quote Service",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "source-ip-hash",
                "user-agent-hash",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "{\"borrower\":\"REDACTED\"}".getBytes(StandardCharsets.UTF_8),
                "mortgage-pricing-default-v1",
                "{\"retentionPolicy\":\"tenant-config-ref\"}".getBytes(StandardCharsets.UTF_8),
                "SUCCESS",
                null,
                occurredAt,
                LocalDate.parse("2033-06-07"),
                false,
                "sha-256-fixture",
                null);
    }
}
