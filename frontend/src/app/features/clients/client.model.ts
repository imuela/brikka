/** Mirrors backend ClientResponse exactly (17_API_SPECIFICATION_DETAILED.md §6). */
export interface Client {
  id: string;
  companyId: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  status: string;
}

/** Mirrors backend CreateClientApiRequest. */
export interface CreateClientRequest {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
}

/** Mirrors backend UpdateClientApiRequest. */
export interface UpdateClientRequest {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
}
