package com.brika.platform.casemgmt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.brika.platform.bank.BankRepository;
import com.brika.platform.bankrequest.BankOfferRepository;
import com.brika.platform.bankrequest.BankRequestRepository;
import com.brika.platform.bankrequest.FinalFinancingRepository;
import com.brika.platform.common.error.ValidationException;
import com.brika.platform.crm.ClientRepository;
import com.brika.platform.document.DocumentRepository;
import com.brika.platform.document.DocumentService;
import com.brika.platform.document.DocumentTypeRepository;
import com.brika.platform.document.ReviewStatus;
import com.brika.platform.identity.CompanyRepository;
import com.brika.platform.identity.CreateUserCommand;
import com.brika.platform.identity.User;
import com.brika.platform.identity.UserProvisioningService;
import com.brika.platform.identity.UserRole;
import java.math.BigDecimal;
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
 * BRIKKA V2 I3. Business preconditions for the three gated Case transitions
 * (13_DEFINITIVE_WORKFLOW_SPECIFICATION.md §5): DOCUMENTATION -> ANALYSIS, BANK_SEARCH ->
 * BANK_SUBMISSION, OFFER -> FORMALIZATION. Every gate is tenant-scoped and can be forced only via
 * an authorized override (permission + non-blank reason, recorded in case_status_history with the
 * [PRECONDITION_OVERRIDE] marker).
 */
@Testcontainers
@SpringBootTest
class CaseTransitionPreconditionsIT {

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
  static void properties(DynamicPropertyRegistry registry) {
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

  @Autowired private CompanyRepository companyRepository;
  @Autowired private UserProvisioningService userProvisioningService;
  @Autowired private ClientRepository clientRepository;
  @Autowired private CaseService caseService;
  @Autowired private CaseClientRepository caseClientRepository;
  @Autowired private CaseStatusHistoryRepository caseStatusHistoryRepository;
  @Autowired private DocumentService documentService;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private DocumentTypeRepository documentTypeRepository;
  @Autowired private BankRepository bankRepository;
  @Autowired private BankRequestRepository bankRequestRepository;
  @Autowired private BankOfferRepository bankOfferRepository;
  @Autowired private FinalFinancingRepository finalFinancingRepository;

  private UUID newCompany(String taxId) {
    return companyRepository.insert("Co " + taxId, "Co " + taxId, taxId);
  }

  private User newManager(UUID companyId, String prefix) {
    return userProvisioningService.createUser(
        new CreateUserCommand(
            UserRole.MANAGER,
            companyId,
            "ext-" + UUID.randomUUID(),
            prefix + "-" + UUID.randomUUID() + "@brika.test",
            "M",
            "Gr"));
  }

  private UUID typeId(String code) {
    return documentTypeRepository.findByCode(code).orElseThrow().id();
  }

  private Case createCaseWithHolder(UUID companyId, User manager, String operationType) {
    Case theCase = caseService.createCase(companyId, manager.id(), operationType);
    UUID clientId =
        clientRepository.insert(
            companyId, "Cli", "Ent", "cli-" + UUID.randomUUID() + "@brika.test", "600");
    caseClientRepository.insert(theCase.id(), clientId, ParticipationType.HOLDER, true);
    return theCase;
  }

  private Case at(Case theCase, User manager, CaseStatus target) {
    Case c = theCase;
    for (CaseStatus step :
        new CaseStatus[] {
          CaseStatus.DOCUMENTATION,
          CaseStatus.ANALYSIS,
          CaseStatus.BANK_SEARCH,
          CaseStatus.BANK_SUBMISSION,
          CaseStatus.BANK_REVIEW,
          CaseStatus.OFFER
        }) {
      // Force past any gate encountered while positioning the case for the test under focus.
      c = caseService.changeStatus(c, step, manager.id(), "positioning", true);
      if (step == target) {
        return c;
      }
    }
    return c;
  }

  private UUID setupBankOffer(UUID companyId, UUID caseId) {
    UUID bankId = bankRepository.insert("BNK-" + UUID.randomUUID(), "Bank", null);
    UUID bankRequestId = bankRequestRepository.insert(companyId, caseId, bankId, null, "{}");
    return bankOfferRepository.insert(
        companyId,
        bankRequestId,
        bankId,
        new BigDecimal("150000"),
        new BigDecimal("3.10"),
        300,
        new BigDecimal("720"),
        "{}");
  }

  // ---------------------------------------------------------------------------
  // Gate 1 — DOCUMENTATION -> ANALYSIS
  // ---------------------------------------------------------------------------

  @Test
  void gate1_blockedWhileAMandatoryRequirementIsPending() {
    UUID companyId = newCompany("PRE-G1a");
    User manager = newManager(companyId, "g1a");
    Case theCase = createCaseWithHolder(companyId, manager, "PURCHASE");
    Case doc = caseService.changeStatus(theCase, CaseStatus.DOCUMENTATION, manager.id(), null);

    assertThatThrownBy(() -> caseService.changeStatus(doc, CaseStatus.ANALYSIS, manager.id(), null))
        .isInstanceOf(ValidationException.class)
        .hasFieldOrPropertyWithValue("code", "PRECONDITION_CHECKLIST_INCOMPLETE");
  }

  @Test
  void gate1_blockedWhenADocumentIsUploadedButNotApproved() {
    UUID companyId = newCompany("PRE-G1b");
    User manager = newManager(companyId, "g1b");
    Case theCase = createCaseWithHolder(companyId, manager, "PURCHASE");
    Case doc = caseService.changeStatus(theCase, CaseStatus.DOCUMENTATION, manager.id(), null);
    UUID holderId = caseClientRepository.findAllByCaseId(doc.id()).get(0).clientId();

    uploadOnly(companyId, doc.id(), holderId, "DNI", manager);

    assertThatThrownBy(() -> caseService.changeStatus(doc, CaseStatus.ANALYSIS, manager.id(), null))
        .isInstanceOf(ValidationException.class)
        .hasFieldOrPropertyWithValue("code", "PRECONDITION_CHECKLIST_INCOMPLETE");
  }

  @Test
  void gate1_allowedWhenEveryMandatoryDocumentIsApproved() {
    UUID companyId = newCompany("PRE-G1c");
    User manager = newManager(companyId, "g1c");
    Case theCase = createCaseWithHolder(companyId, manager, "PURCHASE");
    Case doc = caseService.changeStatus(theCase, CaseStatus.DOCUMENTATION, manager.id(), null);
    UUID holderId = caseClientRepository.findAllByCaseId(doc.id()).get(0).clientId();

    for (String code : new String[] {"DNI", "PAYSLIP", "EMPLOYMENT_HISTORY"}) {
      uploadAndApprove(companyId, doc.id(), holderId, code, manager);
    }
    for (String code : new String[] {"LAND_REGISTRY_EXTRACT", "DEPOSIT_CONTRACT"}) {
      uploadAndApprove(companyId, doc.id(), null, code, manager);
    }

    Case analysis = caseService.changeStatus(doc, CaseStatus.ANALYSIS, manager.id(), null);
    assertThat(analysis.status()).isEqualTo(CaseStatus.ANALYSIS);
  }

  @Test
  void gate1_authorizedOverrideMovesEvenWithAnIncompleteChecklist() {
    UUID companyId = newCompany("PRE-G1d");
    User manager = newManager(companyId, "g1d");
    Case theCase = createCaseWithHolder(companyId, manager, "PURCHASE");
    Case doc = caseService.changeStatus(theCase, CaseStatus.DOCUMENTATION, manager.id(), null);

    Case analysis =
        caseService.changeStatus(
            doc, CaseStatus.ANALYSIS, manager.id(), "client will bring the payslip later", true);

    assertThat(analysis.status()).isEqualTo(CaseStatus.ANALYSIS);
    assertThat(lastReason(doc.id()))
        .startsWith("[PRECONDITION_OVERRIDE] client will bring the payslip later");
  }

  @Test
  void gate1_operationTypeWithNoRequirementsIsNotBlocked() {
    UUID companyId = newCompany("PRE-G1e");
    User manager = newManager(companyId, "g1e");
    // MORTGAGE has no seeded document_requirements -> the checklist is empty -> vacuously complete.
    Case theCase = createCaseWithHolder(companyId, manager, "MORTGAGE");
    Case doc = caseService.changeStatus(theCase, CaseStatus.DOCUMENTATION, manager.id(), null);

    Case analysis = caseService.changeStatus(doc, CaseStatus.ANALYSIS, manager.id(), null);
    assertThat(analysis.status()).isEqualTo(CaseStatus.ANALYSIS);
  }

  @Test
  void overrideWithoutAReasonIsRejected() {
    UUID companyId = newCompany("PRE-OVR");
    User manager = newManager(companyId, "ovr");
    Case theCase = createCaseWithHolder(companyId, manager, "PURCHASE");
    Case doc = caseService.changeStatus(theCase, CaseStatus.DOCUMENTATION, manager.id(), null);

    assertThatThrownBy(
            () -> caseService.changeStatus(doc, CaseStatus.ANALYSIS, manager.id(), "  ", true))
        .isInstanceOf(ValidationException.class)
        .hasFieldOrPropertyWithValue("code", "PRECONDITION_OVERRIDE_REASON_REQUIRED");
  }

  // ---------------------------------------------------------------------------
  // Gate 2 — BANK_SEARCH -> BANK_SUBMISSION
  // ---------------------------------------------------------------------------

  @Test
  void gate2_blockedWithoutAnyBankRequest() {
    UUID companyId = newCompany("PRE-G2a");
    User manager = newManager(companyId, "g2a");
    Case c =
        at(createCaseWithHolder(companyId, manager, "MORTGAGE"), manager, CaseStatus.BANK_SEARCH);

    assertThatThrownBy(
            () -> caseService.changeStatus(c, CaseStatus.BANK_SUBMISSION, manager.id(), null))
        .isInstanceOf(ValidationException.class)
        .hasFieldOrPropertyWithValue("code", "PRECONDITION_NO_BANK_REQUEST");
  }

  @Test
  void gate2_allowedWithAtLeastOneBankRequest() {
    UUID companyId = newCompany("PRE-G2b");
    User manager = newManager(companyId, "g2b");
    Case c =
        at(createCaseWithHolder(companyId, manager, "MORTGAGE"), manager, CaseStatus.BANK_SEARCH);
    UUID bankId = bankRepository.insert("BNK-" + UUID.randomUUID(), "Bank", null);
    bankRequestRepository.insert(companyId, c.id(), bankId, null, "{}");

    Case moved = caseService.changeStatus(c, CaseStatus.BANK_SUBMISSION, manager.id(), null);
    assertThat(moved.status()).isEqualTo(CaseStatus.BANK_SUBMISSION);
  }

  @Test
  void gate2_bankRequestOfAnotherTenantDoesNotSatisfyTheGate() {
    UUID companyId = newCompany("PRE-G2c");
    User manager = newManager(companyId, "g2c");
    Case c =
        at(createCaseWithHolder(companyId, manager, "MORTGAGE"), manager, CaseStatus.BANK_SEARCH);

    UUID otherCompanyId = newCompany("PRE-G2c-OTHER");
    UUID bankId = bankRepository.insert("BNK-" + UUID.randomUUID(), "Bank", null);
    // A bank_request row that points at this case but belongs to another company.
    bankRequestRepository.insert(otherCompanyId, c.id(), bankId, null, "{}");

    assertThatThrownBy(
            () -> caseService.changeStatus(c, CaseStatus.BANK_SUBMISSION, manager.id(), null))
        .isInstanceOf(ValidationException.class)
        .hasFieldOrPropertyWithValue("code", "PRECONDITION_NO_BANK_REQUEST");
  }

  @Test
  void gate2_authorizedOverrideMovesWithoutABankRequest() {
    UUID companyId = newCompany("PRE-G2d");
    User manager = newManager(companyId, "g2d");
    Case c =
        at(createCaseWithHolder(companyId, manager, "MORTGAGE"), manager, CaseStatus.BANK_SEARCH);

    Case moved =
        caseService.changeStatus(
            c, CaseStatus.BANK_SUBMISSION, manager.id(), "urgent, sending by phone", true);
    assertThat(moved.status()).isEqualTo(CaseStatus.BANK_SUBMISSION);
  }

  // ---------------------------------------------------------------------------
  // Gate 3 — OFFER -> FORMALIZATION
  // ---------------------------------------------------------------------------

  @Test
  void gate3_blockedWithoutASelectedOffer() {
    UUID companyId = newCompany("PRE-G3a");
    User manager = newManager(companyId, "g3a");
    Case c = at(createCaseWithHolder(companyId, manager, "MORTGAGE"), manager, CaseStatus.OFFER);

    assertThatThrownBy(
            () -> caseService.changeStatus(c, CaseStatus.FORMALIZATION, manager.id(), null))
        .isInstanceOf(ValidationException.class)
        .hasFieldOrPropertyWithValue("code", "PRECONDITION_NO_SELECTED_OFFER");
  }

  @Test
  void gate3_allowedWithASelectedOfferOfThisCase() {
    UUID companyId = newCompany("PRE-G3b");
    User manager = newManager(companyId, "g3b");
    Case c = at(createCaseWithHolder(companyId, manager, "MORTGAGE"), manager, CaseStatus.OFFER);
    UUID offerId = setupBankOffer(companyId, c.id());
    finalFinancingRepository.insert(companyId, c.id(), offerId);

    Case moved = caseService.changeStatus(c, CaseStatus.FORMALIZATION, manager.id(), null);
    assertThat(moved.status()).isEqualTo(CaseStatus.FORMALIZATION);
  }

  @Test
  void gate3_selectedOfferOfAnotherTenantDoesNotSatisfyTheGate() {
    UUID companyId = newCompany("PRE-G3c");
    User manager = newManager(companyId, "g3c");
    Case c = at(createCaseWithHolder(companyId, manager, "MORTGAGE"), manager, CaseStatus.OFFER);

    UUID otherCompanyId = newCompany("PRE-G3c-OTHER");
    UUID offerId = setupBankOffer(otherCompanyId, c.id());
    // final_financing pointing at this case but owned by another company.
    finalFinancingRepository.insert(otherCompanyId, c.id(), offerId);

    assertThatThrownBy(
            () -> caseService.changeStatus(c, CaseStatus.FORMALIZATION, manager.id(), null))
        .isInstanceOf(ValidationException.class)
        .hasFieldOrPropertyWithValue("code", "PRECONDITION_NO_SELECTED_OFFER");
  }

  @Test
  void gate3_authorizedOverrideMovesWithoutASelectedOffer() {
    UUID companyId = newCompany("PRE-G3d");
    User manager = newManager(companyId, "g3d");
    Case c = at(createCaseWithHolder(companyId, manager, "MORTGAGE"), manager, CaseStatus.OFFER);

    Case moved =
        caseService.changeStatus(
            c, CaseStatus.FORMALIZATION, manager.id(), "offer accepted verbally", true);
    assertThat(moved.status()).isEqualTo(CaseStatus.FORMALIZATION);
  }

  // ---------------------------------------------------------------------------
  // Non-gated transitions are unaffected
  // ---------------------------------------------------------------------------

  @Test
  void nonGatedTransitionsStillWorkWithNoSetup() {
    UUID companyId = newCompany("PRE-NG");
    User manager = newManager(companyId, "ng");
    Case theCase = createCaseWithHolder(companyId, manager, "MORTGAGE");

    Case c = caseService.changeStatus(theCase, CaseStatus.DOCUMENTATION, manager.id(), null);
    c = caseService.changeStatus(c, CaseStatus.ANALYSIS, manager.id(), null); // gate 1 vacuous
    c = caseService.changeStatus(c, CaseStatus.BANK_SEARCH, manager.id(), null); // no gate
    assertThat(c.status()).isEqualTo(CaseStatus.BANK_SEARCH);
    // backwards edge, no gate
    c = caseService.changeStatus(c, CaseStatus.ANALYSIS, manager.id(), null);
    assertThat(c.status()).isEqualTo(CaseStatus.ANALYSIS);
  }

  // ---------------------------------------------------------------------------

  private void uploadOnly(UUID companyId, UUID caseId, UUID clientId, String code, User by) {
    var document = documentService.createDocument(companyId, caseId, typeId(code), clientId);
    documentService.uploadVersion(
        document, "x".getBytes(StandardCharsets.UTF_8), "f.pdf", "application/pdf", by.id());
  }

  private void uploadAndApprove(UUID companyId, UUID caseId, UUID clientId, String code, User by) {
    var document = documentService.createDocument(companyId, caseId, typeId(code), clientId);
    documentService.uploadVersion(
        document, "x".getBytes(StandardCharsets.UTF_8), "f.pdf", "application/pdf", by.id());
    documentService.review(
        documentRepository.findById(document.id()).orElseThrow(),
        ReviewStatus.APPROVED,
        by.id(),
        null);
  }

  private String lastReason(UUID caseId) {
    return caseStatusHistoryRepository.findLatestReasonByCaseId(caseId).orElseThrow();
  }
}
