package com.brika.platform.contract.web;

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
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

/**
 * Sprint 32. End-to-end tests for engagement contract generation.
 *
 * <p>Sprint 38 audit (D38-1): generating a contract really uploads its HTML to object storage
 * (DocumentService -&gt; StorageClient.upload), so this class needs its own MinIO the same way
 * DocumentServiceIT/DocumentEndpointsIT already do — without it, these tests only pass locally by
 * accident (docker-compose's MinIO happening to be up on :19000, brika.storage.endpoint's default)
 * and fail everywhere else, including CI, with a real connection-refused 500. Verified on GitHub
 * Actions: this exact failure predates this fix (reproduced identically as far back as the Sprint
 * 35 close commit).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(StubJwtDecoderConfig.class)
class EngagementContractEndpointsIT {

  private static final String BUCKET = "brika-documents-test";

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("brika_test")
          .withUsername("brika_test")
          .withPassword("brika_test");

  @Container
  static final MinIOContainer MINIO =
      new MinIOContainer("minio/minio:RELEASE.2025-09-07T16-13-09Z");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("brika.storage.endpoint", MINIO::getS3URL);
    registry.add("brika.storage.access-key", MINIO::getUserName);
    registry.add("brika.storage.secret-key", MINIO::getPassword);
    registry.add("brika.storage.bucket", () -> BUCKET);
    createBucket();
  }

  private static void createBucket() {
    try (S3Client client =
        S3Client.builder()
            .endpointOverride(URI.create(MINIO.getS3URL()))
            .region(Region.US_EAST_1)
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(MINIO.getUserName(), MINIO.getPassword())))
            .forcePathStyle(true)
            .build()) {
      client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
    }
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

  private UUID createCaseWithClient(UUID companyId, TestPrincipal actor, String emailPrefix) {
    UUID caseId = caseService.createCase(companyId, actor.user().id(), "PURCHASE").id();
    UUID clientId =
        clientRepository.insert(companyId, "Cli", "Ent", emailPrefix + "@brika.test", "600000000");
    caseClientRepository.insert(caseId, clientId, ParticipationType.HOLDER, true);
    return caseId;
  }

  @Test
  void managerGeneratesAContractAndRegeneratingAddsANewVersion() throws Exception {
    UUID companyId = companyRepository.insert("Co EC1", "Co EC1", "TC-EC1");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-ec1");
    UUID caseId = createCaseWithClient(companyId, manager, "cli-ec1");

    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/contract").header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.versionNumber").value(1))
        .andExpect(jsonPath("$.mimeType").value("text/html"));

    mockMvc
        .perform(
            get("/api/v1/cases/" + caseId + "/contract").header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.versions.length()").value(1));

    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/contract").header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.versionNumber").value(2));

    mockMvc
        .perform(
            get("/api/v1/cases/" + caseId + "/contract").header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.versions.length()").value(2)); // append-only history, both kept
  }

  @Test
  void noContractGeneratedYetReturnsAnEmptyHistory() throws Exception {
    UUID companyId = companyRepository.insert("Co EC2", "Co EC2", "TC-EC2");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-ec2");
    UUID caseId = createCaseWithClient(companyId, manager, "cli-ec2");

    mockMvc
        .perform(
            get("/api/v1/cases/" + caseId + "/contract").header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.documentId").doesNotExist())
        .andExpect(jsonPath("$.versions.length()").value(0));
  }

  @Test
  void noClientsOnCaseIsRejectedWithA400() throws Exception {
    UUID companyId = companyRepository.insert("Co EC3", "Co EC3", "TC-EC3");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-ec3");
    UUID caseId = caseService.createCase(companyId, manager.user().id(), "PURCHASE").id();

    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/contract").header("Authorization", manager.bearer()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("NO_CLIENTS_ON_CASE"));
  }

  @Test
  void brokerNotAssignedToTheCaseIsForbidden() throws Exception {
    UUID companyId = companyRepository.insert("Co EC4", "Co EC4", "TC-EC4");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-ec4");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-ec4");
    UUID caseId = createCaseWithClient(companyId, manager, "cli-ec4");

    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/contract").header("Authorization", broker.bearer()))
        .andExpect(status().isForbidden());
  }

  @Test
  void superadminIsGlobalAcrossTenants() throws Exception {
    UUID companyId = companyRepository.insert("Co EC5", "Co EC5", "TC-EC5");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-ec5");
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-ec5");
    UUID caseId = createCaseWithClient(companyId, manager, "cli-ec5");

    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/contract")
                .header("Authorization", superadmin.bearer()))
        .andExpect(status().isOk());
  }

  @Test
  void managerFromAnotherCompanyGetsAMaskedNotFoundNotForbidden() throws Exception {
    UUID companyA = companyRepository.insert("Co EC6A", "Co EC6A", "TC-EC6A");
    UUID companyB = companyRepository.insert("Co EC6B", "Co EC6B", "TC-EC6B");
    TestPrincipal managerA = createUser(UserRole.MANAGER, companyA, "manager-ec6a");
    TestPrincipal managerB = createUser(UserRole.MANAGER, companyB, "manager-ec6b");
    UUID caseId = createCaseWithClient(companyA, managerA, "cli-ec6a");

    mockMvc
        .perform(
            get("/api/v1/cases/" + caseId + "/contract").header("Authorization", managerB.bearer()))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/contract")
                .header("Authorization", managerB.bearer()))
        .andExpect(status().isNotFound());
  }

  @Test
  void unauthenticatedRequestIsRejected() throws Exception {
    UUID companyId = companyRepository.insert("Co EC7", "Co EC7", "TC-EC7");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-ec7");
    UUID caseId = createCaseWithClient(companyId, manager, "cli-ec7");

    mockMvc
        .perform(get("/api/v1/cases/" + caseId + "/contract"))
        .andExpect(status().isUnauthorized());
  }
}
