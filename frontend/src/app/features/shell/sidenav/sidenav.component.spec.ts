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
        { provide: SessionStore, useValue: { hasPermission: () => true } },
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
});