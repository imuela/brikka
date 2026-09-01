import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { Observable, catchError, map, of, switchMap } from 'rxjs';

import { ApiError } from '../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../core/http/error-messages';
import { ClientFinancialProfile } from '../financial-profile/financial-profile.model';
import { FinancialProfileService } from '../financial-profile/financial-profile.service';
import { DOCUMENT_TYPES, EMPLOYMENT_STATUSES } from '../client.model';
import { ClientsService } from '../clients.service';

/**
 * Create (/app/clients/new) and edit (/app/clients/:id/edit) share this component — same fields
 * for both requests (CreateClientApiRequest and UpdateClientApiRequest are identical shapes).
 * Sprint 27, Bloque 3 adds the extended attributes (document, date of birth, nationality, address,
 * employment status) as optional — only name/email/phone remain required.
 *
 * Sprint 40.x adds "Empresa actual" (employerName) and "Antigüedad" (yearsEmployed): both already
 * exist, but on the separate ClientFinancialProfile resource (its own table/controller/history),
 * not on Client. Rather than duplicate them as new Client columns, this form reads/writes those two
 * fields on the financial profile directly, preserving every other financial-profile field
 * untouched (a snapshot fetched once, at load time, the same way EditFinancialProfileDialogComponent
 * already snapshots the profile at open time).
 */
@Component({
  selector: 'app-client-form',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './client-form.component.html',
  styleUrl: './client-form.component.scss',
})
export class ClientFormComponent {
  private readonly fb = inject(FormBuilder);
  private readonly clientsService = inject(ClientsService);
  private readonly financialProfileService = inject(FinancialProfileService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  private readonly clientId = this.route.snapshot.paramMap.get('id');
  readonly isEditMode = this.clientId !== null;
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly documentTypes = DOCUMENT_TYPES;
  readonly employmentStatuses = EMPLOYMENT_STATUSES;

  /** Snapshot of the financial profile as it was when the form loaded — undefined once we've
   * confirmed (via a 404) that no profile exists yet. Used at submit time to preserve every
   * financial-profile field this form doesn't expose (maritalStatus, monthlyIncome, source...). */
  private existingFinancialProfile: ClientFinancialProfile | undefined;

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
    employerName: [''],
    yearsEmployed: [null as number | null, Validators.min(0)],
  });

  constructor() {
    if (this.clientId) {
      const id = this.clientId;
      this.clientsService.get(id).subscribe({
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
      this.financialProfileService.get(id).subscribe({
        next: (profile) => {
          this.existingFinancialProfile = profile;
          this.form.patchValue({
            employerName: profile.employerName ?? '',
            yearsEmployed: profile.yearsEmployed,
          });
        },
        error: () => {
          // FINANCIAL_PROFILE_NOT_FOUND (or any other read failure) — no profile to preserve yet.
          this.existingFinancialProfile = undefined;
        },
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

    request$
      .pipe(
        switchMap((client) =>
          this.syncFinancialProfile(client.id).pipe(
            // The client itself is already saved at this point — a failure syncing the financial
            // profile must not be reported as a client-save failure, nor block navigation.
            catchError(() => of(null)),
            map(() => client),
          ),
        ),
      )
      .subscribe({
        next: (client) => this.router.navigate(['/app/clients', client.id]),
        error: (err: ApiError) => {
          this.loading.set(false);
          this.error.set(friendlyErrorMessage(err));
        },
      });
  }

  /** Upserts employerName/yearsEmployed onto the client's financial profile, carrying forward
   * every other field from the load-time snapshot untouched. Skipped entirely when there is
   * nothing to preserve and nothing new to save, so a client with no financial data never gets an
   * empty profile row created just because this form exists. */
  private syncFinancialProfile(clientId: string): Observable<ClientFinancialProfile | null> {
    const v = this.form.getRawValue();
    const employerName = v.employerName?.trim() || null;
    const yearsEmployed = v.yearsEmployed;
    const existing = this.existingFinancialProfile;

    if (!existing && employerName === null && yearsEmployed === null) {
      return of(null);
    }

    return this.financialProfileService.upsert(clientId, {
      maritalStatus: existing?.maritalStatus ?? null,
      dependents: existing?.dependents ?? null,
      employmentType: existing?.employmentType ?? null,
      contractType: existing?.contractType ?? null,
      employerName,
      yearsEmployed,
      monthlyIncome: existing?.monthlyIncome ?? null,
      savings: existing?.savings ?? null,
      otherDebtsMonthlyPayment: existing?.otherDebtsMonthlyPayment ?? null,
      creditCardDebt: existing?.creditCardDebt ?? null,
      source: existing?.source ?? null,
      status: existing?.status ?? null,
      evidenceDocumentVersionId: existing?.evidenceDocumentVersionId ?? null,
    });
  }
}