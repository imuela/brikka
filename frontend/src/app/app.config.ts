import { ApplicationConfig, LOCALE_ID, provideBrowserGlobalErrorListeners } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeEs from '@angular/common/locales/es';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';

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
  ],
};
