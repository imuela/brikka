import { Directive, EffectRef, TemplateRef, ViewContainerRef, effect, inject, input } from '@angular/core';

import { SessionStore } from '../../core/session/session.store';

/**
 * `*appHideForRole="'SUPERADMIN'"` — hides the element when the current session's role equals the
 * given value. Sprint 27 (ADR-RBAC-002): a GLOBAL SUPERADMIN can read every tenant screen but, with
 * no company of their own and SUPPORT_SESSION not yet implemented, cannot create tenant-operational
 * records (client/case/task/user) from the UI — so the create action is hidden rather than letting
 * the backend reject it with 403 (03_TECHNICAL_SPECIFICATION.md §3 / sprint §8 no-unjustified-403).
 * It is UX-only, never a substitute for the backend's own authorization check.
 */
@Directive({
  selector: '[appHideForRole]',
  standalone: true,
})
export class HideForRoleDirective {
  private readonly templateRef = inject(TemplateRef<unknown>);
  private readonly viewContainerRef = inject(ViewContainerRef);
  private readonly sessionStore = inject(SessionStore);

  readonly appHideForRole = input.required<string>();

  private hasView = false;
  private readonly watcher: EffectRef = effect(() => {
    const hidden = this.sessionStore.role() === this.appHideForRole();
    if (!hidden && !this.hasView) {
      this.viewContainerRef.createEmbeddedView(this.templateRef);
      this.hasView = true;
    } else if (hidden && this.hasView) {
      this.viewContainerRef.clear();
      this.hasView = false;
    }
  });
}