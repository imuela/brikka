package com.brika.platform.document;

import java.util.UUID;

/**
 * BRIKKA V2 I1. One line of a case's document checklist: a requirement-backed {@link
 * DocumentRequest} plus its derived {@link ChecklistItemState}. {@code clientId} is null for a
 * document of the expediente, or the holder it is requested from. The frontend maps clientId to a
 * name from the case's already-loaded client list (the backend deliberately does not import crm).
 */
public record CaseChecklistItem(
    UUID requirementId,
    UUID documentRequestId,
    UUID documentTypeId,
    String documentTypeCode,
    String documentTypeName,
    boolean mandatory,
    UUID clientId,
    ChecklistItemState state) {}
