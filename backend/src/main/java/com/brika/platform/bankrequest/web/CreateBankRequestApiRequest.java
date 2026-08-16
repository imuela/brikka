package com.brika.platform.bankrequest.web;

import java.util.UUID;

public record CreateBankRequestApiRequest(UUID bankId, UUID bankContactId) {}
