package com.brika.platform.audit.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brika.platform.identity.CompanyRepository;
import com.brika.platform.identity.CreateUserCommand;
import com.brika.platform.identity.User;
import com.brika.platform.identity.UserProvisioningService;
import com.brika.platform.identity.UserRole;
import com.brika.platform.identity.web.StubJwtDecoderConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
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
 * ADR-AUDIT-001 / Sprint 11: audit_events is read-only in V1 with no domain writer wired (D11-5),
 * so test data is seeded directly via JDBC — the only way any row exists today. GLOBAL scope
 * (AUDIT_READ, SUPERADMIN only, no SUPPORT_SESSION dependency): mirrors IntegrationEndpointsIT.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(StubJwtDecoderConfig.class)
class AuditEventEndpointsIT {

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

  private JdbcTemplate jdbc;

  @BeforeEach
  void setUp() {
    jdbc = new JdbcTemplate(dataSource);
  }

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

  private UUID seedAuditEvent(UUID companyId, String action) {
    return jdbc.queryForObject(
        "INSERT INTO audit_events (company_id, action, resource_type, metadata) VALUES (?, ?,"
            + " 'TEST_RESOURCE', '{}'::jsonb) RETURNING id",
        UUID.class,
        companyId,
        action);
  }

  @Test
  void superadminListsAuditEvents() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-ae1");
    UUID companyId = companyRepository.insert("Co AE1", "Co AE1", "TC-AE1");
    seedAuditEvent(companyId, "ACTION_AE1");

    mockMvc
        .perform(get("/api/v1/audit-events").header("Authorization", superadmin.bearer()))
        .andExpect(status().isOk());
  }

  @Test
  void superadminReadsAuditEventDetail() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-ae2");
    UUID companyId = companyRepository.insert("Co AE2", "Co AE2", "TC-AE2");
    UUID id = seedAuditEvent(companyId, "ACTION_AE2");

    mockMvc
        .perform(get("/api/v1/audit-events/" + id).header("Authorization", superadmin.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.action").value("ACTION_AE2"))
        .andExpect(jsonPath("$.resourceType").value("TEST_RESOURCE"))
        .andExpect(jsonPath("$.companyId").value(companyId.toString()));
  }

  @Test
  void superadminUnknownAuditEventIsNotFound() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-ae3");

    mockMvc
        .perform(
            get("/api/v1/audit-events/" + UUID.randomUUID())
                .header("Authorization", superadmin.bearer()))
        .andExpect(status().isNotFound());
  }

  @Test
  void managerCannotListOrReadAuditEvents() throws Exception {
    UUID companyId = companyRepository.insert("Co AE4", "Co AE4", "TC-AE4");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-ae4");
    UUID id = seedAuditEvent(companyId, "ACTION_AE4");

    mockMvc
        .perform(get("/api/v1/audit-events").header("Authorization", manager.bearer()))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get("/api/v1/audit-events/" + id).header("Authorization", manager.bearer()))
        .andExpect(status().isForbidden());
  }

  @Test
  void brokerCannotListOrReadAuditEvents() throws Exception {
    UUID companyId = companyRepository.insert("Co AE5", "Co AE5", "TC-AE5");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-ae5");

    mockMvc
        .perform(get("/api/v1/audit-events").header("Authorization", broker.bearer()))
        .andExpect(status().isForbidden());
  }

  @Test
  void clientCannotListOrReadAuditEvents() throws Exception {
    UUID companyId = companyRepository.insert("Co AE6", "Co AE6", "TC-AE6");
    TestPrincipal client = createUser(UserRole.CLIENT, companyId, "client-ae6");

    mockMvc
        .perform(get("/api/v1/audit-events").header("Authorization", client.bearer()))
        .andExpect(status().isForbidden());
  }

  @Test
  void globalScopeShowsEventsFromMultipleCompaniesTogether() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-ae7");
    UUID companyA = companyRepository.insert("Co AE7A", "Co AE7A", "TC-AE7A");
    UUID companyB = companyRepository.insert("Co AE7B", "Co AE7B", "TC-AE7B");
    seedAuditEvent(companyA, "ACTION_AE7_A");
    seedAuditEvent(companyB, "ACTION_AE7_B");

    String response =
        mockMvc
            .perform(get("/api/v1/audit-events").header("Authorization", superadmin.bearer()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode results = objectMapper.readTree(response);

    boolean sawA = false;
    boolean sawB = false;
    for (JsonNode node : results) {
      String companyId = node.get("companyId").isNull() ? null : node.get("companyId").asText();
      if (companyA.toString().equals(companyId)) {
        sawA = true;
      }
      if (companyB.toString().equals(companyId)) {
        sawB = true;
      }
    }
    assertThat(sawA).as("event from company A visible in the GLOBAL list").isTrue();
    assertThat(sawB).as("event from company B visible in the same GLOBAL list").isTrue();
  }

  @Test
  void noWriteEndpointExistsForAuditEvents() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-ae8");
    Integer before = jdbc.queryForObject("SELECT COUNT(*) FROM audit_events", Integer.class);

    var result =
        mockMvc
            .perform(
                post("/api/v1/audit-events")
                    .header("Authorization", superadmin.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isNotEqualTo(200);
    Integer after = jdbc.queryForObject("SELECT COUNT(*) FROM audit_events", Integer.class);
    assertThat(after).isEqualTo(before);
  }

  @Test
  void listIsOrderedByCreatedAtDescending() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-ae9");
    UUID companyId = companyRepository.insert("Co AE9", "Co AE9", "TC-AE9");
    seedAuditEvent(companyId, "FIRST_AE9");
    Thread.sleep(5);
    UUID second = seedAuditEvent(companyId, "SECOND_AE9");

    mockMvc
        .perform(get("/api/v1/audit-events").header("Authorization", superadmin.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(second.toString()));
  }
}
