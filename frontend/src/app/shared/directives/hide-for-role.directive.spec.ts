import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { SessionStore } from '../../core/session/session.store';
import { HideForRoleDirective } from './hide-for-role.directive';

@Component({
  standalone: true,
  imports: [HideForRoleDirective],
  template: `<div *appHideForRole="'SUPERADMIN'">SECRET</div>`,
})
class TestHostComponent {}

describe('HideForRoleDirective', () => {
  let sessionStore: SessionStore;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    sessionStore = TestBed.inject(SessionStore);
  });

  it('shows content when the session role differs from the hidden role', () => {
    sessionStore.setUser({
      id: 'u1',
      email: 'm@brika.test',
      role: 'MANAGER',
      companyId: 'c1',
      entitlements: {},
    });
    const fixture = TestBed.createComponent(TestHostComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('SECRET');
  });

  it('hides content when the session role equals the hidden role', () => {
    sessionStore.setUser({
      id: 's1',
      email: 's@brika.test',
      role: 'SUPERADMIN',
      companyId: null,
      entitlements: {},
    });
    const fixture = TestBed.createComponent(TestHostComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('SECRET');
  });
});
