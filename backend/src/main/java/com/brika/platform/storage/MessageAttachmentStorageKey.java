package com.brika.platform.storage;

import java.util.UUID;

/** Same key-safety discipline as DocumentStorageKey, scoped to message attachments instead. */
public final class MessageAttachmentStorageKey {

  private MessageAttachmentStorageKey() {}

  public static String build(
      UUID companyId, UUID messageId, UUID attachmentId, String originalFilename) {
    return "companies/"
        + companyId
        + "/messages/"
        + messageId
        + "/attachments/"
        + attachmentId
        + "/"
        + SafeFilenames.sanitize(originalFilename);
  }
}
