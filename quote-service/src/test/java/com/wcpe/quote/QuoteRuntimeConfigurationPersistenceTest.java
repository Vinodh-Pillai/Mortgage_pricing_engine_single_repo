package com.wcpe.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

class QuoteRuntimeConfigurationPersistenceTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(QuoteRuntimeConfiguration.class)
        .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void postgresModeWiresJdbcRepositoriesAsSourceOfTruth() {
        contextRunner
            .withPropertyValues("quote.persistence.mode=postgres")
            .withBean(DataSource.class, () -> mock(DataSource.class))
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(QuoteRepository.class)).isInstanceOf(JdbcQuoteRepository.class);
                assertThat(context.getBean(QuoteJobRepository.class)).isInstanceOf(JdbcQuoteJobRepository.class);
                assertThat(context.getBean(QuoteSnapshotRepository.class)).isInstanceOf(JdbcQuoteSnapshotRepository.class);
                assertThat(context.getBean(QuoteCache.class)).isInstanceOf(LocalQuoteCache.class);
            });
    }

    @Test
    void postgresModeFailsClosedWhenDatasourceContractIsMissing() {
        contextRunner
            .withPropertyValues("quote.persistence.mode=postgres")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .hasMessageContaining("QUOTE_POSTGRES_DATASOURCE_REQUIRED")
                    .hasMessageContaining("SPRING_DATASOURCE_URL")
                    .hasMessageContaining("SPRING_DATASOURCE_USERNAME");
            });
    }

    @Test
    void missingModeFailsClosedWhenDatasourceContractIsMissing() {
        contextRunner
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .hasMessageContaining("QUOTE_POSTGRES_DATASOURCE_REQUIRED")
                    .hasMessageContaining("SPRING_DATASOURCE_URL")
                    .hasMessageContaining("SPRING_DATASOURCE_USERNAME");
            });
    }

    @Test
    void failClosedModeDoesNotWireProductionSourceOfTruthRepositories() {
        contextRunner
            .withPropertyValues("quote.persistence.mode=fail-closed")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .hasMessageContaining("No qualifying bean of type 'com.wcpe.quote.QuoteRepository'");
            });
    }

    @Test
    void explicitTestConfigurationCanStillUseInMemoryRepositories() {
        new ApplicationContextRunner()
            .withUserConfiguration(QuoteRuntimeConfiguration.class, TestOnlyInMemoryPersistenceConfiguration.class)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withPropertyValues("quote.persistence.mode=fail-closed")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(QuoteRepository.class)).isInstanceOf(InMemoryQuoteRepository.class);
                assertThat(context.getBean(QuoteJobRepository.class)).isInstanceOf(InMemoryQuoteJobRepository.class);
                assertThat(context.getBean(QuoteSnapshotRepository.class)).isInstanceOf(InMemoryQuoteSnapshotRepository.class);
            });
    }

    @Test
    void syntheticDependenciesAreOptInForDevQuoteProofOnly() {
        new ApplicationContextRunner()
            .withUserConfiguration(QuoteRuntimeConfiguration.class, TestOnlyInMemoryPersistenceConfiguration.class)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withPropertyValues(
                "quote.persistence.mode=fail-closed",
                "quote.synthetic-dependencies.enabled=true"
            )
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(QuoteDependencies.class)).isInstanceOf(SyntheticQuoteDependencies.class);
                Quote quote = context.getBean(QuoteApplicationService.class).createQuote(QuoteTestSupport.request("idem-synthetic-config"));
                assertThat(quote.status()).isEqualTo(QuoteStatus.READY);
                assertThat(quote.options()).isNotEmpty();
                assertThat(quote.inputVersionSet().rankingPolicyVersion()).isEqualTo(SyntheticQuoteDependencies.POLICY_VERSION);
            });
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestOnlyInMemoryPersistenceConfiguration {
        @Bean
        QuoteRepository testQuoteRepository() {
            return new InMemoryQuoteRepository();
        }

        @Bean
        QuoteJobRepository testQuoteJobRepository() {
            return new InMemoryQuoteJobRepository();
        }

        @Bean
        QuoteSnapshotRepository testQuoteSnapshotRepository() {
            return new InMemoryQuoteSnapshotRepository();
        }
    }
}
