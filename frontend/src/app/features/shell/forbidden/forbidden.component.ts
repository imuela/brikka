import { Component } from '@angular/core';
import { MatCardModule } from '@angular/material/card';

@Component({
  selector: 'app-forbidden',
  standalone: true,
  imports: [MatCardModule],
  template: `
    <mat-card>
      <mat-card-header>
        <mat-card-title>Sin permiso</mat-card-title>
      </mat-card-header>
      <mat-card-content>
        <p>No tienes permiso para acceder a esta sección.</p>
      </mat-card-content>
    </mat-card>
  `,
})
export class ForbiddenComponent {}
