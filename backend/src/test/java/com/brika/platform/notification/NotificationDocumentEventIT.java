package com.brika.platform.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.brika.platform.casemgmt.Case;
import com.brika.platform.casemgmt.CaseService;
import com.brika.platform.casemgmt.ParticipationType;
import com.brika.platform.crm.ClientRepository;
import com.brika.platform.document.Document;
import com.brika.platform.document.DocumentRepository;
import com.brika.platform.document.DocumentService;
import com.brika.platform.document.DocumentTypeRepository;
import com.brika.platform.document.ReviewStatus;
import com.brika.platform.identity.CompanyRepository;
import com.brika.platform.identity.CreateUserCommand;
import com.brika.platform.identity.User;
import com.brika.platform.identity.UserProvisioningService;
import com.brika.platform.identity.UserRole;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
 * Sprint 25: document events (upload / review / publish) produce notifications for the right
 * recipients — case assignees for an internal upload, the version uploader for a review decision,
 * and the case clients (Portal) for a publication.
 */
@Testcontainers
@SpringBootTest
class NotificationDocumentEventIT {

  private static final String BUCKET = "brika-documents-notif-test";

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("brika_test")
          .withUsername("brika_test")
          .withPassword("brika_test");

  @Container
  static final MinIOContainer MINIO =
      new MinIOContainer("minio/minio:RELEASE.2024-01-16T16-07-38Z");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("brika.storage.endpoint", MINIO::getS3URL);
    registry.add("brika.storage.access-key", MINIO::getUserName);
    registry.add("brika.storage.secret-key", MINIO::getPassword);
    registry.add("brika.storage.bucket", () -> BUCKET);
    registry.add("brika.storage.presigned-url-ttl-seconds", () -> 300);
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

  @Autowired private CompanyRepository companyRepository;
  @Autowired private UserProvisioningService userProvisioningService;
  @Autowired private ClientRepository clientRepository;
  @Autowired private CaseService caseService;
  @Autowired private DocumentService documentService;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private DocumentTypeRepository documentTypeRepository;
  @Autowired private NotificationRepository notificationRepository;

  private UUID newCompany(String taxId) {
    return companyRepository.insert("Co " + taxId, "Co " + taxId, taxId);
  }

  private User createUser(UserRole role, UUID companyId, String emailPrefix) {
    return userProvisioningService.createUser(
        new CreateUserCommand(
            role, companyId, "ext-" + UUID.randomUUID(), emailPrefix + "@brika.test", "F", "L"));
  }

  private UUID dniTypeId() {
    return documentTypeRepository.findAll().stream()
        .filter(t -> t.code().equals("DNI"))
        .findFirst()
        .orElseThrow()
        .id();
  }

  @Test
  void uploadNotifiesCaseAssigneesExceptTheUploader() {
    UUID companyId = newCompany("TC-ND1");
    User uploader = createUser(UserRole.BROKER, companyId, "broker-nd1a");
    User recipient = createUser(UserRole.BROKER, companyId, "broker-nd1b");
    Case theCase = caseService.createCase(companyId, uploader.id(), "MORTGAGE");
    caseService.assignUser(theCase, uploader.id(), "BROKER");
    caseService.assignUser(theCase, recipient.id(), "BROKER");

    Document document = documentService.createDocument(companyId, theCase.id(), dniTypeId());
    documentService.uploadVersion(
        document,
        "v1".getBytes(StandardCharsets.UTF_8),
        "dni.pdf",
        "application/pdf",
        uploader.id());

    assertThat(notificationRepository.findAllByRecipientUserId(uploader.id())).isEmpty();
    assertThat(notificationRepository.findAllByRecipientUserId(recipient.id()))
        .singleElement()
        .satisfies(n -> assertThat(n.type()).isEqualTo(NotificationType.DOCUMENT_UPLOADED));
  }

  @Test
  void reviewNotifiesTheVersionUploaderNotTheReviewer() {
    UUID companyId = newCompany("TC-ND2");
    User uploader = createUser(UserRole.BROKER, companyId, "broker-nd2a");
    User reviewer = createUser(UserRole.BROKER, companyId, "broker-nd2b");
    Case theCase = caseService.createCase(companyId, uploader.id(), "MORTGAGE");
    caseService.assignUser(theCase, uploader.id(), "BROKER");
    caseService.assignUser(theCase, reviewer.id(), "BROKER");

    Document document = documentService.createDocument(companyId, theCase.id(), dniTypeId());
    documentService.uploadVersion(
        document,
        "v1".getBytes(StandardCharsets.UTF_8),
        "dni.pdf",
        "application/pdf",
        uploader.id());
    Document afterUpload = documentRepository.findById(document.id()).orElseThrow();

    documentService.review(afterUpload, ReviewStatus.APPROVED, reviewer.id(), null);

    assertThat(notificationRepository.findAllByRecipientUserId(uploader.id()))
        .singleElement()
        .satisfies(n -> assertThat(n.type()).isEqualTo(NotificationType.DOCUMENT_REVIEWED));
    assertThat(notificationRepository.findAllByRecipientUserId(reviewer.id()))
        .filteredOn(n -> n.type().equals(NotificationType.DOCUMENT_REVIEWED))
        .isEmpty();
  }

  @Test
  void publishNotifiesTheCaseClientsOnThePortal() {
    UUID companyId = newCompany("TC-ND3");
    User manager = createUser(UserRole.MANAGER, companyId, "mgr-nd3");
    Case theCase = caseService.createCase(companyId, manager.id(), "MORTGAGE");
    UUID clientId =
        clientRepository.insert(companyId, "Cli", "Ent", "cli-nd3@brika.test", "600000000");
    caseService.addClient(theCase, clientId, ParticipationType.HOLDER, true);

    Document document = documentService.createDocument(companyId, theCase.id(), dniTypeId());
    documentService.uploadVersion(
        document,
        "v1".getBytes(StandardCharsets.UTF_8),
        "dni.pdf",
        "application/pdf",
        manager.id());
    Document afterUpload = documentRepository.findById(document.id()).orElseThrow();

    documentService.publish(afterUpload, manager.id());

    assertThat(notificationRepository.findAllByRecipientClientId(clientId))
        .singleElement()
        .satisfies(n -> assertThat(n.type()).isEqualTo(NotificationType.DOCUMENT_PUBLISHED));
  }
}
