/** Mirrors backend ClientResponse exactly (17_API_SPECIFICATION_DETAILED.md §6, Sprint 27 §Bl). */
export interface Client {
  id: string;
  companyId: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  documentType: string | null;
  documentNumber: string | null;
  dateOfBirth: string | null;
  nationality: string | null;
  address: string | null;
  employmentStatus: string | null;
  status: string;
}

/** Mirrors backend CreateClientApiRequest (Sprint 27, Bloque 3). */
export interface CreateClientRequest {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  documentType?: string | null;
  documentNumber?: string | null;
  dateOfBirth?: string | null;
  nationality?: string | null;
  address?: string | null;
  employmentStatus?: string | null;
}

/** Mirrors backend UpdateClientApiRequest (Sprint 27, Bloque 3). */
export interface UpdateClientRequest {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  documentType?: string | null;
  documentNumber?: string | null;
  dateOfBirth?: string | null;
  nationality?: string | null;
  address?: string | null;
  employmentStatus?: string | null;
}

/** Sprint 40.x: closed catalog for the "Tipo de documento" field. Neither the backend column
 * (clients.document_type, varchar(30)) nor any DTO enforced a value set before this — this list is
 * the standard set of Spanish personal-identity document types, matching exactly what the field's
 * own former free-text placeholder already told users to enter ("DNI / NIE / PASAPORTE"), not a new
 * invented taxonomy. The backend stays free text; this is a frontend-only closed list. */
export const DOCUMENT_TYPES = ['DNI', 'NIE', 'PASAPORTE'] as const;

/** Sprint 40.x: closed catalog for "Situación laboral". No enum or CHECK constraint exists for this
 * field anywhere in the backend (clients.employment_status, varchar(50), free text) — this set was
 * chosen with the user rather than reused from an existing definition, since none exists. */
export const EMPLOYMENT_STATUSES = [
  'Empleado',
  'Autónomo',
  'Funcionario',
  'Desempleado',
  'Jubilado',
  'Estudiante',
] as const;