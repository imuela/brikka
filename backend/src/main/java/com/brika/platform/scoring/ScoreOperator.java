package com.brika.platform.scoring;

import java.math.BigDecimal;

/**
 * ADR-SCORING-001 D9-2: closed set of 9 operators, analogous to
 * com.brika.platform.bankmatching.MatchOperator but independently defined — no cross-domain
 * coupling. No operator may be added without a new ADR decision.
 */
public enum ScoreOperator {
  EQUALS {
    @Override
    public boolean apply(BigDecimal fieldValue, ScoreValue value) {
      return fieldValue.compareTo(value.asScalar()) == 0;
    }
  },
  NOT_EQUALS {
    @Override
    public boolean apply(BigDecimal fieldValue, ScoreValue value) {
      return fieldValue.compareTo(value.asScalar()) != 0;
    }
  },
  LESS_THAN {
    @Override
    public boolean apply(BigDecimal fieldValue, ScoreValue value) {
      return fieldValue.compareTo(value.asScalar()) < 0;
    }
  },
  LESS_THAN_OR_EQUAL {
    @Override
    public boolean apply(BigDecimal fieldValue, ScoreValue value) {
      return fieldValue.compareTo(value.asScalar()) <= 0;
    }
  },
  GREATER_THAN {
    @Override
    public boolean apply(BigDecimal fieldValue, ScoreValue value) {
      return fieldValue.compareTo(value.asScalar()) > 0;
    }
  },
  GREATER_THAN_OR_EQUAL {
    @Override
    public boolean apply(BigDecimal fieldValue, ScoreValue value) {
      return fieldValue.compareTo(value.asScalar()) >= 0;
    }
  },
  IN {
    @Override
    public boolean apply(BigDecimal fieldValue, ScoreValue value) {
      return value.asArray().stream().anyMatch(v -> v.compareTo(fieldValue) == 0);
    }

    @Override
    public boolean requiresArray() {
      return true;
    }
  },
  NOT_IN {
    @Override
    public boolean apply(BigDecimal fieldValue, ScoreValue value) {
      return value.asArray().stream().noneMatch(v -> v.compareTo(fieldValue) == 0);
    }

    @Override
    public boolean requiresArray() {
      return true;
    }
  },
  BETWEEN {
    @Override
    public boolean apply(BigDecimal fieldValue, ScoreValue value) {
      BigDecimal min = value.asArray().get(0);
      BigDecimal max = value.asArray().get(1);
      return fieldValue.compareTo(min) >= 0 && fieldValue.compareTo(max) <= 0;
    }

    @Override
    public boolean requiresArray() {
      return true;
    }
  };

  public abstract boolean apply(BigDecimal fieldValue, ScoreValue value);

  public boolean requiresArray() {
    return false;
  }
}
