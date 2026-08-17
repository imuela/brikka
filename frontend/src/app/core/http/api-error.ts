/** Mirrors backend ErrorResponse (05_API_SPECIFICATION.md §5) — only present for non-401 errors;
 * see error.interceptor.ts for why 401 never carries this shape. */
export interface ApiError {
  status: number;
  code: string | null;
  message: string;
  requestId: string | null;
}
