import { Directive, EffectRef, TemplateRef, ViewContainerRef, effect, inject, input } from '@angular/core';

import { SessionStore } from '../../core/session/session.store';

/**
 * `*appHasPermission="'CASE_READ'"` — shows the element only if the current session has that
 * permission. UX-only (03_TECHNICAL_SPECIFICATION.md §3): hides navigation/actions the backend
 * would reject anyway, never a substitute for the backend's own authorization check.
 */
@Directive({
  selector: '[appHasPermission]',
  standalone: true,
})
export class HasPermissionDirective {
  private readonly templateRef = inject(TemplateRef<unknown>);
  private readonly viewContainerRef = inject(ViewContainerRef);
  private readonly sessionStore = inject(SessionStore);

  readonly appHasPermission = input.required<string>();

  private hasView = false;
  private readonly watcher: EffectRef = effect(() => {
    const allowed = this.sessionStore.hasPermission(this.appHasPermission());
    if (allowed && !this.hasView) {
      this.viewContainerRef.createEmbeddedView(this.templateRef);
      this.hasView = true;
    } else if (!allowed && this.hasView) {
      this.viewContainerRef.clear();
      this.hasView = false;
    }
  });
}
