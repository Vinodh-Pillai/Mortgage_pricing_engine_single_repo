package com.wcpe.adjustment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AdjustmentServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(AdjustmentServiceApplication.class, args);
  }
}
