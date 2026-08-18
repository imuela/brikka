import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';

import { ApiError } from '../../core/http/api-error';
import { friendlyErrorMessage } from '../../core/http/error-messages';
import { PROPERTY_TYPE_LABELS } from '../../shared/labels/status-labels';
import { StatusLabelPipe } from '../../shared/pipes/status-label.pipe';
import { PROPERTY_TYPES, Property } from './property.model';
import { PropertyService } from './property.service';

export interface PropertyDialogData {
  caseId: string;
  property: Property | null;
}

/** address is schemaless jsonb on the backend — street/city/postalCode/province are the frontend's
 * chosen minimal field set, not a backend-documented schema (see property.model.ts). Empty fields
 * are dropped from the submitted address so we don't persist blank keys the user never filled.
 * propertyType is populated from PROPERTY_TYPES (Sprint 20, ADR-PROCESS-008). */
@Component({
  selector: 'app-property-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    StatusLabelPipe,
  ],
  templateUrl: './property-dialog.component.html',
})
export class PropertyDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly propertyService = inject(PropertyService);
  private readonly dialogRef = inject(MatDialogRef<PropertyDialogComponent, Property>);
  private readonly data = inject<PropertyDialogData>(MAT_DIALOG_DATA);

  readonly isEditMode = this.data.property !== null;
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly propertyTypes = PROPERTY_TYPES;
  readonly propertyTypeLabels = PROPERTY_TYPE_LABELS;

  readonly form = this.fb.nonNullable.group({
    propertyType: [this.data.property?.propertyType ?? '', Validators.required],
    street: [this.data.property?.address['street'] ?? ''],
    city: [this.data.property?.address['city'] ?? ''],
    postalCode: [this.data.property?.address['postalCode'] ?? ''],
    province: [this.data.property?.address['province'] ?? ''],
    valuation: [this.data.property?.valuation?.toString() ?? ''],
    purchasePrice: [this.data.property?.purchasePrice?.toString() ?? ''],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    const value = this.form.getRawValue();
    const address = Object.fromEntries(
      Object.entries({
        street: value.street,
        city: value.city,
        postalCode: value.postalCode,
        province: value.province,
      }).filter(([, v]) => v.trim().length > 0),
    );

    this.propertyService
      .upsert(this.data.caseId, {
        address,
        propertyType: value.propertyType,
        valuation: value.valuation ? Number(value.valuation) : null,
        purchasePrice: value.purchasePrice ? Number(value.purchasePrice) : null,
      })
      .subscribe({
        next: (property) => this.dialogRef.close(property),
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
