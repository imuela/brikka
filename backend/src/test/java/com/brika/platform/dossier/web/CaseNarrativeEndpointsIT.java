package com.brika.platform.dossier.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brika.platform.casemgmt.CaseClientRepository;
import com.brika.platform.casemgmt.CaseService;
import com.brika.platform.casemgmt.ParticipationType;
import com.brika.platform.crm.ClientRepository;
import com.brika.platform.identity.CompanyRepository;
import com.brika.platform.identity.CreateUserCommand;
import com.brika.platform.identity.User;
import com.brika.platform.identity.UserProvisioningService;
import com.brika.platform.identity.UserRole;
import com.brika.platform.identity.web.StubJwtDecoderConfig;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * BRIKKA V2 I5. {@code GET /api/v1/cases/{caseId}/dossier/narrative} — read-only deterministic
 * narrative, reusing DOCUMENT_READ and the standard tenant / case / assignment masking. No file
 * storage involved (narrative only reads), so this needs Postgres but not MinIO.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(StubJwtDecoderConfig.class)
class CaseNarrativeEndpointsIT {

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
  @Autowired private CompanyRepository companyRepository;
  @Autowired private UserProvisioningService userProvisioningService;
  @Autowired private CaseService caseService;
  @Autowired private CaseClientRepository caseClientRepository;
  @Autowired private ClientRepository clientRepository;

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

  private UUID caseWithHolder(UUID companyId, TestPrincipal actor) {
    UUID caseId = caseService.createCase(companyId, actor.user().id(), "PURCHASE").id();
    UUID clientId =
        clientRepository.insert(
            companyId, "Cli", "Ent", "cli-" + UUID.randomUUID() + "@brika.test", "600");
    caseClientRepository.insert(caseId, clientId, ParticipationType.HOLDER, true);
    return caseId;
  }

  @Test
  void narrativeEndpointReturnsEveryStructuredSection() throws Exception {
    UUID companyId = companyRepository.insert("Co NARE1", "Co NARE1", "TC-NARE1");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-nare1");
    UUID caseId = caseWithHolder(companyId, manager);

    mockMvc
        .perform(
            get("/api/v1/cases/" + caseId + "/dossier/narrative")
                .header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sections.length()").value(8))
        .andExpect(jsonPath("$.sections[0].key").value("situation"))
        .andExpect(jsonPath("$.sections[0].paragraphs[0]").exists());
  }

  @Test
  void narrativeIsDeterministicOverHttp() throws Exception {
    UUID companyId = companyRepository.insert("Co NARE2", "Co NARE2", "TC-NARE2");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-nare2");
    UUID caseId = caseWithHolder(companyId, manager);

    String first = narrativeBody(manager, caseId);
    String second = narrativeBody(manager, caseId);
    org.assertj.core.api.Assertions.assertThat(first).isEqualTo(second);
  }

  private String narrativeBody(TestPrincipal actor, UUID caseId) throws Exception {
    return mockMvc
        .perform(
            get("/api/v1/cases/" + caseId + "/dossier/narrative")
                .header("Authorization", actor.bearer()))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  @Test
  void narrativeFromAnotherTenantIsNotFound() throws Exception {
    UUID companyA = companyRepository.insert("Co NARE3A", "Co NARE3A", "TC-NARE3A");
    UUID companyB = companyRepository.insert("Co NARE3B", "Co NARE3B", "TC-NARE3B");
    TestPrincipal managerA = createUser(UserRole.MANAGER, companyA, "manager-nare3a");
    TestPrincipal managerB = createUser(UserRole.MANAGER, companyB, "manager-nare3b");
    UUID caseId = caseWithHolder(companyA, managerA);

    mockMvc
        .perform(
            get("/api/v1/cases/" + caseId + "/dossier/narrative")
                .header("Authorization", managerB.bearer()))
        .andExpect(status().isNotFound());
  }

  @Test
  void narrativeForbiddenForUnassignedBroker() throws Exception {
    UUID companyId = companyRepository.insert("Co NARE4", "Co NARE4", "TC-NARE4");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-nare4");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-nare4");
    UUID caseId = caseWithHolder(companyId, manager);

    mockMvc
        .perform(
            get("/api/v1/cases/" + caseId + "/dossier/narrative")
                .header("Authorization", broker.bearer()))
        .andExpect(status().isForbidden());
  }

  @Test
  void narrativeRequiresAuthentication() throws Exception {
    UUID companyId = companyRepository.insert("Co NARE5", "Co NARE5", "TC-NARE5");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-nare5");
    UUID caseId = caseWithHolder(companyId, manager);

    mockMvc
        .perform(get("/api/v1/cases/" + caseId + "/dossier/narrative"))
        .andExpect(status().isUnauthorized());
  }
}
