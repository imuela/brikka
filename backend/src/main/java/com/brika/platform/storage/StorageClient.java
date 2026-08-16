package com.brika.platform.storage;

import java.net.URI;

/**
 * 18_STORAGE_SPECIFICATION.md: never exposes the physical key or storage credentials to callers
 * beyond this package — only a presigned, short-lived download URL.
 */
public interface StorageClient {

  void upload(String key, byte[] content, String contentType);

  URI presignedDownloadUrl(String key, String downloadFilename);
}
