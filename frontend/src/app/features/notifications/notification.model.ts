/** Mirrors backend com.brika.platform.notification.web.NotificationResponse
 * (17_API_SPECIFICATION_DETAILED.md §17C). Named AppNotification (not Notification) to avoid
 * shadowing the browser's global Notification type.
 *
 * Sprint 25 wired real domain events to the backend: case status changes/cancel/reopen, document
 * upload/review/publish and new messages now produce IN_APP notifications for the correct
 * recipients, so this list is populated against real data. */
export interface AppNotification {
  id: string;
  type: string;
  payload: unknown;
  readAt: string | null;
  createdAt: string;
}
