package com.brika.platform.dashboard.web;

import com.brika.platform.casemgmt.web.ActivityResponse;
import java.util.List;
import java.util.Map;

/** Sprint 27, Bloque 2 dashboard payload (FUNCTIONAL_SPECIFICATION.md §3). */
public record DashboardResponse(
    int activeCases,
    Map<String, Integer> casesByStatus,
    int pendingTasks,
    int overdueTasks,
    int pendingDocumentRequests,
    List<ActivityResponse> recentActivity) {}
