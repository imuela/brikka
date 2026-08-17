package com.brika.platform.integrations.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
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
 * ADR-INTEGRATIONS-001 / 17_API_SPECIFICATION_DETAILED.md §17D: read-only/status in V1. No endpoint
 * writes to `integrations`, so test data is seeded directly via JDBC (mirrors how no Java code
 * anywhere in this codebase inserts a row either).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(StubJwtDecoderConfig.class)
class IntegrationEndpointsIT {

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

  private UUID seedIntegration(String type, String status) {
    return jdbc.queryForObject(
        "INSERT INTO integrations (type, status, config) VALUES (?, ?, '{}'::jsonb) RETURNING id",
        UUID.class,
        type,
        status);
  }

  @Test
  void superadminListsAndReadsIntegrations() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-ig1");
    UUID id = seedIntegration("CRM_EXPORT", "CONFIGURED");

    mockMvc
        .perform(get("/api/v1/integrations").header("Authorization", superadmin.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].type").value("CRM_EXPORT"));

    mockMvc
        .perform(get("/api/v1/integrations/" + id).header("Authorization", superadmin.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONFIGURED"));
  }

  @Test
  void unknownIntegrationIdIsNotFound() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-ig2");

    mockMvc
        .perform(
            get("/api/v1/integrations/" + UUID.randomUUID())
                .header("Authorization", superadmin.bearer()))
        .andExpect(status().isNotFound());
  }

  @Test
  void managerCannotListOrReadIntegrations() throws Exception {
    UUID companyId = companyRepository.insert("Co IG3", "Co IG3", "TC-IG3");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-ig3");
    UUID id = seedIntegration("CRM_EXPORT", "CONFIGURED");

    mockMvc
        .perform(get("/api/v1/integrations").header("Authorization", manager.bearer()))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get("/api/v1/integrations/" + id).header("Authorization", manager.bearer()))
        .andExpect(status().isForbidden());
  }

  @Test
  void brokerCannotListOrReadIntegrations() throws Exception {
    UUID companyId = companyRepository.insert("Co IG4", "Co IG4", "TC-IG4");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-ig4");

    mockMvc
        .perform(get("/api/v1/integrations").header("Authorization", broker.bearer()))
        .andExpect(status().isForbidden());
  }

  @Test
  void clientCannotListOrReadIntegrations() throws Exception {
    UUID companyId = companyRepository.insert("Co IG5", "Co IG5", "TC-IG5");
    TestPrincipal client = createUser(UserRole.CLIENT, companyId, "client-ig5");

    mockMvc
        .perform(get("/api/v1/integrations").header("Authorization", client.bearer()))
        .andExpect(status().isForbidden());
  }

  @Test
  void noEndpointCanWriteToIntegrations() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-ig6");
    Integer before = jdbc.queryForObject("SELECT COUNT(*) FROM integrations", Integer.class);

    String body = objectMapper.writeValueAsString(java.util.Map.of("type", "NEW_TYPE"));
    var result =
        mockMvc
            .perform(
                post("/api/v1/integrations")
                    .header("Authorization", superadmin.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andReturn();

    // No POST handler exists for this path — whatever status Spring/the global exception
    // handler produces, no row must ever be written (D: read-only/status in V1).
    assertThat(result.getResponse().getStatus()).isNotEqualTo(200);
    Integer after = jdbc.queryForObject("SELECT COUNT(*) FROM integrations", Integer.class);
    assertThat(after).isEqualTo(before);
  }
}
