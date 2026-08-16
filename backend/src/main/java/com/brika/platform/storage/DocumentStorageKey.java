package com.brika.platform.storage;

import java.util.UUID;

/** Exact key pattern from 18_STORAGE_SPECIFICATION.md §3 for formal documents. */
public final class DocumentStorageKey {

  private DocumentStorageKey() {}

  public static String build(
      UUID companyId, UUID caseId, UUID documentId, UUID versionId, String originalFilename) {
    return "companies/"
        + companyId
        + "/cases/"
        + caseId
        + "/documents/"
        + documentId
        + "/versions/"
        + versionId
        + "/"
        + SafeFilenames.sanitize(originalFilename);
  }
}
