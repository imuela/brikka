package com.brika.platform.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.brika.platform.identity.CompanyRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Structural tests for ADR-PLATFORM-001 (plans/entitlements/company_subscriptions). No plan or
 * entitlement catalog is seeded by any migration: no approved document defines actual plan tiers or
 * entitlement codes, so inventing one here would be inventing a business rule (CLAUDE.md §3). These
 * tests exercise the model with ad-hoc rows, the same way Company/User are tested.
 */
@Testcontainers
@SpringBootTest
class PlanEntitlementIT {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("brika_test")
          .withUsername("brika_test")
          .withPassword("brika_test");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private CompanyRepository companyRepository;
  @Autowired private PlanRepository planRepository;
  @Autowired private EntitlementRepository entitlementRepository;
  @Autowired private PlanEntitlementRepository planEntitlementRepository;
  @Autowired private CompanySubscriptionRepository companySubscriptionRepository;
  @Autowired private EntitlementResolutionService entitlementResolutionService;

  @Test
  void companySubscriptionResolvesTheEntitlementsGrantedByItsPlan() {
    UUID companyId = companyRepository.insert("Test Co PE1", "Test Co PE1", "TC-PE1");
    UUID planId = planRepository.insert("TEST_PLAN", "Test Plan", "ACTIVE");
    UUID maxCasesId =
        entitlementRepository.insert(
            "TEST_MAX_CASES",
            "Test Max Cases",
            "Max concurrent cases",
            EntitlementValueType.NUMERIC);
    UUID aiEnabledId =
        entitlementRepository.insert(
            "TEST_AI_ENABLED",
            "Test AI Enabled",
            "AI features toggle",
            EntitlementValueType.BOOLEAN);

    planEntitlementRepository.grant(planId, maxCasesId, "50");
    planEntitlementRepository.grant(planId, aiEnabledId, "true");
    companySubscriptionRepository.insert(companyId, planId, "ACTIVE");

    Map<String, String> entitlements =
        entitlementResolutionService.entitlementValuesForCompany(companyId);

    assertThat(entitlements).hasSize(2);
    assertThat(entitlements.get("TEST_MAX_CASES")).isEqualTo("50");
    assertThat(entitlements.get("TEST_AI_ENABLED")).isEqualTo("true");
  }

  @Test
  void companyWithoutSubscriptionResolvesNoEntitlements() {
    UUID companyId = companyRepository.insert("Test Co PE2", "Test Co PE2", "TC-PE2");

    Map<String, String> entitlements =
        entitlementResolutionService.entitlementValuesForCompany(companyId);

    assertThat(entitlements).isEmpty();
  }

  @Test
  void companySubscriptionIsUniquePerCompany() {
    UUID companyId = companyRepository.insert("Test Co PE3", "Test Co PE3", "TC-PE3");
    UUID planId = planRepository.insert("TEST_PLAN_2", "Test Plan 2", "ACTIVE");
    companySubscriptionRepository.insert(companyId, planId, "ACTIVE");

    assertThatThrownBy(() -> companySubscriptionRepository.insert(companyId, planId, "ACTIVE"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
