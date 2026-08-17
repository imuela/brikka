import { TestBed } from '@angular/core/testing';

import { SessionStore } from './session.store';
import { MeResponse } from './me.model';

describe('SessionStore', () => {
  let store: SessionStore;

  const user: MeResponse = {
    id: 'user-1',
    email: 'manager@brika.test',
    role: 'MANAGER',
    companyId: 'company-1',
    entitlements: {},
  };

  beforeEach(() => {
    TestBed.configureTestingModule({});
    store = TestBed.inject(SessionStore);
  });

  it('starts unhydrated with no permissions', () => {
    expect(store.isHydrated()).toBe(false);
    expect(store.user()).toBeNull();
    expect(store.hasPermission('CASE_READ')).toBe(false);
  });

  it('setUser/setPermissions hydrate the store', () => {
    store.setUser(user);
    store.setPermissions(['CASE_READ', 'CLIENT_READ']);

    expect(store.isHydrated()).toBe(true);
    expect(store.user()).toEqual(user);
    expect(store.role()).toBe('MANAGER');
    expect(store.companyId()).toBe('company-1');
    expect(store.hasPermission('CASE_READ')).toBe(true);
    expect(store.hasPermission('COMPANY_DELETE')).toBe(false);
  });

  it('clear resets user and permissions', () => {
    store.setUser(user);
    store.setPermissions(['CASE_READ']);

    store.clear();

    expect(store.isHydrated()).toBe(false);
    expect(store.user()).toBeNull();
    expect(store.hasPermission('CASE_READ')).toBe(false);
  });
});
