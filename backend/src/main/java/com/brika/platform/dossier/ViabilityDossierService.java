package com.brika.platform.dossier;

import com.brika.platform.casemgmt.Case;
import com.brika.platform.casemgmt.CaseClient;
import com.brika.platform.casemgmt.CaseClientRepository;
import com.brika.platform.common.error.ValidationException;
import com.brika.platform.document.Document;
import com.brika.platform.document.DocumentRepository;
import com.brika.platform.document.DocumentService;
import com.brika.platform.document.DocumentTypeRepository;
import com.brika.platform.document.DocumentVersion;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 32 · elevated in BRIKKA V2 I5. Consolidates a case into a single HTML document. It no
 * longer dumps raw fields: the body is the deterministic {@link CaseNarrativeService} narrative
 * (situación, titulares, inmueble, financiación, scoring/RAG, viabilidad, documentación,
 * honorarios), rendered section by section. There is <b>one</b> dossier — this service and its
 * endpoints — not a parallel one; the narrative is also exposed read-only at {@code GET
 * /api/v1/cases/{caseId}/dossier/narrative}.
 *
 * <p><b>Snapshot, not live:</b> unchanged from Sprint 32 — each generation reads the current state
 * and freezes it in a new immutable {@link DocumentVersion} (append-only, same as
 * FinancialAnalysisResult). The narrative text itself is clock-free and deterministic; only the
 * HTML footer carries the generation timestamp, which is what makes each snapshot distinguishable.
 */
@Service
public class ViabilityDossierService {

  private static final String DOCUMENT_TYPE_CODE = "VIABILITY_DOSSIER";
  private static final DateTimeFormatter DATE_FORMAT =
      DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneOffset.UTC);

  private final CaseClientRepository caseClientRepository;
  private final CaseNarrativeService caseNarrativeService;
  private final DocumentRepository documentRepository;
  private final DocumentTypeRepository documentTypeRepository;
  private final DocumentService documentService;

  public ViabilityDossierService(
      CaseClientRepository caseClientRepository,
      CaseNarrativeService caseNarrativeService,
      DocumentRepository documentRepository,
      DocumentTypeRepository documentTypeRepository,
      DocumentService documentService) {
    this.caseClientRepository = caseClientRepository;
    this.caseNarrativeService = caseNarrativeService;
    this.documentRepository = documentRepository;
    this.documentTypeRepository = documentTypeRepository;
    this.documentService = documentService;
  }

  @Transactional
  public DocumentVersion generate(Case theCase, UUID actorUserId) {
    List<CaseClient> caseClients = caseClientRepository.findAllByCaseId(theCase.id());
    if (caseClients.isEmpty()) {
      throw new ValidationException(
          "NO_CLIENTS_ON_CASE", "The case has no linked clients to build a dossier for.");
    }

    CaseNarrative narrative = caseNarrativeService.narrate(theCase);
    byte[] content = renderHtml(theCase, narrative).getBytes(StandardCharsets.UTF_8);

    UUID documentTypeId =
        documentTypeRepository
            .findByCode(DOCUMENT_TYPE_CODE)
            .orElseThrow(
                () -> new IllegalStateException("Missing seeded document type: VIABILITY_DOSSIER"))
            .id();
    Document document =
        documentRepository
            .findByCaseIdAndDocumentTypeId(theCase.id(), documentTypeId)
            .orElseGet(
                () ->
                    documentService.createDocument(
                        theCase.companyId(), theCase.id(), documentTypeId));

    return documentService.uploadVersion(
        document, content, "dossier-viabilidad.html", "text/html", actorUserId);
  }

  private String renderHtml(Case theCase, CaseNarrative narrative) {
    StringBuilder body = new StringBuilder();
    body.append("<h1>Dossier de viabilidad</h1>");
    body.append(
        "<p style=\"padding:1em;border:1px solid #999;background:#eef4ff;\"><strong>Aviso:</strong> "
            + "Documento generado automáticamente por Brikka a partir de los datos registrados en el"
            + " sistema en el momento de la generación (snapshot, no se actualiza retroactivamente"
            + " si los datos cambian después). Resumen determinista, sin recomendaciones ni"
            + " conclusiones financieras nuevas. No sustituye el análisis o la decisión de una"
            + " entidad financiera.</p>");

    for (NarrativeSection section : narrative.sections()) {
      body.append("<h2>").append(escape(section.title())).append("</h2>");
      for (String paragraph : section.paragraphs()) {
        body.append("<p>").append(escape(paragraph)).append("</p>");
      }
    }

    body.append("<p><em>Generado el ")
        .append(DATE_FORMAT.format(Instant.now()))
        .append(" UTC.</em></p>");

    return "<!doctype html><html lang=\"es\"><head><meta charset=\"utf-8\">"
        + "<title>Dossier de viabilidad — "
        + escape(theCase.reference())
        + "</title></head><body>"
        + body
        + "</body></html>";
  }

  private static String escape(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}
