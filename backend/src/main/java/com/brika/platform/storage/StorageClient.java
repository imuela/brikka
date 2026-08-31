package com.brika.platform.storage;

import java.io.InputStream;
import java.net.URI;

/**
 * 18_STORAGE_SPECIFICATION.md: never exposes the physical key or storage credentials to callers
 * beyond this package — only a presigned, short-lived download URL, or a read stream for
 * server-side aggregation (BRIKKA V2 I5: the case documents ZIP).
 */
public interface StorageClient {

  void upload(String key, byte[] content, String contentType);

  URI presignedDownloadUrl(String key, String downloadFilename);

  /**
   * BRIKKA V2 I5. Opens a read stream over the object at {@code key} for server-side aggregation
   * (streamed into a ZIP, never buffered whole). The caller owns the stream and must close it.
   */
  InputStream openStream(String key);
}
