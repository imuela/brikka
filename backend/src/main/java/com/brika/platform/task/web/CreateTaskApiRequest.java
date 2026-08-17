package com.brika.platform.task.web;

import java.time.Instant;
import java.util.UUID;

public record CreateTaskApiRequest(
    UUID caseId, UUID assignedTo, String type, String title, String description, Instant dueAt) {}
