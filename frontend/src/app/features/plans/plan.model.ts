/** Mirrors backend PlanResponse (17_API_SPECIFICATION_DETAILED.md §4B, ADR-PLATFORM-001). Global
 * catalog, not tenant-owned — SUPERADMIN-only (PLAN_READ/PLAN_MANAGE). status has no CHECK
 * constraint or documented catalog (Plan.java javadoc: "Global catalog, not tenant-owned"), so it
 * is a plain text field here, not an invented dropdown — same convention as tasks.type. */
export interface Plan {
  id: string;
  code: string;
  name: string;
  status: string;
}

/** Mirrors backend CreatePlanApiRequest. */
export interface CreatePlanRequest {
  code: string;
  name: string;
  status: string;
}

/** Mirrors backend UpdatePlanApiRequest — code is immutable after creation, only name/status. */
export interface UpdatePlanRequest {
  name: string;
  status: string;
}
