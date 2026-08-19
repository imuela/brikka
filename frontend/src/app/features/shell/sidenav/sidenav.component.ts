import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';

import { HasPermissionDirective } from '../../../shared/directives/has-permission.directive';
import { LogoComponent } from '../../../shared/logo/logo.component';
import { NAV_ITEMS } from './nav-items';

@Component({
  selector: 'app-sidenav',
  standalone: true,
  imports: [
    RouterLink,
    RouterLinkActive,
    MatListModule,
    MatIconModule,
    HasPermissionDirective,
    LogoComponent,
  ],
  templateUrl: './sidenav.component.html',
  styleUrl: './sidenav.component.scss',
})
export class SidenavComponent {
  readonly navItems = NAV_ITEMS;
}
