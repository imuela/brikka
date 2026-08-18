import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { ApiError } from '../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../core/http/error-messages';
import { ClientsService } from '../clients.service';

/**
 * Create (/app/clients/new) and edit (/app/clients/:id/edit) share this component — same fields
 * for both requests (CreateClientApiRequest and UpdateClientApiRequest are identical shapes).
 * Only client-side validation is "required" (a form must have something to submit); the backend
 * does not document any format/length rule beyond that, so none is invented here.
 */
@Component({
  selector: 'app-client-form',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './client-form.component.html',
  styleUrl: './client-form.component.scss',
})
export class ClientFormComponent {
  private readonly fb = inject(FormBuilder);
  private readonly clientsService = inject(ClientsService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  private readonly clientId = this.route.snapshot.paramMap.get('id');
  readonly isEditMode = this.clientId !== null;
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    email: ['', Validators.required],
    phone: ['', Validators.required],
  });

  constructor() {
    if (this.clientId) {
      this.clientsService.get(this.clientId).subscribe({
        next: (client) => this.form.patchValue(client),
        error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
      });
    }
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    const value = this.form.getRawValue();
    const request$ = this.clientId
      ? this.clientsService.update(this.clientId, value)
      : this.clientsService.create(value);

    request$.subscribe({
      next: (client) => this.router.navigate(['/app/clients', client.id]),
      error: (err: ApiError) => {
        this.loading.set(false);
        this.error.set(friendlyErrorMessage(err));
      },
    });
  }
}
