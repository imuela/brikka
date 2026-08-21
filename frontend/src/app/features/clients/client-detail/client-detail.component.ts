import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { HasPermissionDirective } from '../../../shared/directives/has-permission.directive';
import { ApiError } from '../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../core/http/error-messages';
import {
  CLIENT_STATUS_LABELS,
  FINANCIAL_PROFILE_SOURCE_LABELS,
  FINANCIAL_PROFILE_STATUS_LABELS,
} from '../../../shared/labels/status-labels';
import { StatusLabelPipe } from '../../../shared/pipes/status-label.pipe';
import { Client } from '../client.model';
import { ClientsService } from '../clients.service';
import { EditFinancialProfileDialogComponent } from '../financial-profile/edit-financial-profile-dialog.component';
import { ClientFinancialProfile } from '../financial-profile/financial-profile.model';
import { FinancialProfileService } from '../financial-profile/financial-profile.service';

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
    DatePipe,
  ],
  templateUrl: './client-detail.component.html',
})
export class ClientDetailComponent {
  private readonly clientsService = inject(ClientsService);
  private readonly financialProfileService = inject(FinancialProfileService);
  private readonly route = inject(ActivatedRoute);
  private readonly dialog = inject(MatDialog);

  readonly client = signal<Client | null>(null);
  readonly error = signal<string | null>(null);
  readonly clientId = this.route.snapshot.paramMap.get('id')!;
  readonly clientStatusLabels = CLIENT_STATUS_LABELS;

  /** null = not loaded yet (spinner), undefined = loaded, no profile exists (empty state),
   * an object = loaded with data. */
  readonly financialProfile = signal<ClientFinancialProfile | null | undefined>(null);
  readonly financialProfileError = signal<string | null>(null);
  readonly financialProfileSourceLabels = FINANCIAL_PROFILE_SOURCE_LABELS;
  readonly financialProfileStatusLabels = FINANCIAL_PROFILE_STATUS_LABELS;

  constructor() {
    this.clientsService.get(this.clientId).subscribe({
      next: (client) => this.client.set(client),
      error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
    });
    this.loadFinancialProfile();
  }

  private loadFinancialProfile(): void {
    this.financialProfileService.get(this.clientId).subscribe({
      next: (profile) => this.financialProfile.set(profile),
      error: (err: ApiError) => {
        if (err.code === 'FINANCIAL_PROFILE_NOT_FOUND') {
          this.financialProfile.set(undefined);
          return;
        }
        this.financialProfileError.set(friendlyErrorMessage(err));
      },
    });
  }

  openFinancialProfileDialog(): void {
    this.dialog
      .open(EditFinancialProfileDialogComponent, {
        data: { clientId: this.clientId, profile: this.financialProfile() ?? null },
        width: '480px',
      })
      .afterClosed()
      .subscribe((profile: ClientFinancialProfile | undefined) => {
        if (profile) {
          this.financialProfile.set(profile);
        }
      });
  }
}
