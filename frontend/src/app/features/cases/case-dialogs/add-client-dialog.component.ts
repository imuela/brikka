import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';

import { ApiError } from '../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../core/http/error-messages';
import { PARTICIPATION_TYPE_LABELS } from '../../../shared/labels/status-labels';
import { StatusLabelPipe } from '../../../shared/pipes/status-label.pipe';
import { Client } from '../../clients/client.model';
import { ClientsService } from '../../clients/clients.service';
import { PARTICIPATION_TYPES } from '../case.model';
import { CasesService } from '../cases.service';

export interface AddClientDialogData {
  caseId: string;
}

/** Reuses ClientsService (features/clients) to list real tenant clients for the picker — no
 * duplicate HTTP call is written, no new "shared" abstraction is introduced for a single reuse. */
@Component({
  selector: 'app-add-client-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatSelectModule,
    MatCheckboxModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    StatusLabelPipe,
  ],
  templateUrl: './add-client-dialog.component.html',
})
export class AddClientDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly casesService = inject(CasesService);
  private readonly clientsService = inject(ClientsService);
  private readonly dialogRef = inject(MatDialogRef<AddClientDialogComponent, boolean>);
  private readonly data = inject<AddClientDialogData>(MAT_DIALOG_DATA);

  readonly clients = signal<Client[] | null>(null);
  readonly participationTypes = PARTICIPATION_TYPES;
  readonly participationTypeLabels = PARTICIPATION_TYPE_LABELS;
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    clientId: ['', Validators.required],
    participationType: ['', Validators.required],
    isPrimary: [false],
  });

  constructor() {
    this.clientsService.list().subscribe({
      next: (clients) => this.clients.set(clients),
      error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.casesService.addClient(this.data.caseId, this.form.getRawValue()).subscribe({
      next: () => this.dialogRef.close(true),
      error: (err: ApiError) => {
        this.loading.set(false);
        this.error.set(friendlyErrorMessage(err));
      },
    });
  }

  cancel(): void {
    this.dialogRef.close();
  }
}
