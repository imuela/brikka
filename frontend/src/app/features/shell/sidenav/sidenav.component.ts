import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { filter } from 'rxjs';

import { SessionStore } from '../../../core/session/session.store';
import { HasPermissionDirective } from '../../../shared/directives/has-permission.directive';
import { LogoComponent } from '../../../shared/logo/logo.component';
import { NotificationService } from '../../notifications/notification.service';
import { navItemsForRole } from './nav-items';

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
  private readonly sessionStore = inject(SessionStore);

  readonly navItems = computed(() => navItemsForRole(this.sessionStore.role()));

  /** Sprint 25: unread count for the Notifications nav badge. Refreshed on init and on every
   * navigation end so it stays current without polling (no WebSockets/SSE). */
  readonly unreadCount = signal(0);

  private readonly notificationService = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  constructor() {
    this.refreshUnreadCount();
    const sub = this.router.events
      .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
      .subscribe(() => this.refreshUnreadCount());
    this.destroyRef.onDestroy(() => sub.unsubscribe());
  }

  private refreshUnreadCount(): void {
    this.notificationService.unreadCount().subscribe({
      next: (count) => this.unreadCount.set(count),
      error: () => this.unreadCount.set(0),
    });
  }
}
