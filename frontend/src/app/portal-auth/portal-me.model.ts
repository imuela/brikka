/** Mirrors backend PortalMeResponse exactly (portal/web/PortalMeResponse.java). */
export interface PortalMeResponse {
  clientId: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  accountStatus: string;
  lastLoginAt: string | null;
}

/** Mirrors backend UpdatePortalProfileApiRequest — only these two fields are ever editable. */
export interface UpdatePortalProfileRequest {
  email: string;
  phone: string;
}
