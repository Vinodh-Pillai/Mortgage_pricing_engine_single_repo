package com.wcpe.auditreplay;

import com.wcpe.auditreplay.config.OutboxProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(OutboxProperties.class)
public class AuditReplayApplication {
  public static void main(String[] args) {
    SpringApplication.run(AuditReplayApplication.class, args);
  }
}
