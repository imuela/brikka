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
];
