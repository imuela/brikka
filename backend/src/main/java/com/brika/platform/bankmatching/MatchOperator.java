package com.brika.platform.bankmatching;

import java.math.BigDecimal;
import java.util.List;

/** ADR-BANKENGINE-001 §2/§3: closed set of 9 operators, all fields numeric (D-B). */
public enum MatchOperator {
  EQUALS {
    @Override
    boolean apply(BigDecimal fieldValue, MatchValue value) {
      return fieldValue.compareTo(value.asScalar()) == 0;
    }
  },
  NOT_EQUALS {
    @Override
    boolean apply(BigDecimal fieldValue, MatchValue value) {
      return fieldValue.compareTo(value.asScalar()) != 0;
    }
  },
  LESS_THAN {
    @Override
    boolean apply(BigDecimal fieldValue, MatchValue value) {
      return fieldValue.compareTo(value.asScalar()) < 0;
    }
  },
  LESS_THAN_OR_EQUAL {
    @Override
    boolean apply(BigDecimal fieldValue, MatchValue value) {
      return fieldValue.compareTo(value.asScalar()) <= 0;
    }
  },
  GREATER_THAN {
    @Override
    boolean apply(BigDecimal fieldValue, MatchValue value) {
      return fieldValue.compareTo(value.asScalar()) > 0;
    }
  },
  GREATER_THAN_OR_EQUAL {
    @Override
    boolean apply(BigDecimal fieldValue, MatchValue value) {
      return fieldValue.compareTo(value.asScalar()) >= 0;
    }
  },
  IN {
    @Override
    boolean apply(BigDecimal fieldValue, MatchValue value) {
      List<BigDecimal> values = value.asArray();
      return values.stream().anyMatch(v -> v.compareTo(fieldValue) == 0);
    }
  },
  NOT_IN {
    @Override
    boolean apply(BigDecimal fieldValue, MatchValue value) {
      List<BigDecimal> values = value.asArray();
      return values.stream().noneMatch(v -> v.compareTo(fieldValue) == 0);
    }
  },
  BETWEEN {
    @Override
    boolean apply(BigDecimal fieldValue, MatchValue value) {
      List<BigDecimal> bounds = value.asArray();
      BigDecimal min = bounds.get(0);
      BigDecimal max = bounds.get(1);
      return fieldValue.compareTo(min) >= 0 && fieldValue.compareTo(max) <= 0;
    }
  };

  abstract boolean apply(BigDecimal fieldValue, MatchValue value);

  public boolean requiresArray() {
    return this == IN || this == NOT_IN || this == BETWEEN;
  }
}
