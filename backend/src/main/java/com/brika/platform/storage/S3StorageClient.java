package com.brika.platform.storage;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

class S3StorageClient implements StorageClient {

  private final S3Client s3Client;
  private final S3Presigner s3Presigner;
  private final StorageProperties properties;

  S3StorageClient(S3Client s3Client, S3Presigner s3Presigner, StorageProperties properties) {
    this.s3Client = s3Client;
    this.s3Presigner = s3Presigner;
    this.properties = properties;
  }

  @Override
  public void upload(String key, byte[] content, String contentType) {
    s3Client.putObject(
        PutObjectRequest.builder()
            .bucket(properties.bucket())
            .key(key)
            .contentType(contentType)
            .build(),
        RequestBody.fromBytes(content));
  }

  @Override
  public InputStream openStream(String key) {
    return s3Client.getObject(
        GetObjectRequest.builder().bucket(properties.bucket()).key(key).build());
  }

  @Override
  public URI presignedDownloadUrl(String key, String downloadFilename) {
    GetObjectRequest getObjectRequest =
        GetObjectRequest.builder()
            .bucket(properties.bucket())
            .key(key)
            .responseContentDisposition(
                "attachment; filename=\"" + SafeFilenames.sanitize(downloadFilename) + "\"")
            .build();
    GetObjectPresignRequest presignRequest =
        GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofSeconds(properties.presignedUrlTtlSeconds()))
            .getObjectRequest(getObjectRequest)
            .build();
    return URI.create(s3Presigner.presignGetObject(presignRequest).url().toString());
  }
}
