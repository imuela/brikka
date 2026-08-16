package com.brika.platform.communication;

import com.brika.platform.common.error.ValidationException;
import com.brika.platform.storage.MessageAttachmentStorageKey;
import com.brika.platform.storage.StorageClient;
import com.brika.platform.storage.StorageProperties;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Same MIME whitelist/size-limit discipline as DocumentService (Sprint 4 pre-flight decision) —
 * reapplied here rather than shared, since message attachments and the formal document pipeline are
 * conceptually separate flows (ADR-COMMS-001: never becomes a DOCUMENT).
 */
@Service
public class MessageAttachmentService {

  private static final Set<String> ALLOWED_MIME_TYPES =
      Set.of(
          "application/pdf",
          "image/jpeg",
          "image/png",
          "application/msword",
          "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
          "application/vnd.ms-excel",
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

  private final MessageAttachmentRepository messageAttachmentRepository;
  private final StorageClient storageClient;
  private final StorageProperties storageProperties;

  public MessageAttachmentService(
      MessageAttachmentRepository messageAttachmentRepository,
      StorageClient storageClient,
      StorageProperties storageProperties) {
    this.messageAttachmentRepository = messageAttachmentRepository;
    this.storageClient = storageClient;
    this.storageProperties = storageProperties;
  }

  public MessageAttachment upload(
      UUID companyId, UUID messageId, byte[] content, String originalFilename, String mimeType) {
    validate(content, mimeType);

    UUID id = UUID.randomUUID();
    String storageKey =
        MessageAttachmentStorageKey.build(companyId, messageId, id, originalFilename);
    String checksum = sha256(content);

    storageClient.upload(storageKey, content, mimeType);
    messageAttachmentRepository.insert(
        id, companyId, messageId, storageKey, originalFilename, mimeType, content.length, checksum);

    return messageAttachmentRepository.findById(id).orElseThrow();
  }

  public URI presignedDownloadUrl(MessageAttachment attachment) {
    return storageClient.presignedDownloadUrl(
        attachment.storageKey(), attachment.originalFilename());
  }

  private void validate(byte[] content, String mimeType) {
    if (content == null || content.length == 0) {
      throw new ValidationException("EMPTY_FILE", "Uploaded file is empty.");
    }
    if (content.length > storageProperties.maxFileSizeBytes()) {
      throw new ValidationException(
          "FILE_TOO_LARGE",
          "File exceeds the maximum allowed size ("
              + storageProperties.maxFileSizeBytes()
              + " bytes).");
    }
    if (mimeType == null || !ALLOWED_MIME_TYPES.contains(mimeType)) {
      throw new ValidationException("UNSUPPORTED_MIME_TYPE", "Unsupported file type: " + mimeType);
    }
  }

  private static String sha256(byte[] content) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(content));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
