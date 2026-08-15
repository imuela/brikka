package com.brika.platform;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Sprint 0 smoke test: the application context must load with no datasource, no security and no
 * business modules configured.
 */
@SpringBootTest
class BrikaApplicationTests {

  @Test
  void contextLoads() {}
}
