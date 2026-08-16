package com.brika.platform.bankmatching;

import java.math.BigDecimal;
import java.util.List;

/** A rule's "value": either a single number or an array of numbers (IN/NOT_IN/BETWEEN). */
public final class MatchValue {

  private final BigDecimal scalar;
  private final List<BigDecimal> array;

  private MatchValue(BigDecimal scalar, List<BigDecimal> array) {
    this.scalar = scalar;
    this.array = array;
  }

  public static MatchValue ofScalar(BigDecimal scalar) {
    return new MatchValue(scalar, null);
  }

  public static MatchValue ofArray(List<BigDecimal> array) {
    return new MatchValue(null, array);
  }

  public boolean isArray() {
    return array != null;
  }

  public BigDecimal asScalar() {
    if (scalar == null) {
      throw new IllegalStateException("Not a scalar value");
    }
    return scalar;
  }

  public List<BigDecimal> asArray() {
    if (array == null) {
      throw new IllegalStateException("Not an array value");
    }
    return array;
  }
}
