import { HttpContextToken } from '@angular/common/http';

/**
 * Marks a request that should not carry the Bearer access token or be parsed with the Brika
 * `{code, message, requestId}` error shape — used for the backend's own unauthenticated auth
 * endpoints (login, refresh, password-reset), which by definition can't attach a token they don't
 * have yet.
 */
export const SKIP_AUTH = new HttpContextToken<boolean>(() => false);
