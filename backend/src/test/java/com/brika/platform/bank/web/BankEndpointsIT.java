package com.brika.platform.bank.web;

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
import com.brika.platform.identity.web.StubJwtDecoderConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
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
 * End-to-end security/tenant tests for Bank/BankProduct/BankCriteriaVersion (GLOBAL catalog) and
 * BankContact (TENANT, PRIVATE visibility) — Sprint 5.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(StubJwtDecoderConfig.class)
class BankEndpointsIT {

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

  private UUID createBank(TestPrincipal superadmin, String code) throws Exception {
    String body =
        objectMapper.writeValueAsString(new CreateBankApiRequest(code, "Bank " + code, Map.of()));
    String response =
        mockMvc
            .perform(
                post("/api/v1/banks")
                    .header("Authorization", superadmin.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(objectMapper.readTree(response).get("id").asText());
  }

  @Test
  void superadminManagesBankCatalogWithoutAnyTenant() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-bk1");
    UUID bankId = createBank(superadmin, "BK1");

    mockMvc
        .perform(get("/api/v1/banks/" + bankId).header("Authorization", superadmin.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("BK1"));

    String updateBody =
        objectMapper.writeValueAsString(
            new UpdateBankApiRequest("Bank BK1 renamed", "INACTIVE", Map.of()));
    mockMvc
        .perform(
            patch("/api/v1/banks/" + bankId)
                .header("Authorization", superadmin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INACTIVE"));

    String productBody =
        objectMapper.writeValueAsString(
            new CreateBankProductApiRequest("P1", "Product 1", Map.of()));
    mockMvc
        .perform(
            post("/api/v1/banks/" + bankId + "/products")
                .header("Authorization", superadmin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(productBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("P1"));

    Map<String, Object> maxLtvRule =
        Map.of(
            "id", "max-ltv-80",
            "field", "computed.ltv",
            "operator", "LESS_THAN_OR_EQUAL",
            "value", 0.80,
            "severity", "FAIL",
            "reason", "LTV must not exceed 80%");
    String criteriaBody =
        objectMapper.writeValueAsString(
            new CreateBankCriteriaVersionApiRequest(
                "v1", null, null, Map.of("rules", List.of(maxLtvRule))));
    mockMvc
        .perform(
            post("/api/v1/banks/" + bankId + "/criteria")
                .header("Authorization", superadmin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(criteriaBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value("v1"));
  }

  @Test
  void brokerCanReadBanksButCannotCreateOrUpdate() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-bk2");
    UUID bankId = createBank(superadmin, "BK2");
    UUID companyId = companyRepository.insert("Co BK2", "Co BK2", "TC-BK2");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-bk2");

    mockMvc
        .perform(get("/api/v1/banks").header("Authorization", broker.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].code", org.hamcrest.Matchers.hasItem("BK2")));

    String body =
        objectMapper.writeValueAsString(new CreateBankApiRequest("BK2B", "Bank BK2B", Map.of()));
    mockMvc
        .perform(
            post("/api/v1/banks")
                .header("Authorization", broker.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());

    String updateBody =
        objectMapper.writeValueAsString(new UpdateBankApiRequest("x", "ACTIVE", Map.of()));
    mockMvc
        .perform(
            patch("/api/v1/banks/" + bankId)
                .header("Authorization", broker.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
        .andExpect(status().isForbidden());
  }

  @Test
  void clientCannotReadBankCatalog() throws Exception {
    UUID companyId = companyRepository.insert("Co BK3", "Co BK3", "TC-BK3");
    TestPrincipal client = createUser(UserRole.CLIENT, companyId, "client-bk3");

    mockMvc
        .perform(get("/api/v1/banks").header("Authorization", client.bearer()))
        .andExpect(status().isForbidden());
  }

  @Test
  void managerCreatesPrivateBankContactVisibleOnlyToOwnerAndManager() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-bc1");
    UUID bankId = createBank(superadmin, "BC1");
    UUID companyId = companyRepository.insert("Co BC1", "Co BC1", "TC-BC1");
    TestPrincipal brokerOwner = createUser(UserRole.BROKER, companyId, "broker-bc1-owner");
    TestPrincipal brokerOther = createUser(UserRole.BROKER, companyId, "broker-bc1-other");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-bc1");

    String body =
        objectMapper.writeValueAsString(
            new CreateBankContactApiRequest(
                bankId,
                "Jane Doe",
                "Manager",
                null,
                null,
                "jane@bank.test",
                null,
                null,
                null,
                "PRIVATE"));
    String response =
        mockMvc
            .perform(
                post("/api/v1/bank-contacts")
                    .header("Authorization", brokerOwner.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID contactId = UUID.fromString(objectMapper.readTree(response).get("id").asText());

    mockMvc
        .perform(
            get("/api/v1/bank-contacts/" + contactId).header("Authorization", brokerOwner.bearer()))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/v1/bank-contacts/" + contactId).header("Authorization", manager.bearer()))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/v1/bank-contacts/" + contactId).header("Authorization", brokerOther.bearer()))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(get("/api/v1/bank-contacts").header("Authorization", brokerOther.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  void companyVisibilityBankContactIsVisibleToOtherBrokers() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-bc2");
    UUID bankId = createBank(superadmin, "BC2");
    UUID companyId = companyRepository.insert("Co BC2", "Co BC2", "TC-BC2");
    TestPrincipal brokerOwner = createUser(UserRole.BROKER, companyId, "broker-bc2-owner");
    TestPrincipal brokerOther = createUser(UserRole.BROKER, companyId, "broker-bc2-other");

    String body =
        objectMapper.writeValueAsString(
            new CreateBankContactApiRequest(
                bankId,
                "John Smith",
                null,
                null,
                null,
                "john@bank.test",
                null,
                null,
                null,
                "COMPANY"));
    mockMvc
        .perform(
            post("/api/v1/bank-contacts")
                .header("Authorization", brokerOwner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/v1/bank-contacts").header("Authorization", brokerOther.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)));
  }

  @Test
  void bankContactFromAnotherTenantIsNotFound() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-bc3");
    UUID bankId = createBank(superadmin, "BC3");
    UUID companyA = companyRepository.insert("Co BC3A", "Co BC3A", "TC-BC3A");
    UUID companyB = companyRepository.insert("Co BC3B", "Co BC3B", "TC-BC3B");
    TestPrincipal managerA = createUser(UserRole.MANAGER, companyA, "manager-bc3a");
    TestPrincipal managerB = createUser(UserRole.MANAGER, companyB, "manager-bc3b");

    String body =
        objectMapper.writeValueAsString(
            new CreateBankContactApiRequest(
                bankId, "Contact B", null, null, null, null, null, null, null, "COMPANY"));
    String response =
        mockMvc
            .perform(
                post("/api/v1/bank-contacts")
                    .header("Authorization", managerB.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID contactId = UUID.fromString(objectMapper.readTree(response).get("id").asText());

    mockMvc
        .perform(
            get("/api/v1/bank-contacts/" + contactId).header("Authorization", managerA.bearer()))
        .andExpect(status().isNotFound());
  }

  @Test
  void ownerCanUpdateAndDeleteOwnPrivateBankContact() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-bc4");
    UUID bankId = createBank(superadmin, "BC4");
    UUID companyId = companyRepository.insert("Co BC4", "Co BC4", "TC-BC4");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-bc4");

    String createBody =
        objectMapper.writeValueAsString(
            new CreateBankContactApiRequest(
                bankId, "Mark", null, null, null, null, null, null, null, "PRIVATE"));
    String response =
        mockMvc
            .perform(
                post("/api/v1/bank-contacts")
                    .header("Authorization", broker.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID contactId = UUID.fromString(objectMapper.readTree(response).get("id").asText());

    String updateBody =
        objectMapper.writeValueAsString(
            new UpdateBankContactApiRequest(
                "Mark Updated", null, null, null, null, null, null, null, "PRIVATE", true));
    mockMvc
        .perform(
            patch("/api/v1/bank-contacts/" + contactId)
                .header("Authorization", broker.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Mark Updated"));

    mockMvc
        .perform(
            delete("/api/v1/bank-contacts/" + contactId).header("Authorization", broker.bearer()))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/v1/bank-contacts/" + contactId).header("Authorization", broker.bearer()))
        .andExpect(status().isNotFound());
  }

  @Test
  void superadminWithoutSupportSessionCannotAccessBankContacts() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-bc5");

    mockMvc
        .perform(get("/api/v1/bank-contacts").header("Authorization", superadmin.bearer()))
        .andExpect(status().isForbidden());
  }
}
