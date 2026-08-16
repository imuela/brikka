package com.brika.platform.bankrequest.web;

import java.util.Map;

public record CreateBankResponseApiRequest(String summary, Map<String, Object> payload) {}
