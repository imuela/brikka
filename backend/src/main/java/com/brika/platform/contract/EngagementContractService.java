package com.brika.platform.contract;

import com.brika.platform.casefee.CaseFee;
import com.brika.platform.casefee.CaseFeeService;
import com.brika.platform.casemgmt.Case;
import com.brika.platform.casemgmt.CaseClient;
import com.brika.platform.casemgmt.CaseClientRepository;
import com.brika.platform.common.error.ValidationException;
import com.brika.platform.crm.Client;
import com.brika.platform.crm.ClientRepository;
import com.brika.platform.document.Document;
import com.brika.platform.document.DocumentRepository;
import com.brika.platform.document.DocumentService;
import com.brika.platform.document.DocumentTypeRepository;
import com.brika.platform.document.DocumentVersion;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 32. Genera el contrato/encargo como un {@link Document} (tipo ENGAGEMENT_CONTRACT, V26)
 * más: cada generación añade una {@link DocumentVersion} nueva al mismo Document, nunca sobrescribe
 * — el propio fichero HTML de cada versión es el snapshot histórico, sin necesidad de una tabla de
 * contratos independiente (ver V26 y el informe de cierre del sprint para la justificación completa
 * de por qué no se duplica infraestructura documental).
 *
 * <p><b>Límite explícito (obligatorio, sección 4 del encargo):</b> no existe en `docs/` ni en el
 * repositorio ninguna plantilla ni clausulado legal aprobado para un contrato de encargo. Este
 * generador produce una plantilla TÉCNICA/PROVISIONAL, claramente etiquetada como tal en el propio
 * documento — nunca se presenta como un contrato jurídicamente válido. No hay firma electrónica, ni
 * validación jurídica, ni envío certificado: solo el documento y su trazabilidad.
 */
@Service
public class EngagementContractService {

  public static final String DISCLAIMER =
      "Documento técnico generado automáticamente por Brikka V1 a partir de los datos registrados en"
          + " el sistema en el momento de la generación. No constituye un contrato "
          + "jurídicamente válido ni ha sido revisado por un profesional legal. Debe ser sustituido"
          + " por el clausulado legal aprobado por la empresa antes de su uso con clientes.";

  private static final String DOCUMENT_TYPE_CODE = "ENGAGEMENT_CONTRACT";
  private static final DateTimeFormatter DATE_FORMAT =
      DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneOffset.UTC);

  private final CaseClientRepository caseClientRepository;
  private final ClientRepository clientRepository;
  private final CaseFeeService caseFeeService;
  private final DocumentTypeRepository documentTypeRepository;
  private final DocumentRepository documentRepository;
  private final DocumentService documentService;

  public EngagementContractService(
      CaseClientRepository caseClientRepository,
      ClientRepository clientRepository,
      CaseFeeService caseFeeService,
      DocumentTypeRepository documentTypeRepository,
      DocumentRepository documentRepository,
      DocumentService documentService) {
    this.caseClientRepository = caseClientRepository;
    this.clientRepository = clientRepository;
    this.caseFeeService = caseFeeService;
    this.documentTypeRepository = documentTypeRepository;
    this.documentRepository = documentRepository;
    this.documentService = documentService;
  }

  @Transactional
  public DocumentVersion generate(Case theCase, UUID actorUserId) {
    List<CaseClient> caseClients = caseClientRepository.findAllByCaseId(theCase.id());
    if (caseClients.isEmpty()) {
      throw new ValidationException(
          "NO_CLIENTS_ON_CASE", "The case has no linked clients to draft a contract for.");
    }
    List<Client> clients =
        caseClients.stream()
            .map(cc -> clientRepository.findById(cc.clientId()).orElseThrow())
            .toList();
    Optional<CaseFee> fee = caseFeeService.find(theCase.id());

    String html = renderHtml(theCase, clients, fee.orElse(null));
    byte[] content = html.getBytes(StandardCharsets.UTF_8);

    UUID documentTypeId =
        documentTypeRepository
            .findByCode(DOCUMENT_TYPE_CODE)
            .orElseThrow(
                () ->
                    new IllegalStateException("Missing seeded document type: ENGAGEMENT_CONTRACT"))
            .id();
    Document document =
        documentRepository
            .findByCaseIdAndDocumentTypeId(theCase.id(), documentTypeId)
            .orElseGet(
                () ->
                    documentService.createDocument(
                        theCase.companyId(), theCase.id(), documentTypeId));

    return documentService.uploadVersion(
        document, content, "contrato-encargo.html", "text/html", actorUserId);
  }

  private String renderHtml(Case theCase, List<Client> clients, CaseFee fee) {
    StringBuilder clientsHtml = new StringBuilder();
    for (Client client : clients) {
      clientsHtml
          .append("<li>")
          .append(escape(client.firstName() + " " + client.lastName()))
          .append(" — ")
          .append(escape(nullToDash(client.documentType())))
          .append(" ")
          .append(escape(nullToDash(client.documentNumber())))
          .append("</li>");
    }

    String feeHtml;
    if (fee == null) {
      feeHtml = "<p><em>Honorarios: no configurados todavía para este caso.</em></p>";
    } else if ("FIXED".equals(fee.feeType())) {
      feeHtml =
          "<p>Honorarios: importe fijo de <strong>"
              + money(fee.calculatedAmount())
              + "</strong> (estado: "
              + escape(fee.status())
              + ").</p>";
    } else {
      feeHtml =
          "<p>Honorarios: "
              + percent(fee.percentage())
              + "% sobre una base de "
              + money(fee.calculationBase())
              + " = <strong>"
              + money(fee.calculatedAmount())
              + "</strong> (estado: "
              + escape(fee.status())
              + ").</p>";
    }

    return "<!doctype html><html lang=\"es\"><head><meta charset=\"utf-8\">"
        + "<title>Contrato de encargo — "
        + escape(theCase.reference())
        + "</title></head><body>"
        + "<h1>Contrato de encargo</h1>"
        + "<p style=\"padding:1em;border:1px solid #999;background:#fef6e0;\"><strong>Aviso:</strong> "
        + escape(DISCLAIMER)
        + "</p>"
        + "<h2>Operación</h2>"
        + "<p>Referencia: "
        + escape(theCase.reference())
        + "<br>Tipo: "
        + escape(nullToDash(theCase.operationType()))
        + "<br>Importe solicitado: "
        + money(theCase.requestedAmount())
        + "</p>"
        + "<h2>Clientes</h2><ul>"
        + clientsHtml
        + "</ul>"
        + "<h2>Honorarios</h2>"
        + feeHtml
        + "<p><em>Generado el "
        + DATE_FORMAT.format(java.time.Instant.now())
        + " UTC.</em></p>"
        + "</body></html>";
  }

  private static String nullToDash(String value) {
    return value == null || value.isBlank() ? "—" : value;
  }

  private static String money(BigDecimal value) {
    return value == null ? "—" : value.toPlainString() + " €";
  }

  private static String percent(BigDecimal value) {
    return value == null ? "—" : value.toPlainString();
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
