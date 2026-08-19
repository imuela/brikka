package com.brika.platform.auth.web;

public record ChangePasswordApiRequest(String currentPassword, String newPassword) {}
