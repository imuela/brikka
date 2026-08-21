package com.brika.platform.bank.web;

import com.brika.platform.bank.Bank;
import com.brika.platform.bank.BankCriteriaVersion;
import com.brika.platform.bank.BankCriteriaVersionRepository;
import com.brika.platform.bank.BankProduct;
import com.brika.platform.bank.BankProductRepository;
import com.brika.platform.bank.BankRepository;
import com.brika.platform.bankmatching.CriteriaRulesValidator;
import com.brika.platform.common.error.ResourceNotFoundException;
import com.brika.platform.common.error.ValidationException;
import com.brika.platform.security.AuthorizationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 17_API_SPECIFICATION_DETAILED.md §12 + Sprint 5 pre-flight decision 11.2. Global catalog (BANK is
 * unique in Brika, 06_BANK_ENGINE_SPECIFICATION.md §2): reads use BANK_READ/ BANK_CRITERIA_READ
 * (GLOBAL for every internal role), never requireTenant. Writes use
 * BANK_CREATE/BANK_UPDATE/BANK_CRITERIA_MANAGE (GLOBAL, SUPERADMIN-only); products are managed
 * under BANK_UPDATE since no dedicated BANK_PRODUCT_* permission exists. No catalog data is seeded
 * here or by any migration.
 */
@RestController
public class BankController {

  private final AuthorizationService authorizationService;
  private final BankRepository bankRepository;
  private final BankProductRepository bankProductRepository;
  private final BankCriteriaVersionRepository bankCriteriaVersionRepository;
  private final ObjectMapper objectMapper;
  private final CriteriaRulesValidator criteriaRulesValidator;

  public BankController(
      AuthorizationService authorizationService,
      BankRepository bankRepository,
      BankProductRepository bankProductRepository,
      BankCriteriaVersionRepository bankCriteriaVersionRepository,
      ObjectMapper objectMapper,
      CriteriaRulesValidator criteriaRulesValidator) {
    this.authorizationService = authorizationService;
    this.bankRepository = bankRepository;
    this.bankProductRepository = bankProductRepository;
    this.bankCriteriaVersionRepository = bankCriteriaVersionRepository;
    this.objectMapper = objectMapper;
    this.criteriaRulesValidator = criteriaRulesValidator;
  }

  @GetMapping("/api/v1/banks")
  public List<BankResponse> list(Authentication authentication) {
    authorizationService.requirePermission(authentication, "BANK_READ");
    return bankRepository.findAll().stream().map(this::toResponse).toList();
  }

  @GetMapping("/api/v1/banks/{id}")
  public BankResponse get(Authentication authentication, @PathVariable UUID id) {
    authorizationService.requirePermission(authentication, "BANK_READ");
    return toResponse(requireBank(id));
  }

  @PostMapping("/api/v1/banks")
  public BankResponse create(
      Authentication authentication, @RequestBody CreateBankApiRequest request) {
    authorizationService.requirePermission(authentication, "BANK_CREATE");
    UUID id = bankRepository.insert(request.code(), request.name(), writeJson(request.metadata()));
    return toResponse(requireBank(id));
  }

  @PatchMapping("/api/v1/banks/{id}")
  public BankResponse update(
      Authentication authentication,
      @PathVariable UUID id,
      @RequestBody UpdateBankApiRequest request) {
    authorizationService.requirePermission(authentication, "BANK_UPDATE");
    requireBank(id);
    bankRepository.update(id, request.name(), request.status(), writeJson(request.metadata()));
    return toResponse(requireBank(id));
  }

  @GetMapping("/api/v1/banks/{id}/products")
  public List<BankProductResponse> listProducts(
      Authentication authentication, @PathVariable UUID id) {
    authorizationService.requirePermission(authentication, "BANK_READ");
    requireBank(id);
    return bankProductRepository.findAllByBankId(id).stream().map(this::toResponse).toList();
  }

  @PostMapping("/api/v1/banks/{id}/products")
  public BankProductResponse createProduct(
      Authentication authentication,
      @PathVariable UUID id,
      @RequestBody CreateBankProductApiRequest request) {
    authorizationService.requirePermission(authentication, "BANK_UPDATE");
    requireBank(id);
    UUID productId =
        bankProductRepository.insert(
            id, request.code(), request.name(), writeJson(request.metadata()));
    return toResponse(requireProduct(productId));
  }

  @PatchMapping("/api/v1/bank-products/{id}")
  public BankProductResponse updateProduct(
      Authentication authentication,
      @PathVariable UUID id,
      @RequestBody UpdateBankProductApiRequest request) {
    authorizationService.requirePermission(authentication, "BANK_UPDATE");
    requireProduct(id);
    bankProductRepository.update(
        id, request.name(), request.status(), writeJson(request.metadata()));
    return toResponse(requireProduct(id));
  }

  @GetMapping("/api/v1/banks/{id}/criteria")
  public List<BankCriteriaVersionResponse> listCriteria(
      Authentication authentication, @PathVariable UUID id) {
    authorizationService.requirePermission(authentication, "BANK_CRITERIA_READ");
    requireBank(id);
    return bankCriteriaVersionRepository.findAllByBankId(id).stream()
        .map(this::toResponse)
        .toList();
  }

  @PostMapping("/api/v1/banks/{id}/criteria")
  @Transactional
  public BankCriteriaVersionResponse createCriteria(
      Authentication authentication,
      @PathVariable UUID id,
      @RequestBody CreateBankCriteriaVersionApiRequest request) {
    authorizationService.requirePermission(authentication, "BANK_CRITERIA_MANAGE");
    requireBank(id);
    String rulesJson = writeJson(request.rules());
    criteriaRulesValidator.validate(rulesJson);
    // Sprint 29: a new version is always created ACTIVE (see BankCriteriaVersionRepository#insert)
    // — supersede whatever was ACTIVE before so "activate a version" stays exclusive per bank and
    // BankMatchingService is never left picking among several rows that all say ACTIVE.
    bankCriteriaVersionRepository.supersedeActiveVersions(id);
    UUID criteriaId =
        bankCriteriaVersionRepository.insert(
            id, request.version(), request.effectiveFrom(), request.effectiveTo(), rulesJson);
    return toResponse(requireCriteria(criteriaId));
  }

  @PatchMapping("/api/v1/bank-criteria-versions/{id}")
  public BankCriteriaVersionResponse updateCriteria(
      Authentication authentication,
      @PathVariable UUID id,
      @RequestBody UpdateBankCriteriaVersionApiRequest request) {
    authorizationService.requirePermission(authentication, "BANK_CRITERIA_MANAGE");
    requireCriteria(id);
    String rulesJson = writeJson(request.rules());
    criteriaRulesValidator.validate(rulesJson);
    bankCriteriaVersionRepository.update(id, request.status(), request.effectiveTo(), rulesJson);
    return toResponse(requireCriteria(id));
  }

  private Bank requireBank(UUID id) {
    return bankRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("BANK_NOT_FOUND", "Bank not found."));
  }

  private BankProduct requireProduct(UUID id) {
    return bankProductRepository
        .findById(id)
        .orElseThrow(
            () ->
                new ResourceNotFoundException("BANK_PRODUCT_NOT_FOUND", "Bank product not found."));
  }

  private BankCriteriaVersion requireCriteria(UUID id) {
    return bankCriteriaVersionRepository
        .findById(id)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "BANK_CRITERIA_VERSION_NOT_FOUND", "Bank criteria version not found."));
  }

  private BankResponse toResponse(Bank bank) {
    return new BankResponse(
        bank.id(), bank.code(), bank.name(), bank.status(), readJson(bank.metadata()));
  }

  private BankProductResponse toResponse(BankProduct product) {
    return new BankProductResponse(
        product.id(),
        product.bankId(),
        product.code(),
        product.name(),
        product.status(),
        readJson(product.metadata()));
  }

  private BankCriteriaVersionResponse toResponse(BankCriteriaVersion version) {
    return new BankCriteriaVersionResponse(
        version.id(),
        version.bankId(),
        version.version(),
        version.status(),
        version.effectiveFrom(),
        version.effectiveTo(),
        readJson(version.rules()));
  }

  private String writeJson(Map<String, Object> value) {
    try {
      return objectMapper.writeValueAsString(value == null ? Map.of() : value);
    } catch (JsonProcessingException e) {
      throw new ValidationException("INVALID_JSON", "Value could not be serialized.");
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> readJson(String json) {
    try {
      return objectMapper.readValue(json, Map.class);
    } catch (JsonProcessingException e) {
      return Map.of();
    }
  }
}
