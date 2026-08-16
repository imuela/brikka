package com.brika.platform.casemgmt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Pure unit tests for 13_DEFINITIVE_WORKFLOW_SPECIFICATION.md §3 (transition graph). */
class CaseWorkflowTest {

  @Test
  void prestudyAllowsDocumentationAndCancelledOnly() {
    assertThat(CaseWorkflow.canTransition(CaseStatus.PRESTUDY, CaseStatus.DOCUMENTATION)).isTrue();
    assertThat(CaseWorkflow.canTransition(CaseStatus.PRESTUDY, CaseStatus.CANCELLED)).isTrue();
    assertThat(CaseWorkflow.canTransition(CaseStatus.PRESTUDY, CaseStatus.ANALYSIS)).isFalse();
    assertThat(CaseWorkflow.canTransition(CaseStatus.PRESTUDY, CaseStatus.COMPLETED)).isFalse();
  }

  @Test
  void everyNonTerminalStateAllowsCancellation() {
    for (CaseStatus status : CaseStatus.values()) {
      if (!status.isTerminal()) {
        assertThat(CaseWorkflow.canTransition(status, CaseStatus.CANCELLED))
            .as("%s should allow CANCELLED", status)
            .isTrue();
      }
    }
  }

  @ParameterizedTest
  @EnumSource(
      value = CaseStatus.class,
      names = {"COMPLETED", "CANCELLED"})
  void terminalStatesAllowNoNormalTransition(CaseStatus terminal) {
    for (CaseStatus target : CaseStatus.values()) {
      assertThat(CaseWorkflow.canTransition(terminal, target))
          .as("%s -> %s should be false (terminal)", terminal, target)
          .isFalse();
    }
  }

  @Test
  void fullHappyPathIsReachable() {
    CaseStatus[] path = {
      CaseStatus.PRESTUDY,
      CaseStatus.DOCUMENTATION,
      CaseStatus.ANALYSIS,
      CaseStatus.BANK_SEARCH,
      CaseStatus.BANK_SUBMISSION,
      CaseStatus.BANK_REVIEW,
      CaseStatus.OFFER,
      CaseStatus.FORMALIZATION,
      CaseStatus.COMPLETED
    };
    for (int i = 0; i < path.length - 1; i++) {
      assertThat(CaseWorkflow.canTransition(path[i], path[i + 1]))
          .as("%s -> %s", path[i], path[i + 1])
          .isTrue();
    }
  }

  @Test
  void skippingStagesIsRejected() {
    assertThat(CaseWorkflow.canTransition(CaseStatus.PRESTUDY, CaseStatus.BANK_REVIEW)).isFalse();
    assertThat(CaseWorkflow.canTransition(CaseStatus.DOCUMENTATION, CaseStatus.OFFER)).isFalse();
    assertThat(CaseWorkflow.canTransition(CaseStatus.ANALYSIS, CaseStatus.FORMALIZATION)).isFalse();
  }

  @Test
  void backwardTransitionsExplicitlyDocumentedAreAllowed() {
    assertThat(CaseWorkflow.canTransition(CaseStatus.DOCUMENTATION, CaseStatus.PRESTUDY)).isTrue();
    assertThat(CaseWorkflow.canTransition(CaseStatus.ANALYSIS, CaseStatus.DOCUMENTATION)).isTrue();
    assertThat(CaseWorkflow.canTransition(CaseStatus.BANK_REVIEW, CaseStatus.BANK_SEARCH)).isTrue();
    assertThat(CaseWorkflow.canTransition(CaseStatus.FORMALIZATION, CaseStatus.OFFER)).isTrue();
  }

  @Test
  void undocumentedBackwardTransitionsAreRejected() {
    assertThat(CaseWorkflow.canTransition(CaseStatus.OFFER, CaseStatus.PRESTUDY)).isFalse();
    assertThat(CaseWorkflow.canTransition(CaseStatus.BANK_SUBMISSION, CaseStatus.DOCUMENTATION))
        .isFalse();
  }
}
