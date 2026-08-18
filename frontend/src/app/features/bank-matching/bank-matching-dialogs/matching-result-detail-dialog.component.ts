import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatTableModule } from '@angular/material/table';

import { HasPermissionDirective } from '../../../shared/directives/has-permission.directive';
import { MATCH_RESULT_LABELS } from '../../../shared/labels/status-labels';
import { StatusLabelPipe } from '../../../shared/pipes/status-label.pipe';
import { BankMatchResult } from '../bank-matching.model';
import { BankMatchingService } from '../bank-matching.service';
import { OverrideRuleDialogComponent } from './override-rule-dialog.component';

export interface MatchingResultDetailDialogData {
  caseId: string;
  result: BankMatchResult;
}

/** Renders a matching result's per-rule breakdown. `expectedValue`/`evaluatedValue` and
 * `inputSnapshot` are schemaless (server-built, no guaranteed shape) — formatted defensively via
 * `formatValue`, never assumed or shown as raw technical JSON except as a last-resort fallback. */
@Component({
  selector: 'app-matching-result-detail-dialog',
  standalone: true,
  imports: [DatePipe, MatDialogModule, MatButtonModule, MatTableModule, HasPermissionDirective, StatusLabelPipe],
  templateUrl: './matching-result-detail-dialog.component.html',
})
export class MatchingResultDetailDialogComponent {
  private readonly bankMatchingService = inject(BankMatchingService);
  private readonly dialog = inject(MatDialog);
  private readonly dialogRef = inject(MatDialogRef<MatchingResultDetailDialogComponent>);
  private readonly data = inject<MatchingResultDetailDialogData>(MAT_DIALOG_DATA);

  readonly matchResultLabels = MATCH_RESULT_LABELS;
  readonly result = signal(this.data.result);
  readonly ruleColumns = ['field', 'operator', 'expected', 'evaluated', 'result', 'actions'];

  openOverride(ruleResultId: string, currentResult: string): void {
    this.dialog
      .open(OverrideRuleDialogComponent, {
        data: { ruleResultId, currentResult },
        width: '400px',
      })
      .afterClosed()
      .subscribe((created) => {
        if (created) {
          this.reload();
        }
      });
  }

  private reload(): void {
    this.bankMatchingService.list(this.data.caseId).subscribe((results) => {
      const updated = results.find((r) => r.id === this.result().id);
      if (updated) {
        this.result.set(updated);
      }
    });
  }

  formatValue(value: unknown): string {
    if (value === null || value === undefined) {
      return '—';
    }
    if (typeof value === 'number' || typeof value === 'string' || typeof value === 'boolean') {
      return String(value);
    }
    try {
      return JSON.stringify(value);
    } catch {
      return '—';
    }
  }

  close(): void {
    this.dialogRef.close();
  }
}
