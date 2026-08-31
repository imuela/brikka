package com.brika.platform.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.brika.platform.storage.StorageClient;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

/**
 * BRIKKA V2 I5. Unit-level guarantees for the case documents ZIP: it streams document by document
 * (never more than one storage stream open at a time — no whole-file accumulation) and every ZIP
 * entry name is safe (sanitized, no {@code ..}, no absolute path) even when the document / holder
 * names are hostile. The end-to-end wiring against real MinIO is in {@code DocumentEndpointsIT}.
 */
class CaseDocumentsArchiveServiceTest {

  private final DocumentRepository documentRepository = mock(DocumentRepository.class);
  private final DocumentVersionRepository versionRepository = mock(DocumentVersionRepository.class);
  private final DocumentTypeRepository typeRepository = mock(DocumentTypeRepository.class);

  /** A StorageClient that tracks how many of its streams are open simultaneously. */
  private static final class TrackingStorageClient implements StorageClient {
    final AtomicInteger open = new AtomicInteger();
    int maxOpen = 0;
    int closed = 0;

    @Override
    public void upload(String key, byte[] content, String contentType) {}

    @Override
    public java.net.URI presignedDownloadUrl(String key, String downloadFilename) {
      return java.net.URI.create("about:blank");
    }

    @Override
    public InputStream openStream(String key) {
      int now = open.incrementAndGet();
      maxOpen = Math.max(maxOpen, now);
      return new ByteArrayInputStream(("bytes-of-" + key).getBytes()) {
        @Override
        public void close() {
          open.decrementAndGet();
          closed++;
        }
      };
    }
  }

  private Document doc(UUID id, UUID versionId, UUID clientId) {
    return new Document(
        id,
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        clientId,
        versionId,
        ReviewStatus.APPROVED);
  }

  private DocumentVersion version(UUID id, UUID documentId, String filename) {
    return new DocumentVersion(
        id,
        documentId,
        1,
        "companies/x/" + id,
        filename,
        "application/pdf",
        10L,
        "checksum",
        UUID.randomUUID(),
        null,
        Instant.now(),
        ReviewStatus.APPROVED,
        null,
        null,
        null);
  }

  @Test
  void streamsOneDocumentAtATimeAndClosesEveryStream() throws Exception {
    UUID companyId = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();
    List<Document> documents = new ArrayList<>();
    when(versionRepository.findById(any()))
        .thenAnswer(
            inv -> {
              UUID vId = inv.getArgument(0);
              return java.util.Optional.of(version(vId, UUID.randomUUID(), vId + ".pdf"));
            });
    for (int i = 0; i < 25; i++) {
      UUID versionId = UUID.randomUUID();
      documents.add(withCompany(doc(UUID.randomUUID(), versionId, null), companyId));
    }
    when(documentRepository.findAllByCaseId(caseId)).thenReturn(documents);
    when(typeRepository.findAll())
        .thenReturn(List.of(new DocumentType(UUID.randomUUID(), "DNI", "DNI", true)));

    TrackingStorageClient storage = new TrackingStorageClient();
    CaseDocumentsArchiveService service =
        new CaseDocumentsArchiveService(
            documentRepository, versionRepository, typeRepository, storage);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    int written = service.writeArchive(caseId, companyId, Map.of(), out);

    assertThat(written).isEqualTo(25);
    assertThat(storage.maxOpen).as("only one storage stream open at a time").isEqualTo(1);
    assertThat(storage.closed).as("every storage stream is closed").isEqualTo(25);
    assertThat(readZipNames(out.toByteArray())).hasSize(25);
  }

  @Test
  void entryNamesAreSanitizedEvenForHostileMetadata() throws Exception {
    UUID companyId = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();
    UUID docId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();

    when(documentRepository.findAllByCaseId(caseId))
        .thenReturn(List.of(withCompany(doc(docId, versionId, clientId), companyId)));
    when(versionRepository.findById(versionId))
        .thenReturn(java.util.Optional.of(version(versionId, docId, "../../etc/passwd")));
    when(typeRepository.findAll())
        .thenReturn(List.of(new DocumentType(UUID.randomUUID(), "DNI", "../secret", true)));

    CaseDocumentsArchiveService service =
        new CaseDocumentsArchiveService(
            documentRepository, versionRepository, typeRepository, new TrackingStorageClient());

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    service.writeArchive(caseId, companyId, Map.of(clientId, "Robert\"); DROP TABLE/../.."), out);

    List<String> names = readZipNames(out.toByteArray());
    assertThat(names).hasSize(1);
    String name = names.get(0);
    // no traversal: not absolute, and no path segment is "." or ".."
    assertThat(name).doesNotStartWith("/");
    assertThat(name.split("/")).noneMatch(segment -> segment.equals(".") || segment.equals(".."));
    assertThat(name).contains(docId.toString());
  }

  private Document withCompany(Document document, UUID companyId) {
    return new Document(
        document.id(),
        companyId,
        document.caseId(),
        document.documentTypeId(),
        document.clientId(),
        document.currentVersionId(),
        document.status());
  }

  private static List<String> readZipNames(byte[] archive) throws Exception {
    List<String> names = new ArrayList<>();
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        names.add(entry.getName());
        zip.readAllBytes();
      }
    }
    return names;
  }
}
