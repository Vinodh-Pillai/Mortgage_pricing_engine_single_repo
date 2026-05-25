package com.wcpe.eligibility.config;

import com.wcpe.eligibility.client.CatalogClient;
import com.wcpe.eligibility.domain.rules.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CatalogClientConfig {

    @Bean
    public CatalogClient catalogClient() {
        return new CatalogClient();
    }

    @Bean
    public EligibilityRule ficoMinimumRule() {
        return new FicoMinimumRule();
    }

    @Bean
    public EligibilityRule ltvRule() {
        return new LtvRule();
    }

    @Bean
    public EligibilityRule dtiRule() {
        return new DtiRule();
    }

    @Bean
    public EligibilityRule propertyTypeRule() {
        return new PropertyTypeRule();
    }

    @Bean
    public EligibilityRule occupancyRule() {
        return new OccupancyRule();
    }

    @Bean
    public EligibilityRule loanPurposeRule() {
        return new LoanPurposeRule();
    }

    @Bean
    public EligibilityRule investorRule(CatalogClient catalogClient) {
        return new InvestorRule(catalogClient);
    }

    @Bean
    public EligibilityRule productRule(CatalogClient catalogClient) {
        return new ProductRule(catalogClient);
    }

    @Bean
    public EligibilityRule channelRule(CatalogClient catalogClient) {
        return new ChannelRule(catalogClient);
    }

    @Bean
    public EligibilityRule stateRule(CatalogClient catalogClient) {
        return new StateRule(catalogClient);
    }

    @Bean
    public EligibilityRule loanAmountRule(CatalogClient catalogClient) {
        return new LoanAmountRule(catalogClient);
    }

    @Bean
    public EligibilityRule documentationTypeRule() {
        return new DocumentationTypeRule();
    }
}
