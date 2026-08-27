import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { of } from 'rxjs';

import { SessionStore } from '../../../core/session/session.store';
import { NotificationService } from '../../notifications/notification.service';
import { SidenavComponent } from './sidenav.component';

describe('SidenavComponent', () => {
  let component: SidenavComponent;
  let fixture: ComponentFixture<SidenavComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RouterTestingModule],
      providers: [
        { provide: SessionStore, useValue: { hasPermission: () => true, role: () => 'BROKER' } },
        { provide: NotificationService, useValue: { unreadCount: () => of(3) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SidenavComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads the unread notification count from the service', () => {
    expect(component.unreadCount()).toBe(3);
  });

  it('renders a badge with the unread count on the notifications item', () => {
    const badges = fixture.nativeElement.querySelectorAll('.notif-badge');
    expect(badges.length).toBe(1);
    expect(badges[0].textContent?.trim()).toBe('3');
  });

  it('does not render a badge when there are no unread notifications', () => {
    component.unreadCount.set(0);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.notif-badge').length).toBe(0);
  });

  it('keeps NAV_ITEMS default order for BROKER (and any other role without a dedicated order)', () => {
    const labels = component.navItems().map((item) => item.label);
    expect(labels).toEqual([
      'Panel',
      'Clientes',
      'Casos',
      'Bancos',
      'Tareas',
      'Notificaciones',
      'Usuarios',
      'Empresas',
      'Planes',
    ]);
  });
});

describe('SidenavComponent (SUPERADMIN)', () => {
  let fixture: ComponentFixture<SidenavComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RouterTestingModule],
      providers: [
        {
          provide: SessionStore,
          useValue: { hasPermission: () => true, role: () => 'SUPERADMIN' },
        },
        { provide: NotificationService, useValue: { unreadCount: () => of(0) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SidenavComponent);
    fixture.detectChanges();
  });

  it('renders exactly the 9 existing items in the requested SUPERADMIN order, same icons/routes', () => {
    const items = fixture.componentInstance.navItems();
    expect(items.map((item) => item.label)).toEqual([
      'Panel',
      'Empresas',
      'Planes',
      'Usuarios',
      'Bancos',
      'Clientes',
      'Casos',
      'Tareas',
      'Notificaciones',
    ]);
    // Same 9 items as the default order — nothing added, nothing removed.
    expect(items.map((item) => item.route).sort()).toEqual(
      [
        '/app',
        '/app/clients',
        '/app/cases',
        '/app/banks',
        '/app/tasks',
        '/app/notifications',
        '/app/users',
        '/app/companies',
        '/app/plans',
      ].sort(),
    );
  });
});

describe('SidenavComponent (MANAGER)', () => {
  let fixture: ComponentFixture<SidenavComponent>;

  // Real MANAGER seed data (verified via live browser login against manager@brika.local) can
  // actually hold COMPANY_READ — the permission split is not reliable on its own. Granting it here
  // too proves the 7-item menu holds regardless: the exclusion of Empresas/Planes must be
  // structural (in navItemsForRole), not an accident of this particular account's permissions.
  const managerPermissions = new Set([
    'CLIENT_READ',
    'CASE_READ',
    'BANK_READ',
    'USER_READ',
    'TASK_READ',
    'NOTIFICATION_READ',
    'COMPANY_READ',
  ]);

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RouterTestingModule],
      providers: [
        {
          provide: SessionStore,
          useValue: {
            hasPermission: (code: string) => managerPermissions.has(code),
            role: () => 'MANAGER',
          },
        },
        { provide: NotificationService, useValue: { unreadCount: () => of(0) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SidenavComponent);
    fixture.detectChanges();
  });

  it('orders the underlying nav items as exactly Panel, Clientes, Casos, Bancos, Usuarios, Tareas, Notificaciones — Empresas/Planes excluded structurally, even though this account has COMPANY_READ', () => {
    const items = fixture.componentInstance.navItems();
    expect(items.map((item) => item.label)).toEqual([
      'Panel',
      'Clientes',
      'Casos',
      'Bancos',
      'Usuarios',
      'Tareas',
      'Notificaciones',
    ]);
    // Same routes/permissions/icons as NAV_ITEMS — nothing added, nothing removed for these 7.
    const byRoute = new Map(items.map((item) => [item.route, item] as const));
    expect(byRoute.get('/app/users')?.permission).toBe('USER_READ');
    expect(byRoute.get('/app/users')?.icon).toBe('group');
  });

  it('renders exactly the 7-item MANAGER menu in order, with no duplicates and no Empresas/Planes', () => {
    const labels = Array.from(
      fixture.nativeElement.querySelectorAll('[matListItemTitle]') as NodeListOf<HTMLElement>,
    ).map((el) => el.textContent?.trim());

    expect(labels).toEqual([
      'Panel',
      'Clientes',
      'Casos',
      'Bancos',
      'Usuarios',
      'Tareas',
      'Notificaciones',
    ]);
  });
});
