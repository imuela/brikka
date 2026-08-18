import { Component, inject } from '@angular/core';
import { MatCardModule } from '@angular/material/card';

import { SessionStore } from '../../../core/session/session.store';
import { ROLE_LABELS } from '../../../shared/labels/status-labels';
import { StatusLabelPipe } from '../../../shared/pipes/status-label.pipe';

/** Sprint 13 foundation placeholder — proves the shell/session pipeline works end-to-end. No
 * business content; Sprint 14+ features replace or extend this. */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [MatCardModule, StatusLabelPipe],
  templateUrl: './dashboard.component.html',
})
export class DashboardComponent {
  readonly sessionStore = inject(SessionStore);
  readonly roleLabels = ROLE_LABELS;
}
