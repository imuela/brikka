package com.brika.platform.storage;

import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** MinIO requires path-style bucket addressing (forcePathStyle), unlike real AWS S3. */
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
