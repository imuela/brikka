export interface NavItem {
  label: string;
  icon: string;
  route: string;
  /** null = always visible once authenticated. Sprint 14+ features add their own gated entries
   * here (e.g. { label: 'Clientes', route: '/app/clients', permission: 'CLIENT_READ' }) — none
   * exist yet, so only the foundation placeholder is listed (Sprint 13 scope). */
  permission: string | null;
}

export const NAV_ITEMS: readonly NavItem[] = [
  { label: 'Panel', icon: 'dashboard', route: '/app', permission: null },
];
