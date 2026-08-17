import { Component, ViewChild } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { MatSidenav, MatSidenavModule } from '@angular/material/sidenav';

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

  toggleSidenav(): void {
    void this.drawer.toggle();
  }
}
