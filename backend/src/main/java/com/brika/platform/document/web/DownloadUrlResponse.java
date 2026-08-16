package com.brika.platform.document.web;

/**
 * url is a short-lived presigned Object Storage URL — never the raw key, never storage credentials.
 */
public record DownloadUrlResponse(String url, long expiresInSeconds) {}
