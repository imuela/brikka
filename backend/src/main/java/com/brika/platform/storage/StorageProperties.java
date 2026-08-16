package com.brika.platform.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "brika.storage")
public record StorageProperties(
    String endpoint,
    String region,
    String accessKey,
    String secretKey,
    String bucket,
    long presignedUrlTtlSeconds,
    long maxFileSizeBytes) {}
