package com.wcpe.underwriting.nonqm;

import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Configuration
class UnderwritingPersistenceConfig {
  @Bean
  @ConditionalOnMissingBean(DataSource.class)
  DataSource dataSource(Environment environment) {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.postgresql.Driver");
    dataSource.setUrl(required(environment, "spring.datasource.url"));
    dataSource.setUsername(required(environment, "spring.datasource.username"));
    dataSource.setPassword(required(environment, "spring.datasource.password"));
    return dataSource;
  }

  @Bean
  @ConditionalOnMissingBean(NonQmUnderwritingApi.class)
  NonQmUnderwritingApi nonQmUnderwritingApi() {
    return new NonQmUnderwritingApi();
  }

  @Bean
  @ConditionalOnMissingBean(UnderwritingResultStore.class)
  UnderwritingResultStore underwritingResultStore(DataSource dataSource, com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
    return new JdbcUnderwritingResultStore(dataSource, objectMapper);
  }

  private static String required(Environment environment, String key) {
    String value = environment.getProperty(key);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(key + " is required");
    }
    return value;
  }
}
