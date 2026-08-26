package com.brika.platform.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;

/**
 * Sprint 40: unit-level regression guard for {@link GlobalExceptionHandler}. The real HTTP contract
 * (missing/malformed body on a live endpoint) is covered by {@code UserAuthEndpointsIT} against a
 * real Testcontainers-backed server — these two tests just pin the handler's own return values
 * directly, including confirming the generic catch-all ({@link
 * GlobalExceptionHandler#handleUnexpected}) still answers an unrelated exception exactly as before
 * this sprint's change.
 */
class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void missingOrMalformedBodyReturnsBadRequestWithStandardErrorBody() {
    var response =
        handler.handleMessageNotReadable(
            new HttpMessageNotReadableException("Required request body is missing"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo("INVALID_REQUEST");
    assertThat(response.getBody().message()).isNotBlank();
  }

  @Test
  void unrelatedExceptionStillFallsThroughToTheGenericCatchAllUnchanged() {
    var response = handler.handleUnexpected(new RuntimeException("boom"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
  }
}
