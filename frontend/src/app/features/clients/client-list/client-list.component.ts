import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';

import { HasPermissionDirective } from '../../../shared/directives/has-permission.directive';
import { ApiError } from '../../../core/http/api-error';
import { Client } from '../client.model';
import { ClientsService } from '../clients.service';

@Component({
  selector: 'app-client-list',
  standalone: true,
  imports: [
    RouterLink,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    HasPermissionDirective,
  ],
  templateUrl: './client-list.component.html',
  styleUrl: './client-list.component.scss',
})
export class ClientListComponent {
  private readonly clientsService = inject(ClientsService);

  readonly clients = signal<Client[] | null>(null);
  readonly error = signal<string | null>(null);
  readonly displayedColumns = ['name', 'email', 'phone', 'status'];

  constructor() {
    this.clientsService.list().subscribe({
      next: (clients) => this.clients.set(clients),
      error: (err: ApiError) => this.error.set(err.message),
    });
  }
}
