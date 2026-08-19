import { ApplicationConfig, LOCALE_ID, provideBrowserGlobalErrorListeners } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeEs from '@angular/common/locales/es';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { MAT_FORM_FIELD_DEFAULT_OPTIONS } from '@angular/material/form-field';

import { routes } from './app.routes';
import { authInterceptor } from './core/http/auth.interceptor';
import { errorInterceptor } from './core/http/error.interceptor';
import { portalAuthInterceptor } from './core/http/portal-auth.interceptor';

registerLocaleData(localeEs);

/** Brika V1 targets Spanish end users exclusively (auditoría UX/i18n pre-Sprint 16) — a single
 * fixed locale, not a multi-locale i18n architecture, since none exists or is required yet. */
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    { provide: LOCALE_ID, useValue: 'es-ES' },
    provideRouter(routes),
    provideAnimationsAsync(),
    provideHttpClient(
      withInterceptors([authInterceptor, portalAuthInterceptor, errorInterceptor]),
    ),
    /* Design system BRIKKA: inputs con borde visible (#D9DEE8) sobre fondo blanco — el
     * appearance "fill" por defecto de Material (fondo gris, sin borde) no encaja con la
     * especificación de marca. Global y centralizado en vez de por-plantilla. */
    { provide: MAT_FORM_FIELD_DEFAULT_OPTIONS, useValue: { appearance: 'outline' } },
  ],
};
