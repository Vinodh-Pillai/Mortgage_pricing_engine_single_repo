package com.wcpe.tenantcontext;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PersistenceClockConfig {
    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }
}
