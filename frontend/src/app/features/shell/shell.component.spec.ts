import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { ShellComponent } from './shell.component';
import { SessionStore } from '../../core/session/session.store';

describe('ShellComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ShellComponent, NoopAnimationsModule],
      providers: [provideRouter([])],
    });
  });

  it('shows the signed-in user email in the header', () => {
    const sessionStore = TestBed.inject(SessionStore);
    sessionStore.setUser({
      id: 'u1',
      email: 'manager@brika.test',
      role: 'MANAGER',
      companyId: 'c1',
      entitlements: {},
    });

    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('manager@brika.test');
  });

  it('always shows the always-visible nav item (no permission required)', () => {
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Panel');
  });
});
