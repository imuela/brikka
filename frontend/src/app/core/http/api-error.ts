/** Mirrors backend ErrorResponse (05_API_SPECIFICATION.md §5) — only present for non-401 errors;
 * see error.interceptor.ts for why 401 never carries this shape. */
export interface ApiError {
  status: number;
  code: string | null;
  message: string;
  requestId: string | null;
}

/**
 * Parses a raw HttpErrorResponse body into ApiError. Used both by error.interceptor.ts (for
 * requests that go through it) and directly by AuthService/PortalAuthService for the pre-session
 * endpoints (login, password-reset) that are marked SKIP_AUTH — those must never trigger
 * errorInterceptor's 401 "session expired, redirect" handling (there is no session yet), but their
 * error bodies still have this exact shape, since GlobalExceptionHandler produces it for these
 * failures too (unlike a genuinely expired bearer token, which Spring Security's own
 * AuthenticationEntryPoint answers before GlobalExceptionHandler ever runs).
 */
export function toApiError(error: {
  status: number;
  error: unknown;
  message: string;
}): ApiError {
  const body = error.error as { code?: string; message?: string; requestId?: string } | null;
  return {
    status: error.status,
    code: body?.code ?? null,
    message: body?.message ?? error.message,
    requestId: body?.requestId ?? null,
  };
}
