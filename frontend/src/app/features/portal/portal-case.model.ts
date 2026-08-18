/** Mirrors backend PortalCaseResponse exactly — deliberately narrower than the internal Case
 * (no scoring/banking/internal fields, "Consulta de información publicada" per 07_PORTAL_CLIENTE.md). */
export interface PortalCase {
  id: string;
  reference: string;
  status: string;
  operationType: string;
  createdAt: string;
}
