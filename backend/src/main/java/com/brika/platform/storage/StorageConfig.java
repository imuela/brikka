package com.brika.platform.storage;

import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * MinIO requires path-style bucket addressing (unlike real AWS S3, and unlike this project's own
 * S3-compatible target per 18_STORAGE_SPECIFICATION.md §2 — no environment here needs
 * virtual-hosted-style) — this project's MinIO has no {@code MINIO_DOMAIN} configured, so it does
 * not support virtual-hosted-style requests at all.
 *
 * <p>BUG-001 (found during Sprint 32's live validation, fixed in Sprint 33): {@code s3Client} had
 * {@code forcePathStyle(true)} from the start, but {@code s3Presigner} did not — {@link
 * S3Presigner.Builder} has no {@code forcePathStyle} shortcut (unlike {@link S3Client.Builder}),
 * only the more verbose {@code serviceConfiguration(S3Configuration)} used below, which is why it
 * was easy to add the setting to one bean and forget the other. Every presigned download URL was
 * generated in virtual-hosted-style ({@code <bucket>.<endpoint-host>/<key>}) and rejected by MinIO
 * with {@code NoSuchBucket} / {@code SignatureDoesNotMatch} — reproduced with a real {@code curl}
 * against a real presigned URL. The existing presigned-URL test never caught this because it only
 * asserts the URL *shape* (bucket name, signature, expiry present) — a virtual-hosted-style URL
 * contains the bucket name string too. See {@code DocumentServiceIT
 * #presignedDownloadUrlIsActuallyFetchableAndReturnsTheUploadedBytes}, added in Sprint 33, which
 * performs the real HTTP GET instead.
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

  @Bean
  S3Client s3Client(StorageProperties properties) {
    return S3Client.builder()
        .endpointOverride(URI.create(properties.endpoint()))
        .region(Region.of(properties.region()))
        .credentialsProvider(credentialsProvider(properties))
        .forcePathStyle(true)
        .build();
  }

  @Bean
  S3Presigner s3Presigner(StorageProperties properties) {
    return S3Presigner.builder()
        .endpointOverride(URI.create(properties.endpoint()))
        .region(Region.of(properties.region()))
        .credentialsProvider(credentialsProvider(properties))
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
        .build();
  }

  @Bean
  StorageClient storageClient(
      S3Client s3Client, S3Presigner s3Presigner, StorageProperties properties) {
    return new S3StorageClient(s3Client, s3Presigner, properties);
  }

  private static StaticCredentialsProvider credentialsProvider(StorageProperties properties) {
    return StaticCredentialsProvider.create(
        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
  }
}
