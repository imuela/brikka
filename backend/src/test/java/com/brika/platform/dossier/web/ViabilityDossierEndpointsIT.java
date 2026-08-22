package com.brika.platform.dossier.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/** Sprint 32. End-to-end tests for the viability dossier generation. */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(StubJwtDecoderConfig.class)
class ViabilityDossierEndpointsIT {

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
  @Autowired private ClientRepository clientRepository;
  @Autowired private CaseService caseService;
  @Autowired private CaseClientRepository caseClientRepository;

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

  private UUID createCaseWithClients(
      UUID companyId, TestPrincipal actor, String emailPrefix, int clientCount) {
    UUID caseId = caseService.createCase(companyId, actor.user().id(), "PURCHASE").id();
    for (int i = 0; i < clientCount; i++) {
      UUID clientId =
          clientRepository.insert(
              companyId, "Cli" + i, "Ent", emailPrefix + "-" + i + "@brika.test", "600000000");
      caseClientRepository.insert(
          caseId,
          clientId,
          i == 0 ? ParticipationType.HOLDER : ParticipationType.CO_HOLDER,
          i == 0);
    }
    return caseId;
  }

  @Test
  void managerGeneratesADossierWithEmptyOptionalSectionsGracefully() throws Exception {
    UUID companyId = companyRepository.insert("Co VD1", "Co VD1", "TC-VD1");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-vd1");
    UUID caseId = createCaseWithClients(companyId, manager, "cli-vd1", 1);

    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/dossier").header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.versionNumber").value(1))
        .andExpect(jsonPath("$.mimeType").value("text/html"));

    mockMvc
        .perform(
            get("/api/v1/cases/" + caseId + "/dossier").header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.versions.length()").value(1));
  }

  @Test
  void multipleClientsAreAllRepresentedInTheDossier() throws Exception {
    UUID companyId = companyRepository.insert("Co VD2", "Co VD2", "TC-VD2");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-vd2");
    UUID caseId = createCaseWithClients(companyId, manager, "cli-vd2", 2);

    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/dossier").header("Authorization", manager.bearer()))
        .andExpect(status().isOk());
  }

  @Test
  void regeneratingAddsANewImmutableVersionKeepingHistory() throws Exception {
    UUID companyId = companyRepository.insert("Co VD3", "Co VD3", "TC-VD3");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-vd3");
    UUID caseId = createCaseWithClients(companyId, manager, "cli-vd3", 1);

    mockMvc.perform(
        post("/api/v1/cases/" + caseId + "/dossier").header("Authorization", manager.bearer()));
    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/dossier").header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.versionNumber").value(2));

    mockMvc
        .perform(
            get("/api/v1/cases/" + caseId + "/dossier").header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.versions.length()").value(2));
  }

  @Test
  void noClientsOnCaseIsRejectedWithA400() throws Exception {
    UUID companyId = companyRepository.insert("Co VD4", "Co VD4", "TC-VD4");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-vd4");
    UUID caseId = caseService.createCase(companyId, manager.user().id(), "PURCHASE").id();

    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/dossier").header("Authorization", manager.bearer()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("NO_CLIENTS_ON_CASE"));
  }

  @Test
  void brokerNotAssignedToTheCaseIsForbidden() throws Exception {
    UUID companyId = companyRepository.insert("Co VD5", "Co VD5", "TC-VD5");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-vd5");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-vd5");
    UUID caseId = createCaseWithClients(companyId, manager, "cli-vd5", 1);

    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/dossier").header("Authorization", broker.bearer()))
        .andExpect(status().isForbidden());
  }

  @Test
  void superadminIsGlobalAcrossTenants() throws Exception {
    UUID companyId = companyRepository.insert("Co VD6", "Co VD6", "TC-VD6");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-vd6");
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-vd6");
    UUID caseId = createCaseWithClients(companyId, manager, "cli-vd6", 1);

    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/dossier")
                .header("Authorization", superadmin.bearer()))
        .andExpect(status().isOk());
  }

  @Test
  void managerFromAnotherCompanyGetsAMaskedNotFoundNotForbidden() throws Exception {
    UUID companyA = companyRepository.insert("Co VD7A", "Co VD7A", "TC-VD7A");
    UUID companyB = companyRepository.insert("Co VD7B", "Co VD7B", "TC-VD7B");
    TestPrincipal managerA = createUser(UserRole.MANAGER, companyA, "manager-vd7a");
    TestPrincipal managerB = createUser(UserRole.MANAGER, companyB, "manager-vd7b");
    UUID caseId = createCaseWithClients(companyA, managerA, "cli-vd7a", 1);

    mockMvc
        .perform(
            get("/api/v1/cases/" + caseId + "/dossier").header("Authorization", managerB.bearer()))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/dossier").header("Authorization", managerB.bearer()))
        .andExpect(status().isNotFound());
  }

  @Test
  void unauthenticatedRequestIsRejected() throws Exception {
    UUID companyId = companyRepository.insert("Co VD8", "Co VD8", "TC-VD8");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-vd8");
    UUID caseId = createCaseWithClients(companyId, manager, "cli-vd8", 1);

    mockMvc
        .perform(get("/api/v1/cases/" + caseId + "/dossier"))
        .andExpect(status().isUnauthorized());
  }
}
