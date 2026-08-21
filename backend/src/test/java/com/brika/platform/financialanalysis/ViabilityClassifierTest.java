package com.brika.platform.financialanalysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ViabilityClassifierTest {

  private final ViabilityClassifier classifier =
      new ViabilityClassifier(new BigDecimal("35"), new BigDecimal("40"));

  @Test
  void computesTheWorkedExampleFromTheSprintPrompt() {
    // income 3000, existing debts 300, new payment 900 -> DTI = 1200/3000*100 = 40%
    BigDecimal dti =
        classifier.computeDti(new BigDecimal("3000"), new BigDecimal("300"), new BigDecimal("900"));
    assertThat(dti).isEqualByComparingTo("40.00");
    assertThat(classifier.classify(dti)).isEqualTo("REVISAR"); // exactly at the REVISAR boundary
  }

  @Test
  void dtiAtOrBelowFavorableThresholdIsFavorable() {
    assertThat(classifier.classify(new BigDecimal("35.00"))).isEqualTo("FAVORABLE");
    assertThat(classifier.classify(new BigDecimal("10.00"))).isEqualTo("FAVORABLE");
  }

  @Test
  void dtiJustAboveFavorableIsRevisar() {
    assertThat(classifier.classify(new BigDecimal("35.01"))).isEqualTo("REVISAR");
  }

  @Test
  void dtiAboveRevisarThresholdIsNoViable() {
    assertThat(classifier.classify(new BigDecimal("40.01"))).isEqualTo("NO_VIABLE");
    assertThat(classifier.classify(new BigDecimal("90.00"))).isEqualTo("NO_VIABLE");
  }

  @Test
  void noExistingDebtsStillComputesCorrectly() {
    BigDecimal dti =
        classifier.computeDti(new BigDecimal("2000"), BigDecimal.ZERO, new BigDecimal("500"));
    assertThat(dti).isEqualByComparingTo("25.00");
  }

  @Test
  void resultIsScaledToTwoDecimalsWithHalfUpRounding() {
    BigDecimal dti =
        classifier.computeDti(new BigDecimal("3000"), new BigDecimal("100"), new BigDecimal("100"));
    // 200/3000*100 = 6.6666... -> 6.67
    assertThat(dti).isEqualByComparingTo("6.67");
  }
}
