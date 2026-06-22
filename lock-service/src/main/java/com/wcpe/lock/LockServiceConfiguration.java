package com.wcpe.lock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LockPersistenceGate.class)
public class LockServiceConfiguration {
  @Bean
  LockService lockService() {
    return new LockService();
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
