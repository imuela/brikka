import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';

import { ApiError } from '../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../core/http/error-messages';
import { INTEREST_TYPE_LABELS } from '../../../shared/labels/status-labels';
import { InterestType, Simulation } from '../financing.model';
import { FinancingService } from '../financing.service';

export interface CreateSimulationDialogData {
  caseId: string;
}

/** BRIKKA V2 I4. Adapts its fields to the interest type (FIXED / VARIABLE / MIXED), lets the user
 * add bonifications, and shows the base rate, the sum of active bonifications and the final rate
 * live. The monthly payment is the server's (French amortization, MortgagePaymentCalculator) —
 * shown from the response once the simulation is created; no amortization formula is duplicated on
 * the client. Simulation has no update/delete endpoint, so this dialog only ever creates. */
@Component({
  selector: 'app-create-simulation-dialog',
  standalone: true,
  imports: [
    CurrencyPipe,
    DecimalPipe,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatCheckboxModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './create-simulation-dialog.component.html',
})
export class CreateSimulationDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly financingService = inject(FinancingService);
  private readonly dialogRef = inject(MatDialogRef<CreateSimulationDialogComponent, Simulation>);
  private readonly data = inject<CreateSimulationDialogData>(MAT_DIALOG_DATA);

  readonly interestTypeLabels = INTEREST_TYPE_LABELS;
  readonly bonificationCatalog: { code: string; label: string }[] = [
    { code: 'PAYROLL', label: 'Domiciliación de nómina' },
    { code: 'HOME_INSURANCE', label: 'Seguro de hogar' },
    { code: 'LIFE_INSURANCE', label: 'Seguro de vida' },
    { code: 'ALARM', label: 'Alarma' },
    { code: 'CARD', label: 'Tarjeta' },
    { code: 'INVESTMENTS', label: 'Inversiones / plan de pensiones' },
    { code: 'OTHER', label: 'Otra bonificación' },
  ];

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly result = signal<Simulation | null>(null);

  readonly form = this.fb.nonNullable.group({
    interestType: ['FIXED' as InterestType, Validators.required],
    principal: ['', Validators.required],
    termMonths: ['', Validators.required],
    fixedRate: [''],
    euriborRate: [''],
    spreadRate: [''],
    fixedPeriodMonths: [''],
    fixedPeriodRate: [''],
    icoGuarantee: [false],
  });

  readonly bonifications = this.fb.array<FormGroup>([]);

  private readonly typeSignal = signal<InterestType>('FIXED');
  readonly interestType = computed(() => this.typeSignal());

  constructor() {
    this.applyTypeValidators('FIXED');
    this.form.controls.interestType.valueChanges.subscribe((type) => {
      this.typeSignal.set(type);
      this.applyTypeValidators(type);
    });
  }

  /** Only the rate fields the chosen type needs are required; the rest are cleared and optional. */
  private applyTypeValidators(type: InterestType): void {
    const perType: Record<InterestType, string[]> = {
      FIXED: ['fixedRate'],
      VARIABLE: ['euriborRate', 'spreadRate'],
      MIXED: ['fixedPeriodMonths', 'fixedPeriodRate', 'euriborRate', 'spreadRate'],
    };
    const rateControls = [
      'fixedRate',
      'euriborRate',
      'spreadRate',
      'fixedPeriodMonths',
      'fixedPeriodRate',
    ] as const;
    for (const name of rateControls) {
      const control = this.form.controls[name];
      if (perType[type].includes(name)) {
        control.addValidators(Validators.required);
      } else {
        control.clearValidators();
        control.setValue('');
      }
      control.updateValueAndValidity({ emitEvent: false });
    }
  }

  addBonification(code = 'PAYROLL'): void {
    const known = this.bonificationCatalog.find((b) => b.code === code);
    this.bonifications.push(
      this.fb.nonNullable.group({
        code: [code, Validators.required],
        label: [known?.label ?? '', Validators.required],
        rate: ['', Validators.required],
        active: [true],
      }),
    );
  }

  removeBonification(index: number): void {
    this.bonifications.removeAt(index);
  }

  onBonificationCodeChange(index: number): void {
    const group = this.bonifications.at(index);
    const code = group.get('code')?.value as string;
    const known = this.bonificationCatalog.find((b) => b.code === code);
    if (known && known.code !== 'OTHER') {
      group.get('label')?.setValue(known.label);
    }
  }

  /** Base rate before bonifications, per type (trivial arithmetic — not a payment calculation). */
  baseRatePreview(): number | null {
    const v = this.form.getRawValue();
    if (v.interestType === 'FIXED') {
      return v.fixedRate === '' ? null : Number(v.fixedRate);
    }
    if (v.interestType === 'VARIABLE') {
      if (v.euriborRate === '' || v.spreadRate === '') return null;
      return Number(v.euriborRate) + Number(v.spreadRate);
    }
    return v.fixedPeriodRate === '' ? null : Number(v.fixedPeriodRate);
  }

  totalBonificationPreview(): number {
    return this.bonifications.controls
      .map((g) => g.getRawValue() as { rate: string; active: boolean })
      .filter((b) => b.active && b.rate !== '')
      .reduce((sum, b) => sum + Number(b.rate), 0);
  }

  finalRatePreview(): number | null {
    const base = this.baseRatePreview();
    if (base === null) return null;
    return Math.max(0, base - this.totalBonificationPreview());
  }

  submit(): void {
    if (this.form.invalid || this.bonifications.invalid) {
      this.form.markAllAsTouched();
      this.bonifications.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    const v = this.form.getRawValue();
    const numberOrNull = (raw: string) => (raw === '' ? null : Number(raw));

    this.financingService
      .createSimulation(this.data.caseId, {
        interestType: v.interestType,
        principal: Number(v.principal),
        termMonths: Number(v.termMonths),
        fixedRate: numberOrNull(v.fixedRate),
        euriborRate: numberOrNull(v.euriborRate),
        spreadRate: numberOrNull(v.spreadRate),
        fixedPeriodMonths: numberOrNull(v.fixedPeriodMonths),
        fixedPeriodRate: numberOrNull(v.fixedPeriodRate),
        bonifications: this.bonifications.controls.map((g) => {
          const b = g.getRawValue() as {
            code: string;
            label: string;
            rate: string;
            active: boolean;
          };
          return { code: b.code, label: b.label, rate: Number(b.rate), active: b.active };
        }),
        icoGuarantee: v.icoGuarantee,
        metadata: {},
      })
      .subscribe({
        next: (simulation) => {
          this.loading.set(false);
          this.result.set(simulation);
        },
        error: (err: ApiError) => {
          this.loading.set(false);
          this.error.set(friendlyErrorMessage(err));
        },
      });
  }

  close(): void {
    this.dialogRef.close(this.result() ?? undefined);
  }

  cancel(): void {
    this.dialogRef.close();
  }
}
