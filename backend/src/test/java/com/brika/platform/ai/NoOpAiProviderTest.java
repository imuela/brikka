package com.brika.platform.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * D10-2: NoOpAiProvider must never claim success — every method reports executed=false with a
 * non-null reason and a null output, for any input including null/blank context.
 */
class NoOpAiProviderTest {

  private final NoOpAiProvider provider = new NoOpAiProvider();

  @Test
  void summarizeNeverClaimsSuccess() {
    AiProviderResult result = provider.summarize("some case notes");
    assertThat(result.executed()).isFalse();
    assertThat(result.output()).isNull();
    assertThat(result.reason()).isNotBlank();
  }

  @Test
  void explainNeverClaimsSuccess() {
    AiProviderResult result = provider.explain("scoring explanation context");
    assertThat(result.executed()).isFalse();
    assertThat(result.output()).isNull();
    assertThat(result.reason()).isNotBlank();
  }

  @Test
  void draftMessageNeverClaimsSuccess() {
    AiProviderResult result = provider.draftMessage("draft context");
    assertThat(result.executed()).isFalse();
    assertThat(result.output()).isNull();
    assertThat(result.reason()).isNotBlank();
  }

  @Test
  void nullContextNeverProducesFabricatedOutput() {
    assertThat(provider.summarize(null).executed()).isFalse();
    assertThat(provider.explain(null).executed()).isFalse();
    assertThat(provider.draftMessage(null).executed()).isFalse();
  }
}
