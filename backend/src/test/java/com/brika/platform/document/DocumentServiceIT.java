package com.brika.platform.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.brika.platform.casemgmt.Case;
import com.brika.platform.casemgmt.CaseService;
import com.brika.platform.common.error.ValidationException;
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
 * Business-rule tests for DocumentService against a real MinIO instance
 * (18_STORAGE_SPECIFICATION.md).
 */
@Testcontainers
@SpringBootTest
class DocumentServiceIT {

  private static final String BUCKET = "brika-documents-test";
  private static final long MINIO_PRESIGN_TTL_SECONDS = 300;

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("brika_test")
          .withUsername("brika_test")
          .withPassword("brika_test");

  // Sprint 39 audit (D39-1): pinned to the same tag as the brika-minio service in
  // docs/docker-compose.yml so tests validate against the same MinIO version developers and
  // prod actually run, not an unrelated ~20-month-old tag. Every other MinIOContainer-using IT in
  // this module uses this same tag for the same reason.
  @Container
  static final MinIOContainer MINIO =
      new MinIOContainer("minio/minio:RELEASE.2025-09-07T16-13-09Z");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("brika.storage.endpoint", MINIO::getS3URL);
    registry.add("brika.storage.access-key", MINIO::getUserName);
    registry.add("brika.storage.secret-key", MINIO::getPassword);
    registry.add("brika.storage.bucket", () -> BUCKET);
    registry.add("brika.storage.presigned-url-ttl-seconds", () -> MINIO_PRESIGN_TTL_SECONDS);
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
  @Autowired private CaseService caseService;
  @Autowired private DocumentService documentService;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private DocumentTypeRepository documentTypeRepository;

  private UUID newCompany(String taxId) {
    return companyRepository.insert("Co " + taxId, "Co " + taxId, taxId);
  }

  private User newManager(UUID companyId, String emailPrefix) {
    return userProvisioningService.createUser(
        new CreateUserCommand(
            UserRole.MANAGER,
            companyId,
            "ext-" + UUID.randomUUID(),
            emailPrefix + "@brika.test",
            "M",
            "Gr"));
  }

  private UUID dniTypeId() {
    return documentTypeRepository.findAll().stream()
        .filter(t -> t.code().equals("DNI"))
        .findFirst()
        .orElseThrow()
        .id();
  }

  @Test
  void uploadValidatesMimeTypeChecksumAndUpdatesDocumentAndCurrentVersion() {
    UUID companyId = newCompany("TC-DOC1");
    User manager = newManager(companyId, "mgr-doc1");
    Case theCase = caseService.createCase(companyId, manager.id(), "MORTGAGE");
    Document document = documentService.createDocument(companyId, theCase.id(), dniTypeId());

    byte[] content = "hello world".getBytes(StandardCharsets.UTF_8);
    DocumentVersion version =
        documentService.uploadVersion(
            document, content, "dni.pdf", "application/pdf", manager.id());

    assertThat(version.versionNumber()).isEqualTo(1);
    assertThat(version.reviewStatus()).isEqualTo(ReviewStatus.PENDING);
    assertThat(version.checksum()).hasSize(64); // SHA-256 hex

    Document reloaded = documentRepository.findById(document.id()).orElseThrow();
    assertThat(reloaded.currentVersionId()).isEqualTo(version.id());
    assertThat(reloaded.status()).isEqualTo(ReviewStatus.PENDING);
  }

  @Test
  void uploadRejectsUnsupportedMimeType() {
    UUID companyId = newCompany("TC-DOC2");
    User manager = newManager(companyId, "mgr-doc2");
    Case theCase = caseService.createCase(companyId, manager.id(), "MORTGAGE");
    Document document = documentService.createDocument(companyId, theCase.id(), dniTypeId());

    byte[] content = "payload".getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(
            () ->
                documentService.uploadVersion(
                    document, content, "malware.exe", "application/x-msdownload", manager.id()))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void uploadRejectsFilesLargerThanTheConfiguredLimit() {
    UUID companyId = newCompany("TC-DOC3");
    User manager = newManager(companyId, "mgr-doc3");
    Case theCase = caseService.createCase(companyId, manager.id(), "MORTGAGE");
    Document document = documentService.createDocument(companyId, theCase.id(), dniTypeId());

    byte[] tooLarge = new byte[21_000_000];

    assertThatThrownBy(
            () ->
                documentService.uploadVersion(
                    document, tooLarge, "big.pdf", "application/pdf", manager.id()))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void secondUploadCreatesVersionTwoAndBecomesCurrent() {
    UUID companyId = newCompany("TC-DOC4");
    User manager = newManager(companyId, "mgr-doc4");
    Case theCase = caseService.createCase(companyId, manager.id(), "MORTGAGE");
    Document document = documentService.createDocument(companyId, theCase.id(), dniTypeId());

    documentService.uploadVersion(
        document, "v1".getBytes(StandardCharsets.UTF_8), "a.pdf", "application/pdf", manager.id());
    Document afterFirst = documentRepository.findById(document.id()).orElseThrow();
    DocumentVersion second =
        documentService.uploadVersion(
            afterFirst,
            "v2".getBytes(StandardCharsets.UTF_8),
            "b.pdf",
            "application/pdf",
            manager.id());

    assertThat(second.versionNumber()).isEqualTo(2);
    assertThat(documentRepository.findById(document.id()).orElseThrow().currentVersionId())
        .isEqualTo(second.id());
  }

  @Test
  void reviewApprovesTheCurrentVersionAndUpdatesDocumentStatus() {
    UUID companyId = newCompany("TC-DOC5");
    User manager = newManager(companyId, "mgr-doc5");
    Case theCase = caseService.createCase(companyId, manager.id(), "MORTGAGE");
    Document document = documentService.createDocument(companyId, theCase.id(), dniTypeId());
    documentService.uploadVersion(
        document, "v1".getBytes(StandardCharsets.UTF_8), "a.pdf", "application/pdf", manager.id());
    Document afterUpload = documentRepository.findById(document.id()).orElseThrow();

    DocumentVersion reviewed =
        documentService.review(afterUpload, ReviewStatus.APPROVED, manager.id(), null);

    assertThat(reviewed.reviewStatus()).isEqualTo(ReviewStatus.APPROVED);
    assertThat(documentRepository.findById(document.id()).orElseThrow().status())
        .isEqualTo(ReviewStatus.APPROVED);
  }

  @Test
  void reviewRejectsWithCommentActingAsRequestForNewVersion() {
    UUID companyId = newCompany("TC-DOC6");
    User manager = newManager(companyId, "mgr-doc6");
    Case theCase = caseService.createCase(companyId, manager.id(), "MORTGAGE");
    Document document = documentService.createDocument(companyId, theCase.id(), dniTypeId());
    documentService.uploadVersion(
        document, "v1".getBytes(StandardCharsets.UTF_8), "a.pdf", "application/pdf", manager.id());
    Document afterUpload = documentRepository.findById(document.id()).orElseThrow();

    DocumentVersion reviewed =
        documentService.review(
            afterUpload, ReviewStatus.REJECTED, manager.id(), "Please re-upload a clearer scan.");

    assertThat(reviewed.reviewStatus()).isEqualTo(ReviewStatus.REJECTED);
    assertThat(reviewed.reviewComment()).isEqualTo("Please re-upload a clearer scan.");
  }

  @Test
  void publishAndUnpublishToggleTheActivePublication() {
    UUID companyId = newCompany("TC-DOC7");
    User manager = newManager(companyId, "mgr-doc7");
    Case theCase = caseService.createCase(companyId, manager.id(), "MORTGAGE");
    Document document = documentService.createDocument(companyId, theCase.id(), dniTypeId());
    documentService.uploadVersion(
        document, "v1".getBytes(StandardCharsets.UTF_8), "a.pdf", "application/pdf", manager.id());
    Document afterUpload = documentRepository.findById(document.id()).orElseThrow();

    documentService.publish(afterUpload, manager.id());
    documentService.unpublish(afterUpload);
    // No exception on the round trip is the meaningful assertion here; publication state itself
    // is verified via DocumentPublicationRepository in the endpoint-level tests.
    assertThat(documentRepository.findById(document.id())).isPresent();
  }

  @Test
  void presignedDownloadUrlIsSignedShortLivedAndScopedToTheConfiguredBucket() {
    UUID companyId = newCompany("TC-DOC8");
    User manager = newManager(companyId, "mgr-doc8");
    Case theCase = caseService.createCase(companyId, manager.id(), "MORTGAGE");
    Document document = documentService.createDocument(companyId, theCase.id(), dniTypeId());
    DocumentVersion version =
        documentService.uploadVersion(
            document,
            "v1".getBytes(StandardCharsets.UTF_8),
            "a.pdf",
            "application/pdf",
            manager.id());

    URI url = documentService.presignedDownloadUrl(version);
    String urlString = url.toString();

    // A presigned SigV4 URL legitimately carries the access key id in X-Amz-Credential (it is
    // not secret, by design) — what must never appear is the raw storage key exposed without a
    // signature, or the secret key used to compute it. Asserting signature/expiry presence is
    // the meaningful "never a bare unauthenticated URL" property (18_STORAGE_SPECIFICATION.md §4).
    assertThat(urlString).contains(BUCKET);
    assertThat(urlString).contains("X-Amz-Signature=");
    assertThat(urlString).contains("X-Amz-Expires=" + MINIO_PRESIGN_TTL_SECONDS);
  }

  /**
   * BUG-001 (Sprint 32 finding, fixed Sprint 33): the presigned-URL test above only asserts the
   * URL's *shape* (bucket name, signature, expiry present) — a virtual-hosted-style URL contains
   * the bucket name string too, so it would have passed even with the bug. This test performs the
   * real HTTP GET a browser/API client would make, through the real Spring-managed {@code
   * StorageConfig} beans against a real (Testcontainers) MinIO — the only way to actually catch a
   * path-style-vs-virtual-hosted-style mismatch.
   */
  @Test
  void presignedDownloadUrlIsActuallyFetchableAndReturnsTheUploadedBytes() throws Exception {
    UUID companyId = newCompany("TC-DOC9");
    User manager = newManager(companyId, "mgr-doc9");
    Case theCase = caseService.createCase(companyId, manager.id(), "MORTGAGE");
    Document document = documentService.createDocument(companyId, theCase.id(), dniTypeId());
    byte[] content = "real fetch content".getBytes(StandardCharsets.UTF_8);
    DocumentVersion version =
        documentService.uploadVersion(document, content, "a.pdf", "application/pdf", manager.id());

    URI url = documentService.presignedDownloadUrl(version);
    java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
    java.net.http.HttpResponse<byte[]> response =
        client.send(
            java.net.http.HttpRequest.newBuilder(url).GET().build(),
            java.net.http.HttpResponse.BodyHandlers.ofByteArray());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo(content);
  }
}
