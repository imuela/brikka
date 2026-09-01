package com.brika.platform.document;

import java.util.List;

/**
 * BRIKKA V2 I1. A case's document checklist. {@code complete} = every mandatory item is APPROVED;
 * it is the precondition BRIKKA V2 I3 will consume for the DOCUMENTATION -> ANALYSIS transition.
 */
public record CaseChecklist(
    int mandatoryTotal,
    int mandatoryMissing,
    int optionalTotal,
    int optionalMissing,
    boolean complete,
    List<CaseChecklistItem> items) {

  public static CaseChecklist of(List<CaseChecklistItem> items) {
    int mandatoryTotal = 0;
    int mandatoryMissing = 0;
    int optionalTotal = 0;
    int optionalMissing = 0;
    for (CaseChecklistItem item : items) {
      boolean satisfied = item.state() == ChecklistItemState.APPROVED;
      if (item.mandatory()) {
        mandatoryTotal++;
        if (!satisfied) {
          mandatoryMissing++;
        }
      } else {
        optionalTotal++;
        if (!satisfied) {
          optionalMissing++;
        }
      }
    }
    return new CaseChecklist(
        mandatoryTotal,
        mandatoryMissing,
        optionalTotal,
        optionalMissing,
        mandatoryMissing == 0,
        items);
  }
}
