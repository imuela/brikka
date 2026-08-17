import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';

import { HasPermissionDirective } from '../../../shared/directives/has-permission.directive';
import { ApiError } from '../../../core/http/api-error';
import { Case } from '../case.model';
import { CasesService } from '../cases.service';

/** Backend already filters this list by role — BROKER sees only assigned cases, MANAGER/
 * SUPERADMIN see the whole tenant (CaseController.list) — the frontend applies no filtering of
 * its own. */
@Component({
  selector: 'app-case-list',
  standalone: true,
  imports: [
    RouterLink,
    DatePipe,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    HasPermissionDirective,
  ],
  templateUrl: './case-list.component.html',
  styleUrl: './case-list.component.scss',
})
export class CaseListComponent {
  private readonly casesService = inject(CasesService);

  readonly cases = signal<Case[] | null>(null);
  readonly error = signal<string | null>(null);
  readonly displayedColumns = ['reference', 'operationType', 'status', 'createdAt'];

  constructor() {
    this.casesService.list().subscribe({
      next: (cases) => this.cases.set(cases),
      error: (err: ApiError) => this.error.set(err.message),
    });
  }
}
