package com.brika.platform.auth.web;

public record PasswordResetConfirmApiRequest(String token, String newPassword) {}
