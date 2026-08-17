package com.brika.platform.ai.web;

import com.brika.platform.ai.AiProviderResult;

public record AiUseCaseResponse(boolean executed, String output, String reason) {

  public static AiUseCaseResponse from(AiProviderResult result) {
    return new AiUseCaseResponse(result.executed(), result.output(), result.reason());
  }
}
