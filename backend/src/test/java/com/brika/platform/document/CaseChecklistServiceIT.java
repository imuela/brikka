package com.brika.platform.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.brika.platform.casemgmt.Case;
import com.brika.platform.casemgmt.CaseClientRepository;
import com.brika.platform.casemgmt.CaseService;
import com.brika.platform.casemgmt.CaseStatus;
import com.brika.platform.casemgmt.ParticipationType;
import com.brika.platform.crm.ClientRepository;
import com.brika.platform.identity.CompanyRepository;
import com.brika.platform.identity.CreateUserCommand;
import com.brika.platform.identity.User;
import com.brika.platform.identity.UserProvisioningService;
import com.brika.platform.identity.UserRole;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
 * BRIKKA V2 I1. The document checklist: auto-generation on entering DOCUMENTATION (idempotent,
 * per-holder vs per-case), and completion driven strictly by document review/approval — never by
 * mere upload (product-owner decision §10.3).
 */
@Testcontainers
@SpringBootTest
class CaseChecklistServiceIT {

  private static final String BUCKET = "brika-documents-test";
  private static final long MINIO_PRESIGN_TTL_SECONDS = 300;

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
  @Autowired private ClientRepository clientRepository;
  @Autowired private CaseService caseService;
  @Autowired private CaseClientRepository caseClientRepository;
  @Autowired private DocumentService documentService;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private DocumentTypeRepository documentTypeRepository;
  @Autowired private DocumentRequestRepository documentRequestRepository;
  @Autowired private CaseChecklistService caseChecklistService;

  private UUID newCompany(String taxId) {
    return companyRepository.insert("Co " + taxId, "Co " + taxId, taxId);
  }

  private User newManager(UUID companyId, String emailPrefix) {
    return userProvisioningService.createUser(
        new CreateUserCommand(
            UserRole.MANAGER,
            companyId,
            "ext-" + UUID.randomUUID(),
            emailPrefix + "-" + UUID.randomUUID() + "@brika.test",
            "M",
            "Gr"));
  }

  private UUID addHolder(UUID companyId, UUID caseId, String prefix) {
    UUID clientId =
        clientRepository.insert(
            companyId, prefix, "Holder", prefix + "-" + UUID.randomUUID() + "@brika.test", "600");
    caseClientRepository.insert(caseId, clientId, ParticipationType.HOLDER, false);
    return clientId;
  }

  private UUID typeId(String code) {
    return documentTypeRepository.findByCode(code).orElseThrow().id();
  }

  private Case purchaseCaseInDocumentation(UUID companyId, User manager, int holders) {
    Case theCase = caseService.createCase(companyId, manager.id(), "PURCHASE");
    for (int i = 0; i < holders; i++) {
      addHolder(companyId, theCase.id(), "h" + i);
    }
    return caseService.changeStatus(theCase, CaseStatus.DOCUMENTATION, manager.id(), null);
  }

  private void uploadAndReview(
      UUID companyId, UUID caseId, UUID clientId, String typeCode, ReviewStatus decision, User by) {
    var document = documentService.createDocument(companyId, caseId, typeId(typeCode), clientId);
    documentService.uploadVersion(
        document, "content".getBytes(StandardCharsets.UTF_8), "f.pdf", "application/pdf", by.id());
    documentService.review(
        documentRepository.findById(document.id()).orElseThrow(), decision, by.id(), null);
  }

  @Test
  void enteringDocumentationGeneratesRequirementBackedRequestsPerHolderAndPerCaseAndIsIdempotent() {
    UUID companyId = newCompany("CHK-1");
    User manager = newManager(companyId, "mgr1");
    Case theCase = purchaseCaseInDocumentation(companyId, manager, 2);

    List<DocumentRequest> requests = documentRequestRepository.findAllByCaseId(theCase.id());
    // 6 per-holder requirements x 2 holders + 3 per-case = 15, every one requirement-backed.
    assertThat(requests).hasSize(15);
    assertThat(requests).allMatch(r -> r.requirementId() != null);
    assertThat(requests.stream().filter(r -> r.requestedFromClientId() == null)).hasSize(3);

    // Backwards ANALYSIS -> DOCUMENTATION must not duplicate anything.
    Case analysis = caseService.changeStatus(theCase, CaseStatus.ANALYSIS, manager.id(), null);
    caseService.changeStatus(analysis, CaseStatus.DOCUMENTATION, manager.id(), null);
    assertThat(documentRequestRepository.findAllByCaseId(theCase.id())).hasSize(15);
  }

  @Test
  void checklistStartsAllMissingAndIncomplete() {
    UUID companyId = newCompany("CHK-2");
    User manager = newManager(companyId, "mgr2");
    Case theCase = purchaseCaseInDocumentation(companyId, manager, 2);
    UUID h0 = caseClientRepository.findAllByCaseId(theCase.id()).get(0).clientId();

    CaseChecklist checklist =
        caseChecklistService.checklist(theCase.id(), "PURCHASE", holderIds(theCase.id()));

    assertThat(checklist.items()).hasSize(15);
    assertThat(checklist.items()).allMatch(i -> i.state() == ChecklistItemState.MISSING);
    assertThat(checklist.mandatoryTotal())
        .isEqualTo(8); // (DNI, PAYSLIP, EMPLOYMENT_HISTORY) x2 + 2
    assertThat(checklist.mandatoryMissing()).isEqualTo(8);
    assertThat(checklist.optionalTotal()).isEqualTo(7);
    assertThat(checklist.complete()).isFalse();
    // sanity: the per-holder DNI item is addressed to a real holder.
    assertThat(checklist.items())
        .anyMatch(i -> "DNI".equals(i.documentTypeCode()) && h0.equals(i.clientId()));
  }

  @Test
  void uploadingADocumentMakesTheItemSubmittedButNeverComplete() {
    UUID companyId = newCompany("CHK-3");
    User manager = newManager(companyId, "mgr3");
    Case theCase = purchaseCaseInDocumentation(companyId, manager, 1);
    UUID h0 = holderIds(theCase.id()).get(0);

    var document = documentService.createDocument(companyId, theCase.id(), typeId("DNI"), h0);
    documentService.uploadVersion(
        document, "x".getBytes(StandardCharsets.UTF_8), "dni.pdf", "application/pdf", manager.id());

    CaseChecklist checklist =
        caseChecklistService.checklist(theCase.id(), "PURCHASE", holderIds(theCase.id()));
    CaseChecklistItem dniItem = itemFor(checklist, "DNI", h0);
    assertThat(dniItem.state()).isEqualTo(ChecklistItemState.SUBMITTED);
    assertThat(checklist.complete()).isFalse();
    // The backing request must NOT be fulfilled by a mere upload.
    assertThat(requestStatus(dniItem)).isEqualTo(DocumentRequestStatus.PENDING);
  }

  @Test
  void approvingDocumentsFulfilsRequirementsAndCompletesTheChecklist() {
    UUID companyId = newCompany("CHK-4");
    User manager = newManager(companyId, "mgr4");
    Case theCase = purchaseCaseInDocumentation(companyId, manager, 1);
    UUID h0 = holderIds(theCase.id()).get(0);

    for (String code : new String[] {"DNI", "PAYSLIP", "EMPLOYMENT_HISTORY"}) {
      uploadAndReview(companyId, theCase.id(), h0, code, ReviewStatus.APPROVED, manager);
    }
    for (String code : new String[] {"LAND_REGISTRY_EXTRACT", "DEPOSIT_CONTRACT"}) {
      uploadAndReview(companyId, theCase.id(), null, code, ReviewStatus.APPROVED, manager);
    }

    CaseChecklist checklist =
        caseChecklistService.checklist(theCase.id(), "PURCHASE", holderIds(theCase.id()));
    assertThat(checklist.mandatoryMissing()).isZero();
    assertThat(checklist.complete()).isTrue();
    assertThat(requestStatus(itemFor(checklist, "DNI", h0)))
        .isEqualTo(DocumentRequestStatus.FULFILLED);
  }

  @Test
  void rejectingAPreviouslyApprovedVersionReopensTheRequirement() {
    UUID companyId = newCompany("CHK-5");
    User manager = newManager(companyId, "mgr5");
    Case theCase = purchaseCaseInDocumentation(companyId, manager, 1);
    UUID h0 = holderIds(theCase.id()).get(0);

    var document = documentService.createDocument(companyId, theCase.id(), typeId("DNI"), h0);
    documentService.uploadVersion(
        document,
        "v1".getBytes(StandardCharsets.UTF_8),
        "dni.pdf",
        "application/pdf",
        manager.id());
    documentService.review(
        documentRepository.findById(document.id()).orElseThrow(),
        ReviewStatus.APPROVED,
        manager.id(),
        null);
    assertThat(requestStatus(itemFor(currentChecklist(theCase), "DNI", h0)))
        .isEqualTo(DocumentRequestStatus.FULFILLED);

    documentService.uploadVersion(
        document,
        "v2".getBytes(StandardCharsets.UTF_8),
        "dni.pdf",
        "application/pdf",
        manager.id());
    documentService.review(
        documentRepository.findById(document.id()).orElseThrow(),
        ReviewStatus.REJECTED,
        manager.id(),
        "blurry");

    CaseChecklistItem dniItem = itemFor(currentChecklist(theCase), "DNI", h0);
    assertThat(dniItem.state()).isEqualTo(ChecklistItemState.REJECTED);
    assertThat(requestStatus(dniItem)).isEqualTo(DocumentRequestStatus.PENDING);
  }

  @Test
  void checklistIsTenantScoped() {
    UUID companyA = newCompany("CHK-6A");
    User managerA = newManager(companyA, "mgr6a");
    Case caseA = purchaseCaseInDocumentation(companyA, managerA, 1);

    UUID companyB = newCompany("CHK-6B");
    User managerB = newManager(companyB, "mgr6b");
    Case caseB = purchaseCaseInDocumentation(companyB, managerB, 1);
    uploadAndReview(
        companyB, caseB.id(), holderIds(caseB.id()).get(0), "DNI", ReviewStatus.APPROVED, managerB);

    CaseChecklist checklistA =
        caseChecklistService.checklist(caseA.id(), "PURCHASE", holderIds(caseA.id()));
    assertThat(checklistA.items()).allMatch(i -> i.state() == ChecklistItemState.MISSING);
    assertThat(checklistA.complete()).isFalse();
  }

  private CaseChecklist currentChecklist(Case theCase) {
    return caseChecklistService.checklist(theCase.id(), "PURCHASE", holderIds(theCase.id()));
  }

  private List<UUID> holderIds(UUID caseId) {
    return caseClientRepository.findAllByCaseId(caseId).stream().map(cc -> cc.clientId()).toList();
  }

  private static CaseChecklistItem itemFor(
      CaseChecklist checklist, String typeCode, UUID clientId) {
    return checklist.items().stream()
        .filter(
            i ->
                typeCode.equals(i.documentTypeCode())
                    && java.util.Objects.equals(clientId, i.clientId()))
        .findFirst()
        .orElseThrow();
  }

  private DocumentRequestStatus requestStatus(CaseChecklistItem item) {
    return documentRequestRepository.findById(item.documentRequestId()).orElseThrow().status();
  }
}
