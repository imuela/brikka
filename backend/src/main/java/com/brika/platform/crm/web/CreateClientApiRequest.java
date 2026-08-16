package com.brika.platform.crm.web;

public record CreateClientApiRequest(
    String firstName, String lastName, String email, String phone) {}
