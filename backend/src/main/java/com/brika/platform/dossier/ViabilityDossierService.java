package com.brika.platform.dossier;

import com.brika.platform.bank.Bank;
import com.brika.platform.bank.BankRepository;
import com.brika.platform.bankmatching.BankMatchResult;
import com.brika.platform.bankmatching.BankMatchResultRepository;
import com.brika.platform.bankrequest.BankOffer;
import com.brika.platform.bankrequest.BankOfferRepository;
import com.brika.platform.bankrequest.BankRequest;
import com.brika.platform.bankrequest.BankRequestRepository;
import com.brika.platform.casefee.CaseFee;
import com.brika.platform.casefee.CaseFeeService;
import com.brika.platform.casemgmt.Case;
import com.brika.platform.casemgmt.CaseClient;
import com.brika.platform.casemgmt.CaseClientRepository;
import com.brika.platform.common.error.ValidationException;
import com.brika.platform.crm.Client;
import com.brika.platform.crm.ClientFinancialProfile;
import com.brika.platform.crm.ClientFinancialProfileRepository;
import com.brika.platform.crm.ClientRepository;
import com.brika.platform.document.Document;
import com.brika.platform.document.DocumentRepository;
import com.brika.platform.document.DocumentRequest;
import com.brika.platform.document.DocumentRequestRepository;
import com.brika.platform.document.DocumentRequestStatus;
import com.brika.platform.document.DocumentService;
import com.brika.platform.document.DocumentType;
import com.brika.platform.document.DocumentTypeRepository;
import com.brika.platform.document.DocumentVersion;
import com.brika.platform.financialanalysis.FinancialAnalysisResult;
import com.brika.platform.financialanalysis.FinancialAnalysisResultRepository;
import com.brika.platform.financing.FinancingRequest;
import com.brika.platform.financing.FinancingRequestRepository;
import com.brika.platform.financing.Simulation;
import com.brika.platform.financing.SimulationRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 32. Consolida en un único documento HTML toda la información YA EXISTENTE de un caso —
 * clientes, perfil financiero, análisis financiero (Sprint 31, snapshot histórico, nunca
 * recalculado aquí), simulaciones, solicitudes de financiación, matching bancario, ofertas,
 * honorarios y estado documental. No recalcula nada: lee resultados ya persistidos.
 *
 * <p><b>Dossier vivo vs. snapshot (decisión, sección 3 del encargo):</b> Opción B, snapshot
 * persistido — cada generación lee el estado ACTUAL del sistema y lo fija en una nueva
 * DocumentVersion inmutable (mismo patrón append-only que FinancialAnalysisResult, Sprint 31). Se
 * descarta un modo "dinámico" adicional: el propio encargo pide priorizar trazabilidad y evitar que
 * "un dossier histórico cambie silenciosamente" — un dossier vivo sin persistir violaría
 * exactamente eso, y el encargo ya anticipa un estado de "historial" en el frontend, lo que solo
 * tiene sentido con snapshots. Generar es barato (solo lectura) por lo que una vista "previa" en
 * vivo no aporta nada que una nueva generación no dé ya.
 */
@Service
public class ViabilityDossierService {

  private static final String DOCUMENT_TYPE_CODE = "VIABILITY_DOSSIER";
  private static final DateTimeFormatter DATE_FORMAT =
      DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneOffset.UTC);

  private final CaseClientRepository caseClientRepository;
  private final ClientRepository clientRepository;
  private final ClientFinancialProfileRepository financialProfileRepository;
  private final FinancialAnalysisResultRepository financialAnalysisRepository;
  private final SimulationRepository simulationRepository;
  private final FinancingRequestRepository financingRequestRepository;
  private final BankRequestRepository bankRequestRepository;
  private final BankOfferRepository bankOfferRepository;
  private final BankMatchResultRepository bankMatchResultRepository;
  private final BankRepository bankRepository;
  private final CaseFeeService caseFeeService;
  private final DocumentRepository documentRepository;
  private final DocumentRequestRepository documentRequestRepository;
  private final DocumentTypeRepository documentTypeRepository;
  private final DocumentService documentService;

  public ViabilityDossierService(
      CaseClientRepository caseClientRepository,
      ClientRepository clientRepository,
      ClientFinancialProfileRepository financialProfileRepository,
      FinancialAnalysisResultRepository financialAnalysisRepository,
      SimulationRepository simulationRepository,
      FinancingRequestRepository financingRequestRepository,
      BankRequestRepository bankRequestRepository,
      BankOfferRepository bankOfferRepository,
      BankMatchResultRepository bankMatchResultRepository,
      BankRepository bankRepository,
      CaseFeeService caseFeeService,
      DocumentRepository documentRepository,
      DocumentRequestRepository documentRequestRepository,
      DocumentTypeRepository documentTypeRepository,
      DocumentService documentService) {
    this.caseClientRepository = caseClientRepository;
    this.clientRepository = clientRepository;
    this.financialProfileRepository = financialProfileRepository;
    this.financialAnalysisRepository = financialAnalysisRepository;
    this.simulationRepository = simulationRepository;
    this.financingRequestRepository = financingRequestRepository;
    this.bankRequestRepository = bankRequestRepository;
    this.bankOfferRepository = bankOfferRepository;
    this.bankMatchResultRepository = bankMatchResultRepository;
    this.bankRepository = bankRepository;
    this.caseFeeService = caseFeeService;
    this.documentRepository = documentRepository;
    this.documentRequestRepository = documentRequestRepository;
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
    List<Client> clients =
        caseClients.stream()
            .map(cc -> clientRepository.findById(cc.clientId()).orElseThrow())
            .toList();

    Map<UUID, ClientFinancialProfile> profilesByClientId = new LinkedHashMap<>();
    Map<UUID, FinancialAnalysisResult> latestAnalysisByClientId = new LinkedHashMap<>();
    for (Client client : clients) {
      financialProfileRepository
          .findByClientId(client.id())
          .ifPresent(p -> profilesByClientId.put(client.id(), p));
    }
    for (FinancialAnalysisResult result :
        financialAnalysisRepository.findAllByCaseId(theCase.id())) {
      // findAllByCaseId orders calculated_at DESC, so the first hit per client is the latest.
      latestAnalysisByClientId.putIfAbsent(result.clientId(), result);
    }

    List<Simulation> simulations = simulationRepository.findAllByCaseId(theCase.id());
    List<FinancingRequest> financingRequests =
        financingRequestRepository.findAllByCaseId(theCase.id());
    List<BankRequest> bankRequests = bankRequestRepository.findAllByCaseId(theCase.id());
    List<BankOffer> bankOffers = bankOfferRepository.findAllByCaseId(theCase.id());
    List<BankMatchResult> matchResults = bankMatchResultRepository.findAllByCaseId(theCase.id());
    Optional<CaseFee> fee = caseFeeService.find(theCase.id());
    List<Document> documents = documentRepository.findAllByCaseId(theCase.id());
    List<DocumentRequest> documentRequests =
        documentRequestRepository.findAllByCaseId(theCase.id());
    Map<UUID, DocumentType> documentTypesById = new LinkedHashMap<>();
    for (DocumentType type : documentTypeRepository.findAll()) {
      documentTypesById.put(type.id(), type);
    }
    Map<UUID, Bank> banksById = new LinkedHashMap<>();
    for (BankRequest request : bankRequests) {
      bankRepository.findById(request.bankId()).ifPresent(b -> banksById.put(b.id(), b));
    }

    String html =
        renderHtml(
            theCase,
            clients,
            profilesByClientId,
            latestAnalysisByClientId,
            simulations,
            financingRequests,
            bankRequests,
            bankOffers,
            matchResults,
            banksById,
            fee.orElse(null),
            documents,
            documentRequests,
            documentTypesById);
    byte[] content = html.getBytes(StandardCharsets.UTF_8);

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

  private String renderHtml(
      Case theCase,
      List<Client> clients,
      Map<UUID, ClientFinancialProfile> profilesByClientId,
      Map<UUID, FinancialAnalysisResult> latestAnalysisByClientId,
      List<Simulation> simulations,
      List<FinancingRequest> financingRequests,
      List<BankRequest> bankRequests,
      List<BankOffer> bankOffers,
      List<BankMatchResult> matchResults,
      Map<UUID, Bank> banksById,
      CaseFee fee,
      List<Document> documents,
      List<DocumentRequest> documentRequests,
      Map<UUID, DocumentType> documentTypesById) {
    StringBuilder body = new StringBuilder();
    body.append("<h1>Dossier de viabilidad</h1>");
    body.append(
        "<p style=\"padding:1em;border:1px solid #999;background:#eef4ff;\"><strong>Aviso:</strong> "
            + "Documento generado automáticamente por Brikka V1 a partir de los datos registrados en"
            + " el sistema en el momento de la generación (snapshot, no se actualiza retroactivamente"
            + " si los datos cambian después). No sustituye el análisis o la decisión de una entidad"
            + " financiera.</p>");

    body.append("<h2>Operación</h2><p>Referencia: ")
        .append(escape(theCase.reference()))
        .append("<br>Estado: ")
        .append(escape(theCase.status() == null ? "—" : theCase.status().name()))
        .append("<br>Tipo: ")
        .append(escape(nullToDash(theCase.operationType())))
        .append("<br>Importe solicitado: ")
        .append(money(theCase.requestedAmount()))
        .append("</p>");

    body.append("<h2>Clientes y situación financiera</h2>");
    for (Client client : clients) {
      body.append("<h3>")
          .append(escape(client.firstName() + " " + client.lastName()))
          .append("</h3>");
      ClientFinancialProfile profile = profilesByClientId.get(client.id());
      if (profile == null) {
        body.append("<p><em>Sin perfil financiero registrado.</em></p>");
      } else {
        body.append("<p>Ingresos mensuales: ")
            .append(money(profile.monthlyIncome()))
            .append("<br>Deudas mensuales: ")
            .append(money(profile.otherDebtsMonthlyPayment()))
            .append("<br>Ahorro: ")
            .append(money(profile.savings()))
            .append("</p>");
      }
      FinancialAnalysisResult analysis = latestAnalysisByClientId.get(client.id());
      if (analysis == null) {
        body.append("<p><em>Sin análisis financiero ejecutado (Sprint 31).</em></p>");
      } else {
        body.append("<p>Cuota calculada: ")
            .append(money(analysis.monthlyPayment()))
            .append("<br>DTI: ")
            .append(analysis.dtiPercent())
            .append("%<br>Viabilidad: <strong>")
            .append(escape(analysis.viabilityCategory()))
            .append("</strong> (")
            .append(DATE_FORMAT.format(analysis.calculatedAt()))
            .append(" UTC)</p>");
      }
    }

    body.append("<h2>Financiación</h2>");
    if (simulations.isEmpty() && financingRequests.isEmpty()) {
      body.append("<p><em>Sin simulaciones ni solicitudes de financiación registradas.</em></p>");
    }
    for (Simulation simulation : simulations) {
      body.append("<p>Simulación: ")
          .append(money(simulation.principal()))
          .append(" a ")
          .append(simulation.interestRate())
          .append("% / ")
          .append(simulation.termMonths())
          .append(" meses (")
          .append(DATE_FORMAT.format(simulation.createdAt()))
          .append(" UTC)</p>");
    }
    for (FinancingRequest request : financingRequests) {
      body.append("<p>Solicitud de financiación: ")
          .append(money(request.requestedAmount()))
          .append(" / ")
          .append(request.termMonths())
          .append(" meses — estado ")
          .append(escape(request.status()))
          .append("</p>");
    }

    body.append("<h2>Matching bancario y ofertas</h2>");
    if (matchResults.isEmpty() && bankOffers.isEmpty()) {
      body.append("<p><em>Sin resultados de matching ni ofertas bancarias todavía.</em></p>");
    }
    for (BankMatchResult match : matchResults) {
      Bank bank = banksById.get(match.bankId());
      body.append("<p>Matching con ")
          .append(escape(bank == null ? match.bankId().toString() : bank.name()))
          .append(": <strong>")
          .append(escape(match.globalResult()))
          .append("</strong></p>");
    }
    for (BankOffer offer : bankOffers) {
      body.append("<p>Oferta: ")
          .append(money(offer.amount()))
          .append(" a ")
          .append(offer.interestRate())
          .append("% / ")
          .append(offer.termMonths())
          .append(" meses — estado ")
          .append(escape(offer.status()))
          .append("</p>");
    }

    body.append("<h2>Honorarios</h2>");
    if (fee == null) {
      body.append("<p><em>Sin honorarios configurados todavía.</em></p>");
    } else {
      body.append("<p>Importe: <strong>")
          .append(money(fee.calculatedAmount()))
          .append("</strong> (")
          .append(escape(fee.feeType()))
          .append(", estado ")
          .append(escape(fee.status()))
          .append(")</p>");
    }

    body.append("<h2>Documentación</h2>");
    if (documents.isEmpty()) {
      body.append("<p><em>Sin documentos registrados todavía.</em></p>");
    } else {
      body.append("<ul>");
      for (Document document : documents) {
        DocumentType type = documentTypesById.get(document.documentTypeId());
        body.append("<li>")
            .append(escape(type == null ? "—" : type.name()))
            .append(" — estado ")
            .append(escape(document.status().name()))
            .append("</li>");
      }
      body.append("</ul>");
    }
    List<DocumentRequest> pendingRequests =
        documentRequests.stream().filter(r -> r.status() == DocumentRequestStatus.PENDING).toList();
    if (pendingRequests.isEmpty()) {
      body.append("<p><em>Sin solicitudes documentales pendientes.</em></p>");
    } else {
      body.append("<p>Solicitudes documentales pendientes: ")
          .append(pendingRequests.size())
          .append("</p><ul>");
      for (DocumentRequest request : pendingRequests) {
        DocumentType type = documentTypesById.get(request.documentTypeId());
        body.append("<li>").append(escape(type == null ? "—" : type.name())).append("</li>");
      }
      body.append("</ul>");
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

  private static String nullToDash(String value) {
    return value == null || value.isBlank() ? "—" : value;
  }

  private static String money(BigDecimal value) {
    return value == null ? "—" : value.toPlainString() + " €";
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
