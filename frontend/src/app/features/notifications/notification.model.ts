/** Mirrors backend com.brika.platform.notification.web.NotificationResponse
 * (17_API_SPECIFICATION_DETAILED.md §17C). Named AppNotification (not Notification) to avoid
 * shadowing the browser's global Notification type.
 *
 * No producer wires this to any real domain event yet (NotificationService.create() has no
 * caller in the codebase outside its own test — confirmed by direct code reading during Sprint 17
 * Fase 1), so this list is genuinely empty against real data today. That is a real, honest
 * backend limitation, not a frontend gap — this UI must render it correctly, not paper over it. */
export interface AppNotification {
  id: string;
  type: string;
  payload: unknown;
  readAt: string | null;
  createdAt: string;
}
