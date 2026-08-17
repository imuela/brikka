package com.brika.platform.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brika.platform.identity.CompanyRepository;
import com.brika.platform.identity.CreateUserCommand;
import com.brika.platform.identity.User;
import com.brika.platform.identity.UserProvisioningService;
import com.brika.platform.identity.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sprint 12.1 (ADR-PLATFORM-002): companies lifecycle — CRUD, tenant isolation, RBAC (dual scope:
 * SUPERADMIN GLOBAL, MANAGER TENANT on their own company only), and D-MASTER-2 (DELETE is a logical
 * status transition, never a physical row delete).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(StubJwtDecoderConfig.class)
class CompanyEndpointsIT {

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
  @Autowired private DataSource dataSource;

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
  void superadminCreatesReadsAndListsCompanies() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-co1");

    String createBody =
        objectMapper.writeValueAsString(
            new CreateCompanyApiRequest("Legal CO1", "Trade CO1", "TAX-CO1"));
    String response =
        mockMvc
            .perform(
                post("/api/v1/companies")
                    .header("Authorization", superadmin.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.legalName").value("Legal CO1"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID id = UUID.fromString(objectMapper.readTree(response).get("id").asText());

    mockMvc
        .perform(get("/api/v1/companies/" + id).header("Authorization", superadmin.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.taxId").value("TAX-CO1"));

    mockMvc
        .perform(get("/api/v1/companies").header("Authorization", superadmin.bearer()))
        .andExpect(status().isOk());
  }

  @Test
  void managerCannotCreateCompany() throws Exception {
    UUID companyId = companyRepository.insert("Co MG1", "Co MG1", "TC-MG1");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-co2");

    String body = objectMapper.writeValueAsString(new CreateCompanyApiRequest("X", "X", "X-TAX"));
    mockMvc
        .perform(
            post("/api/v1/companies")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
  }

  @Test
  void managerReadsAndUpdatesOnlyOwnCompany() throws Exception {
    UUID companyA = companyRepository.insert("Co CA1", "Co CA1", "TC-CA1");
    UUID companyB = companyRepository.insert("Co CB1", "Co CB1", "TC-CB1");
    TestPrincipal managerA = createUser(UserRole.MANAGER, companyA, "manager-ca1");

    mockMvc
        .perform(get("/api/v1/companies/" + companyA).header("Authorization", managerA.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(companyA.toString()));

    mockMvc
        .perform(get("/api/v1/companies/" + companyB).header("Authorization", managerA.bearer()))
        .andExpect(status().isNotFound());

    String updateBody =
        objectMapper.writeValueAsString(
            new UpdateCompanyApiRequest("Updated Legal", "Updated Trade", "TC-CA1-U"));
    mockMvc
        .perform(
            patch("/api/v1/companies/" + companyA)
                .header("Authorization", managerA.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.legalName").value("Updated Legal"));

    mockMvc
        .perform(
            patch("/api/v1/companies/" + companyB)
                .header("Authorization", managerA.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
        .andExpect(status().isNotFound());
  }

  @Test
  void managerListOnlyContainsOwnCompany() throws Exception {
    UUID companyA = companyRepository.insert("Co LC1", "Co LC1", "TC-LC1");
    companyRepository.insert("Co LC2", "Co LC2", "TC-LC2");
    TestPrincipal managerA = createUser(UserRole.MANAGER, companyA, "manager-lc1");

    mockMvc
        .perform(get("/api/v1/companies").header("Authorization", managerA.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].id").value(companyA.toString()));
  }

  @Test
  void brokerAndClientCannotReadCompanies() throws Exception {
    UUID companyId = companyRepository.insert("Co BC1", "Co BC1", "TC-BC1");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-bc1");
    TestPrincipal client = createUser(UserRole.CLIENT, companyId, "client-bc1");

    mockMvc
        .perform(get("/api/v1/companies").header("Authorization", broker.bearer()))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get("/api/v1/companies").header("Authorization", client.bearer()))
        .andExpect(status().isForbidden());
  }

  @Test
  void managerCannotSuspendOrDeleteOwnCompany() throws Exception {
    UUID companyId = companyRepository.insert("Co MS1", "Co MS1", "TC-MS1");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-ms1");

    mockMvc
        .perform(
            post("/api/v1/companies/" + companyId + "/suspend")
                .header("Authorization", manager.bearer()))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(delete("/api/v1/companies/" + companyId).header("Authorization", manager.bearer()))
        .andExpect(status().isForbidden());
  }

  @Test
  void superadminSuspendsCompanyAndCannotSuspendTwice() throws Exception {
    UUID companyId = companyRepository.insert("Co SU1", "Co SU1", "TC-SU1");
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-su1");

    mockMvc
        .perform(
            post("/api/v1/companies/" + companyId + "/suspend")
                .header("Authorization", superadmin.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUSPENDED"));

    mockMvc
        .perform(
            post("/api/v1/companies/" + companyId + "/suspend")
                .header("Authorization", superadmin.bearer()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("COMPANY_NOT_ACTIVE"));
  }

  @Test
  void deletingACompanyIsALogicalTransitionNeverAPhysicalRowDelete() throws Exception {
    UUID companyId = companyRepository.insert("Co DEL1", "Co DEL1", "TC-DEL1");
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-del1");
    // A dependent row (a user of this company) exists — a real SQL DELETE would violate the
    // company_id FK the instant it ran. Proves D-MASTER-2 is enforced structurally, not just by
    // convention.
    createUser(UserRole.MANAGER, companyId, "manager-del1");

    mockMvc
        .perform(
            delete("/api/v1/companies/" + companyId).header("Authorization", superadmin.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DELETED"));

    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    Integer stillPresent =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM companies WHERE id = ?", Integer.class, companyId);
    assertThat(stillPresent).as("the row must still physically exist").isEqualTo(1);

    mockMvc
        .perform(
            delete("/api/v1/companies/" + companyId).header("Authorization", superadmin.bearer()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("COMPANY_ALREADY_DELETED"));
  }
}
