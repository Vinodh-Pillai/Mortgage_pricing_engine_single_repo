package com.wcpe.quote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.quote.parallel.ParallelPricingOrchestrator;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(QuoteRuntimeConfiguration.QuotePersistenceProperties.class)
class QuoteRuntimeConfiguration {
    @Bean
    @ConditionalOnMissingBean
    Clock quoteClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "quote.persistence", name = "mode", havingValue = "postgres", matchIfMissing = true)
    QuoteRepository jdbcQuoteRepository(DataSource dataSource, ObjectMapper objectMapper) {
        return new JdbcQuoteRepository(dataSource, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "quote.persistence", name = "mode", havingValue = "postgres", matchIfMissing = true)
    QuoteJobRepository jdbcQuoteJobRepository(DataSource dataSource, ObjectMapper objectMapper) {
        return new JdbcQuoteJobRepository(dataSource, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "quote.persistence", name = "mode", havingValue = "postgres", matchIfMissing = true)
    QuoteSnapshotRepository jdbcQuoteSnapshotRepository(DataSource dataSource, ObjectMapper objectMapper) {
        return new JdbcQuoteSnapshotRepository(dataSource, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    QuoteCache quoteCache() {
        return new LocalQuoteCache();
    }

    @Bean
    @ConditionalOnMissingBean
    BestExecutionRanker bestExecutionRanker() {
        return new BestExecutionRanker();
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    ParallelPricingOrchestrator parallelPricingOrchestrator() {
        return new ParallelPricingOrchestrator();
    }

    @Bean
    @ConditionalOnMissingBean
    QuoteDependencies quoteDependencies() {
        return new FailClosedQuoteDependencies();
    }

    @Bean
    @ConditionalOnMissingBean
    QuoteApplicationService quoteApplicationService(
        QuoteRepository repository,
        QuoteJobRepository jobRepository,
        QuoteSnapshotRepository snapshotRepository,
        QuoteDependencies dependencies,
        QuoteCache cache,
        BestExecutionRanker ranker,
        Clock clock,
        ParallelPricingOrchestrator parallelPricingOrchestrator
    ) {
        return new QuoteApplicationService(repository, jobRepository, snapshotRepository, dependencies, cache, ranker, clock, parallelPricingOrchestrator);
    }

    @Bean
    @ConditionalOnProperty(prefix = "quote.persistence", name = "mode", havingValue = "postgres", matchIfMissing = true)
    @ConditionalOnMissingBean
    DataSource postgresDataSource(
        @Value("${spring.datasource.url:}") String url,
        @Value("${spring.datasource.username:}") String username,
        @Value("${spring.datasource.password:}") String password
    ) {
        if (url == null || url.isBlank() || username == null || username.isBlank()) {
            throw new IllegalStateException("QUOTE_POSTGRES_DATASOURCE_REQUIRED: SPRING_DATASOURCE_URL and SPRING_DATASOURCE_USERNAME must be set");
        }
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(url);
        dataSource.setUser(username);
        dataSource.setPassword(password == null ? "" : password);
        return dataSource;
    }

    @ConfigurationProperties(prefix = "quote.persistence")
    record QuotePersistenceProperties(String mode) {}

    private static final class FailClosedQuoteDependencies implements QuoteDependencies {
        @Override
        public Optional<RankingPolicy> rankingPolicyFor(QuoteCreateRequest request) {
            throw unconfigured();
        }

        @Override
        public List<QuoteCandidate> candidatesFor(QuoteCreateRequest request) {
            throw unconfigured();
        }

        @Override
        public String eligibilityVersion() {
            throw unconfigured();
        }

        @Override
        public String pricingVersion() {
            throw unconfigured();
        }

        @Override
        public String adjustmentVersion() {
            throw unconfigured();
        }

        @Override
        public String marginVersion() {
            throw unconfigured();
        }

        private QuoteCreateException unconfigured() {
            return new QuoteCreateException(
                "QUOTE_DEPENDENCIES_UNCONFIGURED",
                "Quote runtime dependencies are not configured; refusing to fabricate pricing or ranking data"
            );
        }
    }
}
