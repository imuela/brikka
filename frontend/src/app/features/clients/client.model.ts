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