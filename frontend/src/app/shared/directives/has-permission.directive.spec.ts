import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { HasPermissionDirective } from './has-permission.directive';
import { SessionStore } from '../../core/session/session.store';

@Component({
  standalone: true,
  imports: [HasPermissionDirective],
  template: `<span *appHasPermission="'CASE_READ'">visible</span>`,
})
class HostComponent {}

describe('HasPermissionDirective', () => {
  let sessionStore: SessionStore;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HostComponent] });
    sessionStore = TestBed.inject(SessionStore);
  });

  it('hides the element when the permission is missing', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent.trim()).toBe('');
  });

  it('shows the element once the session gains the permission', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();

    sessionStore.setPermissions(['CASE_READ']);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent.trim()).toBe('visible');
  });

  it('hides the element again if the permission is later removed', () => {
    sessionStore.setPermissions(['CASE_READ']);
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent.trim()).toBe('visible');

    sessionStore.setPermissions([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent.trim()).toBe('');
  });
});
