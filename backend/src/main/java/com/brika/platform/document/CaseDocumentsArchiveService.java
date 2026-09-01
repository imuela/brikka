package com.brika.platform.document;

import com.brika.platform.storage.SafeFilenames;
import com.brika.platform.storage.StorageClient;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * BRIKKA V2 I5. Streams a ZIP with the documentation of a case straight to an {@link OutputStream}
 * (the HTTP response) — one document's storage stream is open at a time and copied into the ZIP,
 * nothing is buffered whole.
 *
 * <p><b>What goes in:</b> the <b>current version</b> ({@code documents.current_version_id}) of
 * every {@code documents} row of the case — exactly the version {@code GET
 * /api/v1/documents/{id}/download} returns. No approval / publication filter (the single-document
 * download has none either). Documents that never had a version uploaded are skipped. Generated
 * dossier / engagement-contract HTML are ordinary documents of the case and are included. The
 * immutable versioning semantics are untouched — this only reads.
 *
 * <p><b>Layout (derived from current metadata, not the Legacy {@code 01–06} folders):</b> {@code
 * <tipo de documento>/<titular | "expediente">/<documentId>-<nombre original>}. Every segment is
 * run through {@link SafeFilenames#sanitize} and guarded against {@code .} / {@code ..} / empty, so
 * a document (or holder) name can never steer a path inside the archive; the {@code documentId}
 * prefix makes every entry name unique.
 *
 * <p>Stays a pure {@code document}-package service: the holder display names are passed in by the
 * caller (same discipline as {@link CaseChecklistService}).
 */
@Service
public class CaseDocumentsArchiveService {

  private static final Logger log = LoggerFactory.getLogger(CaseDocumentsArchiveService.class);

  private final DocumentRepository documentRepository;
  private final DocumentVersionRepository documentVersionRepository;
  private final DocumentTypeRepository documentTypeRepository;
  private final StorageClient storageClient;

  public CaseDocumentsArchiveService(
      DocumentRepository documentRepository,
      DocumentVersionRepository documentVersionRepository,
      DocumentTypeRepository documentTypeRepository,
      StorageClient storageClient) {
    this.documentRepository = documentRepository;
    this.documentVersionRepository = documentVersionRepository;
    this.documentTypeRepository = documentTypeRepository;
    this.storageClient = storageClient;
  }

  /** Documents of the case (tenant-guarded) that have a downloadable current version. */
  public int countDownloadable(UUID caseId, UUID companyId) {
    return downloadableDocuments(caseId, companyId).size();
  }

  /**
   * Writes the ZIP into {@code out}, streaming each document. Returns the number of entries. A
   * storage failure mid-stream aborts the download (the response has already started); it is logged
   * and rethrown as {@link UncheckedIOException}.
   */
  public int writeArchive(
      UUID caseId, UUID companyId, Map<UUID, String> holderNamesByClientId, OutputStream out) {
    Map<UUID, DocumentType> typesById = new LinkedHashMap<>();
    for (DocumentType type : documentTypeRepository.findAll()) {
      typesById.put(type.id(), type);
    }
    Set<String> usedPaths = new HashSet<>();
    int written = 0;
    try (ZipOutputStream zip = new ZipOutputStream(out)) {
      for (Document document : downloadableDocuments(caseId, companyId)) {
        DocumentVersion version =
            documentVersionRepository.findById(document.currentVersionId()).orElse(null);
        if (version == null) {
          continue;
        }
        String path = entryPath(document, version, typesById, holderNamesByClientId, usedPaths);
        zip.putNextEntry(new ZipEntry(path));
        try (InputStream in = storageClient.openStream(version.storageKey())) {
          in.transferTo(zip);
        }
        zip.closeEntry();
        written++;
      }
    } catch (IOException e) {
      log.warn("Aborting case {} documents archive after {} entries", caseId, written, e);
      throw new UncheckedIOException("Failed to build the case documents archive", e);
    }
    return written;
  }

  private List<Document> downloadableDocuments(UUID caseId, UUID companyId) {
    return documentRepository.findAllByCaseId(caseId).stream()
        .filter(document -> document.companyId().equals(companyId))
        .filter(document -> document.currentVersionId() != null)
        .toList();
  }

  private String entryPath(
      Document document,
      DocumentVersion version,
      Map<UUID, DocumentType> typesById,
      Map<UUID, String> holderNamesByClientId,
      Set<String> usedPaths) {
    DocumentType type = typesById.get(document.documentTypeId());
    String typeSegment =
        safeSegment(
            type == null
                ? "otros"
                : (type.name() == null || type.name().isBlank() ? type.code() : type.name()));
    String holderSegment =
        document.clientId() == null
            ? "expediente"
            : safeSegment(
                holderNamesByClientId.getOrDefault(
                    document.clientId(), document.clientId().toString()));
    String fileSegment = document.id() + "-" + safeSegment(version.originalFilename());

    String candidate = typeSegment + "/" + holderSegment + "/" + fileSegment;
    String path = candidate;
    int suffix = 2;
    while (!usedPaths.add(path)) {
      path = candidate + "-" + suffix++;
    }
    return path;
  }

  private static String safeSegment(String raw) {
    String sanitized = SafeFilenames.sanitize(raw);
    if (sanitized.isEmpty() || sanitized.equals(".") || sanitized.equals("..")) {
      return "_";
    }
    return sanitized;
  }
}
