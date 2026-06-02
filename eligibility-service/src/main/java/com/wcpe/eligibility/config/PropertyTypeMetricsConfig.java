package com.wcpe.eligibility.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PropertyTypeMetricsConfig {

    @Bean
    PropertyTypeMetrics propertyTypeMetrics(MeterRegistry registry) {
        return new PropertyTypeMetrics(registry);
    }
}
