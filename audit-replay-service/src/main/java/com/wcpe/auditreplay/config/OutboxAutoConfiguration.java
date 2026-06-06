package com.wcpe.auditreplay.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OutboxProperties.class)
@ConditionalOnProperty(name = "wcpe.audit.outbox.publisher.enabled", matchIfMissing = true)
public class OutboxAutoConfiguration {
}
