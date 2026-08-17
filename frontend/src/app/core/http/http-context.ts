import { HttpContextToken } from '@angular/common/http';

/**
 * Marks a request as going to a non-Brika-API origin (e.g. Keycloak's token endpoint), so
 * `authInterceptor` never attaches our access token to it and `errorInterceptor` never assumes
 * the Brika `{code, message, requestId}` error shape on its response.
 */
export const SKIP_AUTH = new HttpContextToken<boolean>(() => false);
