import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';

import { HasPermissionDirective } from '../../../shared/directives/has-permission.directive';
import { NAV_ITEMS } from './nav-items';

@Component({
  selector: 'app-sidenav',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, MatListModule, MatIconModule, HasPermissionDirective],
  templateUrl: './sidenav.component.html',
})
export class SidenavComponent {
  readonly navItems = NAV_ITEMS;
}
