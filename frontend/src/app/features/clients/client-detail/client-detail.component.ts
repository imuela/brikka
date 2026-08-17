import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { HasPermissionDirective } from '../../../shared/directives/has-permission.directive';
import { ApiError } from '../../../core/http/api-error';
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
  ],
  templateUrl: './client-detail.component.html',
})
export class ClientDetailComponent {
  private readonly clientsService = inject(ClientsService);
  private readonly route = inject(ActivatedRoute);

  readonly client = signal<Client | null>(null);
  readonly error = signal<string | null>(null);
  readonly clientId = this.route.snapshot.paramMap.get('id')!;

  constructor() {
    this.clientsService.get(this.clientId).subscribe({
      next: (client) => this.client.set(client),
      error: (err: ApiError) => this.error.set(err.message),
    });
  }
}
