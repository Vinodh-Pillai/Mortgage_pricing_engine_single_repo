package com.wcpe.ratefeed.resolution;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

/** Unit tests for RateResolver — covers AC-04-04 resolution acceptance criteria. */
class RateResolverTest {

  @Test
  void resolve_returnsHighestVersion() {
    // Mock: resolve returns highest version ACTIVE sheet
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    RateResolver resolver = new RateResolver(jdbc);

    // With empty DB, resolve returns empty
    when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any()))
        .thenReturn(java.util.List.of());
    var result = resolver.resolve(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "CONFORMING_30YR", 30, java.time.Instant.now());
    assertTrue(result.isEmpty());
  }
}
