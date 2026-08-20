package com.brika.platform.casemgmt.web;

import com.brika.platform.audit.AuditEventWriter;
import com.brika.platform.casemgmt.CancellationReason;
import com.brika.platform.casemgmt.Case;
import com.brika.platform.casemgmt.CaseAccessResult;
import com.brika.platform.casemgmt.CaseAccessService;
import com.brika.platform.casemgmt.CaseAssignment;
import com.brika.platform.casemgmt.CaseClient;
import com.brika.platform.casemgmt.CaseRepository;
import com.brika.platform.casemgmt.CaseService;
import com.brika.platform.casemgmt.CaseStatus;
import com.brika.platform.casemgmt.ParticipationType;
import com.brika.platform.common.error.ValidationException;
import com.brika.platform.crm.Client;
import com.brika.platform.crm.ClientRepository;
import com.brika.platform.identity.User;
import com.brika.platform.identity.UserRole;
import com.brika.platform.security.AuthorizationService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 17_API_SPECIFICATION_DETAILED.md §7 + Sprint 3 pre-flight resolution for case_clients (Decision:
 * Opción 1 — POST/GET/DELETE /cases/{id}/clients). CASE_READ/UPDATE/CHANGE_STATUS/CANCEL for BROKER
 * require an active case_assignment (CaseAccessService); CASE_CREATE/ASSIGN/REOPEN do not apply a
 * per-case check (CREATE has no case yet; ASSIGN/REOPEN are MANAGER-only in practice).
 */
@RestController
@RequestMapping("/api/v1/cases")
public class CaseController {

  private final AuthorizationService authorizationService;
  private final CaseAccessService caseAccessService;
  private final CaseService caseService;
  private final CaseRepository caseRepository;
  private final ClientRepository clientRepository;
  private final AuditEventWriter auditEventWriter;

  public CaseController(
      AuthorizationService authorizationService,
      CaseAccessService caseAccessService,
      CaseService caseService,
      CaseRepository caseRepository,
      ClientRepository clientRepository,
      AuditEventWriter auditEventWriter) {
    this.authorizationService = authorizationService;
    this.caseAccessService = caseAccessService;
    this.caseService = caseService;
    this.caseRepository = caseRepository;
    this.clientRepository = clientRepository;
    this.auditEventWriter = auditEventWriter;
  }

  @GetMapping
  public List<CaseResponse> list(Authentication authentication) {
    authorizationService.requirePermission(authentication, "CASE_READ");
    User user = authorizationService.currentUser(authentication);
    List<Case> cases;
    if (authorizationService.isSuperadmin(authentication)) {
      cases = caseRepository.findAll();
    } else {
      UUID tenantId = authorizationService.requireTenant(authentication);
      cases =
          user.role() == UserRole.BROKER
              ? caseRepository.findAllAssignedToUser(tenantId, user.id())
              : caseRepository.findAllByCompanyId(tenantId);
    }
    return cases.stream().map(CaseResponse::from).toList();
  }

  @PostMapping
  public CaseResponse create(
      Authentication authentication, @RequestBody CreateCaseApiRequest request) {
    authorizationService.requirePermission(authentication, "CASE_CREATE");
    User user = authorizationService.currentUser(authentication);
    UUID tenantId = authorizationService.requireTenant(authentication);
    return CaseResponse.from(
        caseService.createCase(
            tenantId,
            user.id(),
            request.operationType(),
            request.requestedAmount(),
            request.description()));
  }

  @GetMapping("/{id}")
  public CaseResponse get(Authentication authentication, @PathVariable UUID id) {
    CaseAccessResult access = caseAccessService.requireCaseAccess(authentication, "CASE_READ", id);
    return CaseResponse.from(access.theCase());
  }

  @PatchMapping("/{id}")
  public CaseResponse update(
      Authentication authentication,
      @PathVariable UUID id,
      @RequestBody UpdateCaseApiRequest request) {
    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, "CASE_UPDATE", id);
    Case updated =
        caseService.updateDetails(
            access.theCase(),
            request.operationType(),
            request.requestedAmount(),
            request.description());
    auditEventWriter.write(
        access.tenantId(),
        access.user().id(),
        null,
        "CASE_UPDATED",
        "CASE",
        id,
        "{\"caseId\":\"" + id + "\"}");
    return CaseResponse.from(updated);
  }

  @PostMapping("/{id}/status")
  public CaseResponse changeStatus(
      Authentication authentication,
      @PathVariable UUID id,
      @RequestBody ChangeCaseStatusApiRequest request) {
    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, "CASE_CHANGE_STATUS", id);
    CaseStatus oldStatus = access.theCase().status();
    CaseStatus newStatus = parseCaseStatus(request.newStatus());
    Case updated =
        caseService.changeStatus(access.theCase(), newStatus, access.user().id(), request.reason());
    auditEventWriter.write(
        access.tenantId(),
        access.user().id(),
        null,
        "CASE_STATUS_CHANGED",
        "CASE",
        id,
        "{\"caseId\":\""
            + id
            + "\",\"oldStatus\":\""
            + oldStatus
            + "\",\"newStatus\":\""
            + newStatus
            + "\"}");
    return CaseResponse.from(updated);
  }

  @PostMapping("/{id}/cancel")
  public CaseResponse cancel(
      Authentication authentication,
      @PathVariable UUID id,
      @RequestBody CancelCaseApiRequest request) {
    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, "CASE_CANCEL", id);
    CancellationReason reason = parseCancellationReason(request.reason());
    Case updated =
        caseService.cancel(access.theCase(), access.user().id(), reason, request.comment());
    auditEventWriter.write(
        access.tenantId(),
        access.user().id(),
        null,
        "CASE_CANCELLED",
        "CASE",
        id,
        "{\"caseId\":\"" + id + "\",\"reason\":\"" + reason + "\"}");
    return CaseResponse.from(updated);
  }

  @PostMapping("/{id}/reopen")
  public CaseResponse reopen(
      Authentication authentication,
      @PathVariable UUID id,
      @RequestBody ReopenCaseApiRequest request) {
    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, "CASE_REOPEN", id);
    CaseStatus targetStatus = parseCaseStatus(request.targetStatus());
    Case updated =
        caseService.reopen(access.theCase(), access.user().id(), request.reason(), targetStatus);
    auditEventWriter.write(
        access.tenantId(),
        access.user().id(),
        null,
        "CASE_REOPENED",
        "CASE",
        id,
        "{\"caseId\":\"" + id + "\",\"targetStatus\":\"" + targetStatus + "\"}");
    return CaseResponse.from(updated);
  }

  @PostMapping("/{id}/assignments")
  public CaseAssignmentResponse assign(
      Authentication authentication,
      @PathVariable UUID id,
      @RequestBody CreateCaseAssignmentApiRequest request) {
    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, "CASE_ASSIGN", id);
    CaseAssignment assignment =
        caseService.assignUser(access.theCase(), request.userId(), request.assignmentType());
    return CaseAssignmentResponse.from(assignment);
  }

  @GetMapping("/{id}/assignments")
  public List<CaseAssignmentResponse> listAssignments(
      Authentication authentication, @PathVariable UUID id) {
    CaseAccessResult access = caseAccessService.requireCaseAccess(authentication, "CASE_READ", id);
    return caseService.listAssignments(access.theCase()).stream()
        .map(CaseAssignmentResponse::from)
        .toList();
  }

  @PostMapping("/{id}/clients")
  public void addClient(
      Authentication authentication,
      @PathVariable UUID id,
      @RequestBody CaseClientApiRequest request) {
    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, "CASE_UPDATE", id);
    ParticipationType participationType = parseParticipationType(request.participationType());
    caseService.addClient(
        access.theCase(), request.clientId(), participationType, request.isPrimary());
  }

  @GetMapping("/{id}/clients")
  public List<CaseClientResponse> listClients(
      Authentication authentication, @PathVariable UUID id) {
    CaseAccessResult access = caseAccessService.requireCaseAccess(authentication, "CASE_READ", id);
    List<CaseClient> caseClients = caseService.listClients(access.theCase());
    Map<UUID, Client> clientsById =
        caseClients.stream()
            .map(cc -> clientRepository.findById(cc.clientId()))
            .flatMap(Optional::stream)
            .collect(Collectors.toMap(Client::id, c -> c));
    return caseClients.stream()
        .map(
            cc -> {
              Client client = clientsById.get(cc.clientId());
              return new CaseClientResponse(
                  cc.clientId(),
                  client == null ? null : client.firstName(),
                  client == null ? null : client.lastName(),
                  cc.participationType().name(),
                  cc.isPrimary());
            })
        .toList();
  }

  @DeleteMapping("/{id}/clients/{clientId}")
  public void removeClient(
      Authentication authentication, @PathVariable UUID id, @PathVariable UUID clientId) {
    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, "CASE_UPDATE", id);
    caseService.removeClient(access.theCase(), clientId);
  }

  private CaseStatus parseCaseStatus(String value) {
    try {
      return CaseStatus.valueOf(value);
    } catch (IllegalArgumentException e) {
      throw new ValidationException("INVALID_STATUS", "Unknown case status: " + value);
    }
  }

  private CancellationReason parseCancellationReason(String value) {
    try {
      return CancellationReason.valueOf(value);
    } catch (IllegalArgumentException e) {
      throw new ValidationException(
          "INVALID_CANCELLATION_REASON", "Unknown cancellation reason: " + value);
    }
  }

  private ParticipationType parseParticipationType(String value) {
    try {
      return ParticipationType.valueOf(value);
    } catch (IllegalArgumentException e) {
      throw new ValidationException(
          "INVALID_PARTICIPATION_TYPE", "Unknown participation type: " + value);
    }
  }
}
