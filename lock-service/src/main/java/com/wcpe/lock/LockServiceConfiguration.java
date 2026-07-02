package com.wcpe.lock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@EnableConfigurationProperties(LockPersistenceGate.class)
public class LockServiceConfiguration {
  @Bean
  LockService lockService(
      ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
      ObjectProvider<ObjectMapper> objectMapperProvider,
      LockPersistenceGate persistenceGate) {
    JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    LockRepository repository;
    if (jdbcTemplate == null) {
      persistenceGate.markJdbcRepositoryAvailable(false);
      repository = new LockRepository();
    } else {
      persistenceGate.markJdbcRepositoryAvailable(true);
      repository = new JdbcLockRepository(jdbcTemplate, objectMapperProvider.getIfAvailable(ObjectMapper::new));
    }
    return new LockService(repository);
  }

  @Bean
  LockDetailApi lockDetailApi(LockService lockService) {
    return new LockDetailApi(lockService);
  }

  @Bean
  LockConfirmationApi lockConfirmationApi(LockService lockService) {
    return new LockConfirmationApi(lockService);
  }

  @Bean
  LockExtensionApi lockExtensionApi(LockService lockService) {
    return new LockExtensionApi(lockService);
  }
}
