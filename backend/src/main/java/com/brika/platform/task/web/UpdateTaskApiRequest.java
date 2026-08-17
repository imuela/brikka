package com.brika.platform.task.web;

import java.time.Instant;
import java.util.UUID;

/**
 * Full-replace PATCH, same convention as UpdateClientApiRequest. status here excludes DONE —
 * completing a task always goes through POST /tasks/{id}/complete.
 */
public record UpdateTaskApiRequest(
    String title, String description, String status, Instant dueAt, UUID assignedTo) {}
