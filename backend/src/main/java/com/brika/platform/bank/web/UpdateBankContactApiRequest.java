package com.brika.platform.bank.web;

public record UpdateBankContactApiRequest(
    String name,
    String position,
    String department,
    String branch,
    String email,
    String phone,
    String secondaryPhone,
    String notes,
    String visibility,
    boolean active) {}
