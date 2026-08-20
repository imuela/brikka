/** Mirrors backend DashboardResponse (Sprint 27, Bloque 2). */
export interface DashboardActivity {
  id: string;
  caseId: string | null;
  activityType: string;
  summary: string;
  createdAt: string;
}

export interface Dashboard {
  activeCases: number;
  casesByStatus: Record<string, number>;
  pendingTasks: number;
  overdueTasks: number;
  pendingDocumentRequests: number;
  recentActivity: DashboardActivity[];
}