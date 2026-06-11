package com.wcpe.compliance;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ComplianceEvidenceServiceConfiguration {
  @Bean
  ComplianceEvidenceRegistryService complianceEvidenceRegistryService() {
    return new ComplianceEvidenceRegistryService();
  }
}
