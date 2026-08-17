package com.brika.platform.task.web;

import com.brika.platform.task.Task;
import java.time.Instant;
import java.util.UUID;

public record TaskResponse(
    UUID id,
    UUID caseId,
    UUID assignedTo,
    String type,
    String title,
    String description,
    String status,
    Instant dueAt,
    UUID createdBy,
    Instant completedAt,
    Instant createdAt,
    Instant updatedAt) {

  public static TaskResponse from(Task task) {
    return new TaskResponse(
        task.id(),
        task.caseId(),
        task.assignedTo(),
        task.type(),
        task.title(),
        task.description(),
        task.status(),
        task.dueAt(),
        task.createdBy(),
        task.completedAt(),
        task.createdAt(),
        task.updatedAt());
  }
}
