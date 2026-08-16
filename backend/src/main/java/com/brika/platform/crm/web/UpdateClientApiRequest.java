package com.brika.platform.crm.web;

public record UpdateClientApiRequest(
    String firstName, String lastName, String email, String phone) {}
