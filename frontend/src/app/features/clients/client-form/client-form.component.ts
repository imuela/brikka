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
 * Sprint 27, Bloque 3 adds the extended attributes (document, date of birth, nationality, address,
 * employment status) as optional — only name/email/phone remain required.
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

  readonly form = this.fb.group({
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    phone: ['', Validators.required],
    documentType: [''],
    documentNumber: [''],
    dateOfBirth: [''],
    nationality: [''],
    address: [''],
    employmentStatus: [''],
  });

  constructor() {
    if (this.clientId) {
      this.clientsService.get(this.clientId).subscribe({
        next: (client) =>
          this.form.patchValue({
            firstName: client.firstName,
            lastName: client.lastName,
            email: client.email,
            phone: client.phone,
            documentType: client.documentType ?? '',
            documentNumber: client.documentNumber ?? '',
            dateOfBirth: client.dateOfBirth ?? '',
            nationality: client.nationality ?? '',
            address: client.address ?? '',
            employmentStatus: client.employmentStatus ?? '',
          }),
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
    const v = this.form.getRawValue();
    const value: { firstName: string; lastName: string; email: string; phone: string; documentType: string | null; documentNumber: string | null; dateOfBirth: string | null; nationality: string | null; address: string | null; employmentStatus: string | null } = {
      firstName: v.firstName ?? '',
      lastName: v.lastName ?? '',
      email: v.email ?? '',
      phone: v.phone ?? '',
      documentType: v.documentType || null,
      documentNumber: v.documentNumber || null,
      dateOfBirth: v.dateOfBirth || null,
      nationality: v.nationality || null,
      address: v.address || null,
      employmentStatus: v.employmentStatus || null,
    };
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