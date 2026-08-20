package com.brika.platform.task.web;

import com.brika.platform.activity.ActivityPublisher;
import com.brika.platform.activity.CaseActivityEvent;
import com.brika.platform.casemgmt.CaseAccessResult;
import com.brika.platform.casemgmt.CaseAccessService;
import com.brika.platform.common.error.ResourceNotFoundException;
import com.brika.platform.common.error.ValidationException;
import com.brika.platform.identity.User;
import com.brika.platform.identity.UserRepository;
import com.brika.platform.identity.UserRole;
import com.brika.platform.security.AuthorizationService;
import com.brika.platform.task.Task;
import com.brika.platform.task.TaskRepository;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 25_CLAUDE_CODE_EXECUTION_GUIDE.md Sprint 8 / 17_API_SPECIFICATION_DETAILED.md §17. A task with
 * caseId set is gated by CaseAccessService (CASE ASSIGNMENT applies, same as every other
 * case-scoped resource); a caseless task uses the tenant-only pattern already established by
 * ClientController (permission + tenant, no case check — task_id has no case to be assigned to).
 */
@RestController
public class TaskController {

  private static final Set<String> UPDATABLE_STATUSES =
      Set.of("TODO", "IN_PROGRESS", "BLOCKED", "CANCELLED");

  private final CaseAccessService caseAccessService;
  private final AuthorizationService authorizationService;
  private final TaskRepository taskRepository;
  private final UserRepository userRepository;
  private final ActivityPublisher activityPublisher;

  public TaskController(
      CaseAccessService caseAccessService,
      AuthorizationService authorizationService,
      TaskRepository taskRepository,
      UserRepository userRepository,
      ActivityPublisher activityPublisher) {
    this.caseAccessService = caseAccessService;
    this.authorizationService = authorizationService;
    this.taskRepository = taskRepository;
    this.userRepository = userRepository;
    this.activityPublisher = activityPublisher;
  }

  @PostMapping("/api/v1/tasks")
  public TaskResponse create(
      Authentication authentication, @RequestBody CreateTaskApiRequest request) {
    if (request.title() == null || request.title().isBlank()) {
      throw new ValidationException("TITLE_REQUIRED", "title is required.");
    }
    if (request.type() == null || request.type().isBlank()) {
      throw new ValidationException("TYPE_REQUIRED", "type is required.");
    }

    UUID tenantId;
    UUID createdBy;
    if (request.caseId() != null) {
      CaseAccessResult access =
          caseAccessService.requireCaseAccess(authentication, "TASK_CREATE", request.caseId());
      tenantId = access.tenantId();
      createdBy = access.user().id();
    } else {
      authorizationService.requirePermission(authentication, "TASK_CREATE");
      tenantId = authorizationService.requireTenant(authentication);
      createdBy = authorizationService.currentUser(authentication).id();
    }

    UUID assignedTo = requireAssignedUserInTenant(request.assignedTo(), tenantId);

    UUID taskId =
        taskRepository.insert(
            tenantId,
            request.caseId(),
            assignedTo,
            request.type(),
            request.title(),
            request.description(),
            request.dueAt(),
            createdBy);

    if (request.caseId() != null) {
      activityPublisher.publish(
          CaseActivityEvent.byUser(
              "task.created",
              tenantId,
              request.caseId(),
              createdBy,
              "Task created: " + request.title()));
    }

    return TaskResponse.from(taskRepository.findById(taskId).orElseThrow());
  }

  @GetMapping("/api/v1/tasks")
  public List<TaskResponse> list(Authentication authentication) {
    authorizationService.requirePermission(authentication, "TASK_READ");
    User user = authorizationService.currentUser(authentication);
    if (authorizationService.isSuperadmin(authentication)) {
      return taskRepository.findAll().stream().map(TaskResponse::from).toList();
    }
    UUID tenantId = authorizationService.requireTenant(authentication);
    boolean restrictToAssignedCases = user.role() == UserRole.BROKER;
    return taskRepository
        .findAllVisibleToUser(tenantId, restrictToAssignedCases, user.id())
        .stream()
        .map(TaskResponse::from)
        .toList();
  }

  @GetMapping("/api/v1/tasks/{id}")
  public TaskResponse get(Authentication authentication, @PathVariable UUID id) {
    return TaskResponse.from(requireAccessibleTask(authentication, "TASK_READ", id).task());
  }

  @PatchMapping("/api/v1/tasks/{id}")
  public TaskResponse update(
      Authentication authentication,
      @PathVariable UUID id,
      @RequestBody UpdateTaskApiRequest request) {
    TaskAccess access = requireAccessibleTask(authentication, "TASK_UPDATE", id);

    if (!Objects.equals(request.assignedTo(), access.task().assignedTo())) {
      authorizationService.requirePermission(authentication, "TASK_ASSIGN");
    }
    if (request.title() == null || request.title().isBlank()) {
      throw new ValidationException("TITLE_REQUIRED", "title is required.");
    }
    if (request.status() == null || !UPDATABLE_STATUSES.contains(request.status())) {
      throw new ValidationException(
          "INVALID_TASK_STATUS",
          "status must be one of TODO, IN_PROGRESS, BLOCKED, CANCELLED (use"
              + " POST /tasks/{id}/complete for DONE).");
    }
    UUID assignedTo = requireAssignedUserInTenant(request.assignedTo(), access.tenantId());

    taskRepository.update(
        id, request.title(), request.description(), request.status(), request.dueAt(), assignedTo);

    return TaskResponse.from(taskRepository.findById(id).orElseThrow());
  }

  @PostMapping("/api/v1/tasks/{id}/complete")
  public TaskResponse complete(Authentication authentication, @PathVariable UUID id) {
    TaskAccess access = requireAccessibleTask(authentication, "TASK_COMPLETE", id);
    if ("CANCELLED".equals(access.task().status())) {
      throw new ValidationException(
          "TASK_ALREADY_CANCELLED", "A cancelled task cannot be completed.");
    }

    taskRepository.complete(id);

    if (access.task().caseId() != null) {
      activityPublisher.publish(
          CaseActivityEvent.byUser(
              "task.completed",
              access.tenantId(),
              access.task().caseId(),
              access.user().id(),
              "Task completed: " + access.task().title()));
    }

    return TaskResponse.from(taskRepository.findById(id).orElseThrow());
  }

  @DeleteMapping("/api/v1/tasks/{id}")
  public void delete(Authentication authentication, @PathVariable UUID id) {
    TaskAccess access = requireAccessibleTask(authentication, "TASK_DELETE", id);
    taskRepository.delete(access.task().id());
  }

  private UUID requireAssignedUserInTenant(UUID assignedTo, UUID tenantId) {
    if (assignedTo == null) {
      return null;
    }
    userRepository
        .findById(assignedTo)
        .filter(u -> tenantId.equals(u.companyId()))
        .orElseThrow(
            () ->
                new ValidationException(
                    "ASSIGNED_USER_NOT_IN_TENANT", "assignedTo must belong to your company."));
    return assignedTo;
  }

  private record TaskAccess(Task task, UUID tenantId, User user) {}

  private TaskAccess requireAccessibleTask(
      Authentication authentication, String permissionCode, UUID taskId) {
    Task task =
        taskRepository
            .findById(taskId)
            .orElseThrow(() -> new ResourceNotFoundException("TASK_NOT_FOUND", "Task not found."));

    if (task.caseId() != null) {
      CaseAccessResult access =
          caseAccessService.requireCaseAccess(authentication, permissionCode, task.caseId());
      if (!task.companyId().equals(access.tenantId())) {
        throw new ResourceNotFoundException("TASK_NOT_FOUND", "Task not found.");
      }
      return new TaskAccess(task, access.tenantId(), access.user());
    }

    authorizationService.requirePermission(authentication, permissionCode);
    UUID tenantId;
    if (authorizationService.isSuperadmin(authentication)) {
      tenantId = task.companyId(); // GLOBAL (ADR-RBAC-002): tenant resolved from the resource.
    } else {
      tenantId = authorizationService.requireTenant(authentication);
    }
    if (!task.companyId().equals(tenantId)) {
      throw new ResourceNotFoundException("TASK_NOT_FOUND", "Task not found.");
    }
    return new TaskAccess(task, tenantId, authorizationService.currentUser(authentication));
  }
}
