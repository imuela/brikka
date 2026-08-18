/** Mirrors backend PortalNotificationResponse. readAt is null until Sprint 19's new
 * PATCH .../read is called (ADR-PROCESS-007) — before that it could never be set at all. */
export interface PortalNotification {
  id: string;
  type: string;
  payload: Record<string, unknown>;
  readAt: string | null;
  createdAt: string;
}
