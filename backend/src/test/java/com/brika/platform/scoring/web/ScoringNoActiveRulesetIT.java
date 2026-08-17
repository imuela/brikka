package com.brika.platform.scoring.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brika.platform.casemgmt.web.CreateCaseApiRequest;
import com.brika.platform.identity.CompanyRepository;
import com.brika.platform.identity.CreateUserCommand;
import com.brika.platform.identity.User;
import com.brika.platform.identity.UserProvisioningService;
import com.brika.platform.identity.UserRole;
import com.brika.platform.identity.web.StubJwtDecoderConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * ADR-SCORING-001 D9-11: a fresh database with zero scoring_rulesets ever created must reject
 * /scoring/run with NO_ACTIVE_SCORING_RULESET. Kept in its own class/container — scoring_rulesets
 * is GLOBAL (D9-6), so this assertion would be unreliable in a class shared with any test that
 * creates a ruleset.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(StubJwtDecoderConfig.class)
class ScoringNoActiveRulesetIT {

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

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private CompanyRepository companyRepository;
  @Autowired private UserProvisioningService userProvisioningService;

  private record TestPrincipal(String externalIdentityId, User user) {
    String bearer() {
      return "Bearer " + externalIdentityId;
    }
  }

  private TestPrincipal createUser(UserRole role, UUID companyId, String emailPrefix) {
    String externalId = "ext-" + UUID.randomUUID();
    User user =
        userProvisioningService.createUser(
            new CreateUserCommand(
                role, companyId, externalId, emailPrefix + "@brika.test", "First", "Last"));
    return new TestPrincipal(externalId, user);
  }

  @Test
  void noActiveRulesetRejectsScoringRun() throws Exception {
    UUID companyId = companyRepository.insert("Co SNA1", "Co SNA1", "TC-SNA1");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-sna1");

    String caseBody = objectMapper.writeValueAsString(new CreateCaseApiRequest("MORTGAGE"));
    String caseResponse =
        mockMvc
            .perform(
                post("/api/v1/cases")
                    .header("Authorization", manager.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(caseBody))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID caseId = UUID.fromString(objectMapper.readTree(caseResponse).get("id").asText());

    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/scoring/run")
                .header("Authorization", manager.bearer()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("NO_ACTIVE_SCORING_RULESET"));
  }
}
