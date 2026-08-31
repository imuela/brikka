package com.brika.platform.document.web;

import com.brika.platform.document.CaseChecklist;
import com.brika.platform.document.CaseChecklistItem;
import java.util.List;
import java.util.UUID;

/** BRIKKA V2 I1. GET /api/v1/cases/{caseId}/checklist. */
public record CaseChecklistResponse(
    int mandatoryTotal,
    int mandatoryMissing,
    int optionalTotal,
    int optionalMissing,
    boolean complete,
    List<Item> items) {

  public record Item(
      UUID requirementId,
      UUID documentRequestId,
      UUID documentTypeId,
      String documentTypeCode,
      String documentTypeName,
      boolean mandatory,
      UUID clientId,
      String state) {

    static Item from(CaseChecklistItem item) {
      return new Item(
          item.requirementId(),
          item.documentRequestId(),
          item.documentTypeId(),
          item.documentTypeCode(),
          item.documentTypeName(),
          item.mandatory(),
          item.clientId(),
          item.state().name());
    }
  }

  public static CaseChecklistResponse from(CaseChecklist checklist) {
    return new CaseChecklistResponse(
        checklist.mandatoryTotal(),
        checklist.mandatoryMissing(),
        checklist.optionalTotal(),
        checklist.optionalMissing(),
        checklist.complete(),
        checklist.items().stream().map(Item::from).toList());
  }
}
