import { Component, ViewChild, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterOutlet } from '@angular/router';
import { MatSidenav, MatSidenavModule } from '@angular/material/sidenav';
import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { map } from 'rxjs';

import { HeaderComponent } from './header/header.component';
import { SidenavComponent } from './sidenav/sidenav.component';

/** Authenticated app shell: header + collapsible sidenav + routed content. Sprint 13 foundation
 * only — feature routes are added under `/app/**` starting Sprint 14. */
@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, MatSidenavModule, HeaderComponent, SidenavComponent],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
})
export class ShellComponent {
  @ViewChild('drawer') drawer!: MatSidenav;

  private readonly breakpointObserver = inject(BreakpointObserver);

  /** Diseño de sistema: por debajo de 840px (móvil + tablet en vertical) el sidenav pasa a modo
   * "over" (overlay, cerrado por defecto) en lugar del "side" fijo de escritorio — sin esto, un
   * sidenav de 240px fijo se comía media pantalla en móvil y dejaba el contenido de tablet
   * innecesariamente estrecho.
   *
   * <p>Sprint 35: el comentario original ya decía "por debajo de 768px", pero solo observaba
   * {@code Breakpoints.Handset} (≤599.98px en vertical) — nunca cubrió tablet en vertical
   * (600–839.98px), que es exactamente donde se reprodujo el problema. Corregido observando
   * también {@code Breakpoints.TabletPortrait}, sin tocar el modo "side" fijo de tablet en
   * horizontal ni de escritorio. */
  protected readonly isCompact = toSignal(
    this.breakpointObserver
      .observe([Breakpoints.Handset, Breakpoints.TabletPortrait])
      .pipe(map((result) => result.matches)),
    { initialValue: false },
  );

  toggleSidenav(): void {
    void this.drawer.toggle();
  }
}
