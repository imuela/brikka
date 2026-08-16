package com.brika.platform.portal.web;

/** Approved editable fields only (Sprint 7 gate decision): email and phone. Nothing else. */
public record UpdatePortalProfileApiRequest(String email, String phone) {}
