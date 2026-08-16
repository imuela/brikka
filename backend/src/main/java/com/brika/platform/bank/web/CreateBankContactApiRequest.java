package com.brika.platform.bank.web;

import java.util.UUID;

public record CreateBankContactApiRequest(
    UUID bankId,
    String name,
    String position,
    String department,
    String branch,
    String email,
    String phone,
    String secondaryPhone,
    String notes,
    String visibility) {}
