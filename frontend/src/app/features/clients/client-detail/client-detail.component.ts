import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { HasPermissionDirective } from '../../../shared/directives/has-permission.directive';
import { ApiError } from '../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../core/http/error-messages';
import { CLIENT_STATUS_LABELS } from '../../../shared/labels/status-labels';
import { StatusLabelPipe } from '../../../shared/pipes/status-label.pipe';
import { Client } from '../client.model';
import { ClientsService } from '../clients.service';

@Component({
  selector: 'app-client-detail',
  standalone: true,
  imports: [
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    HasPermissionDirective,
    StatusLabelPipe,
  ],
  templateUrl: './client-detail.component.html',
})
export class ClientDetailComponent {
  private readonly clientsService = inject(ClientsService);
  private readonly route = inject(ActivatedRoute);

  readonly client = signal<Client | null>(null);
  readonly error = signal<string | null>(null);
  readonly clientId = this.route.snapshot.paramMap.get('id')!;
  readonly clientStatusLabels = CLIENT_STATUS_LABELS;

  constructor() {
    this.clientsService.get(this.clientId).subscribe({
      next: (client) => this.client.set(client),
      error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
    });
  }
}
