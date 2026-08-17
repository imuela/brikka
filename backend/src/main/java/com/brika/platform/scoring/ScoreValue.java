package com.brika.platform.scoring;

import java.math.BigDecimal;
import java.util.List;

/**
 * Wraps either a scalar BigDecimal or a List<BigDecimal> (for IN/NOT_IN/BETWEEN). Independent of
 * com.brika.platform.bankmatching.MatchValue — no cross-domain coupling (ADR-SCORING-001).
 */
public final class ScoreValue {

  private final BigDecimal scalar;
  private final List<BigDecimal> array;

  private ScoreValue(BigDecimal scalar, List<BigDecimal> array) {
    this.scalar = scalar;
    this.array = array;
  }

  public static ScoreValue ofScalar(BigDecimal value) {
    return new ScoreValue(value, null);
  }

  public static ScoreValue ofArray(List<BigDecimal> values) {
    return new ScoreValue(null, values);
  }

  public boolean isArray() {
    return array != null;
  }

  public BigDecimal asScalar() {
    return scalar;
  }

  public List<BigDecimal> asArray() {
    return array;
  }
}
