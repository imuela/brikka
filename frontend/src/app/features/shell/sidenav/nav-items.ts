export interface NavItem {
  label: string;
  icon: string;
  route: string;
  /** null = always visible once authenticated. */
  permission: string | null;
}

export const NAV_ITEMS: readonly NavItem[] = [
  { label: 'Panel', icon: 'dashboard', route: '/app', permission: null },
  { label: 'Clientes', icon: 'people', route: '/app/clients', permission: 'CLIENT_READ' },
  { label: 'Casos', icon: 'work', route: '/app/cases', permission: 'CASE_READ' },
  { label: 'Bancos', icon: 'account_balance', route: '/app/banks', permission: 'BANK_READ' },
  { label: 'Tareas', icon: 'checklist', route: '/app/tasks', permission: 'TASK_READ' },
  {
    label: 'Notificaciones',
    icon: 'notifications',
    route: '/app/notifications',
    permission: 'NOTIFICATION_READ',
  },
  { label: 'Usuarios', icon: 'group', route: '/app/users', permission: 'USER_READ' },
  { label: 'Empresas', icon: 'apartment', route: '/app/companies', permission: 'COMPANY_READ' },
  { label: 'Planes', icon: 'inventory_2', route: '/app/plans', permission: 'PLAN_READ' },
];

/** Sprint 41: visual-only display order for SUPERADMIN — same items, icons, routes and
 * permissions as NAV_ITEMS above (nothing there is touched), just a different render order for
 * this role. BROKER keeps NAV_ITEMS' own order unchanged. */
const SUPERADMIN_NAV_ORDER: readonly string[] = [
  '/app',
  '/app/companies',
  '/app/plans',
  '/app/users',
  '/app/banks',
  '/app/clients',
  '/app/cases',
  '/app/tasks',
  '/app/notifications',
];

/** Sprint 40.x: visual-only display order for MANAGER — same items, icons, routes and permissions
 * as NAV_ITEMS above. Empresas/Planes are deliberately left OUT of this list (not just left to the
 * template's permission directive): a real MANAGER account can hold COMPANY_READ (confirmed via
 * live testing against actual seed data), which would otherwise leak "Empresas" into the menu.
 * The spec requires exactly these 7 items for MANAGER regardless of any particular account's
 * permission grants, so the exclusion is structural here — permissions/guards/routes themselves
 * are untouched; a MANAGER with COMPANY_READ simply gets no menu link for it. */
const MANAGER_NAV_ORDER: readonly string[] = [
  '/app',
  '/app/clients',
  '/app/cases',
  '/app/banks',
  '/app/users',
  '/app/tasks',
  '/app/notifications',
];

function orderedNavItems(order: readonly string[]): readonly NavItem[] {
  const byRoute = new Map(NAV_ITEMS.map((item) => [item.route, item] as const));
  return order.map((route) => byRoute.get(route)).filter((item): item is NavItem => item !== undefined);
}

export function navItemsForRole(role: string | null): readonly NavItem[] {
  if (role === 'SUPERADMIN') {
    return orderedNavItems(SUPERADMIN_NAV_ORDER);
  }
  if (role === 'MANAGER') {
    return orderedNavItems(MANAGER_NAV_ORDER);
  }
  return NAV_ITEMS;
}
