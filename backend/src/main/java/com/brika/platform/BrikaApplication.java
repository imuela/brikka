package com.brika.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Sprint 0 skeleton entry point. No business logic, no persistence, no security beyond what Spring
 * Boot Actuator provides out of the box. See 25_CLAUDE_CODE_EXECUTION_GUIDE.md for what belongs in
 * later sprints.
 */
@SpringBootApplication
public class BrikaApplication {

  public static void main(String[] args) {
    SpringApplication.run(BrikaApplication.class, args);
  }
}
