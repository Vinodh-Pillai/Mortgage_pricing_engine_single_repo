package com.wcpe.ratefeed.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.ratefeed.activation.ActivationService;
import com.wcpe.ratefeed.activation.VersionManager;
import com.wcpe.ratefeed.audit.AuditService;
import com.wcpe.ratefeed.resolution.GridLookup;
import com.wcpe.ratefeed.resolution.RateResolver;
import com.wcpe.ratefeed.service.ReplayRepository;
import com.wcpe.ratefeed.service.ReplayService;
import org.springframework.jdbc.core.JdbcTemplate;

public final class TestRateFeedServices {
  private TestRateFeedServices() {}

  public static RateFeedService create(RateFeedRepository repository) {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    return create(repository, jdbc);
  }

  public static RateFeedService create(RateFeedRepository repository, JdbcTemplate jdbc) {
    AuditService auditService = new AuditService(jdbc);
    return new RateFeedService(
        repository,
        jdbc,
        new ObjectMapper(),
        new ActivationService(jdbc, auditService),
        new VersionManager(jdbc),
        new RateResolver(jdbc),
        new GridLookup(jdbc),
        auditService,
        new ReplayService(jdbc, new ReplayRepository(jdbc)));
  }
}
