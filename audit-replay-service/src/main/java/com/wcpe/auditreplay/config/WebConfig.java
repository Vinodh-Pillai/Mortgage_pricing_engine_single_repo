package com.wcpe.auditreplay.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CorrelationContextResolver correlationContextResolver;

    public WebConfig(CorrelationContextResolver correlationContextResolver) {
        this.correlationContextResolver = correlationContextResolver;
    }

    @Override
    public void addArgumentResolvers(java.util.List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(correlationContextResolver);
    }
}
