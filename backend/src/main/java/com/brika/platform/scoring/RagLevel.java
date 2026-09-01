package com.brika.platform.scoring;

/**
 * BRIKKA V2 I2. Qualitative traffic-light level of a single RAG axis or of the combined case
 * indicator. {@code NOT_EVALUATED} means "this signal is not available yet" and is deliberately
 * distinct from a bad score — it never worsens the combined indicator (see {@link CaseRagService}).
 */
public enum RagLevel {
  GREEN(1),
  AMBER(2),
  RED(3),
  NOT_EVALUATED(0);

  private final int severity;

  RagLevel(int severity) {
    this.severity = severity;
  }

  /** Higher = worse. Used only to pick the worst axis; {@code NOT_EVALUATED} sorts lowest. */
  int severity() {
    return severity;
  }
}
